package com.example.javaweb.service;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * JWT 白名单与会话管理。
 *
 * 一次登录对应一个 sid（会话），Access Token 与 Refresh Token 共享该 sid：
 * - auth:token:<accessJti>     -> sid，每次请求校验 Access 是否仍在白名单
 * - auth:refresh:<refreshJti>  -> sid，刷新时校验 Refresh 是否仍有效
 * - auth:session:<sid>         -> userId|accessJti|refreshJti|clientType
 * - auth:refresh-used:<jti>    -> userId，短暂保留已轮换的 Refresh，用于重放检测
 *
 * 退出登录 / 禁用用户 / 重置密码时，可以按 sid 或 userId 一次撤销整段会话。
 */
@Service
public class TokenService {
    private static final String ACCESS_KEY_PREFIX = "auth:token:";
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String SESSION_KEY_PREFIX = "auth:session:";
    private static final String USER_ACCESS_PREFIX = "auth:user-tokens:";
    private static final String USER_REFRESH_PREFIX = "auth:user-refresh:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";
    private static final String USED_REFRESH_PREFIX = "auth:refresh-used:";

    /** 已轮换 Refresh 的重放检测窗口 */
    private static final Duration REUSE_GUARD_TTL = Duration.ofMinutes(2);

    @Resource
    private StringRedisTemplate redis;

    public record SessionRecord(Long userId, String accessJti, String refreshJti, String clientType) {
    }

    /**
     * 登录成功后登记整段会话。
     */
    public void saveSession(String sid, String accessJti, String refreshJti, Long userId,
                            String clientType, long accessTtlMillis, long refreshTtlMillis) {
        long accessTtl = Math.max(accessTtlMillis, 1_000L);
        long refreshTtl = Math.max(refreshTtlMillis, accessTtl);

        redis.opsForValue().set(ACCESS_KEY_PREFIX + accessJti, sid, Duration.ofMillis(accessTtl));
        redis.opsForValue().set(REFRESH_KEY_PREFIX + refreshJti, sid, Duration.ofMillis(refreshTtl));
        redis.opsForValue().set(SESSION_KEY_PREFIX + sid,
                encodeSession(userId, accessJti, refreshJti, clientType),
                Duration.ofMillis(refreshTtl));

        if (userId != null) {
            addToSet(USER_ACCESS_PREFIX + userId, accessJti, accessTtl);
            addToSet(USER_REFRESH_PREFIX + userId, refreshJti, refreshTtl);
            addToSet(USER_SESSIONS_PREFIX + userId, sid, refreshTtl);
        }
    }

    /** 每次请求校验 Access Token 是否仍在白名单中。 */
    public boolean isValid(String accessJti) {
        return accessJti != null && Boolean.TRUE.equals(redis.hasKey(ACCESS_KEY_PREFIX + accessJti));
    }

    /** 刷新时校验 Refresh Token 是否仍在白名单中。 */
    public boolean isRefreshValid(String refreshJti) {
        return refreshJti != null && Boolean.TRUE.equals(redis.hasKey(REFRESH_KEY_PREFIX + refreshJti));
    }

    /** 该 Refresh 是否已经被轮换过（用于重放检测）。 */
    public boolean isRefreshReused(String refreshJti) {
        return refreshJti != null && Boolean.TRUE.equals(redis.hasKey(USED_REFRESH_PREFIX + refreshJti));
    }

    public Long getUserIdByUsedRefresh(String refreshJti) {
        if (refreshJti == null) {
            return null;
        }
        String value = redis.opsForValue().get(USED_REFRESH_PREFIX + refreshJti);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public SessionRecord getSession(String sid) {
        if (sid == null || sid.isBlank()) {
            return null;
        }
        return parseSession(redis.opsForValue().get(SESSION_KEY_PREFIX + sid));
    }

    /**
     * 刷新时轮换 Refresh Token：
     * 旧 jti 标记为已使用并移出白名单，随后写入新的 Access / Refresh。
     */
    public void rotate(String sid, String oldRefreshJti, String newAccessJti, String newRefreshJti,
                       Long userId, String clientType, long accessTtlMillis, long refreshTtlMillis) {
        if (oldRefreshJti != null) {
            redis.opsForValue().set(USED_REFRESH_PREFIX + oldRefreshJti,
                    userId == null ? "" : userId.toString(), REUSE_GUARD_TTL);
            redis.delete(REFRESH_KEY_PREFIX + oldRefreshJti);
            if (userId != null) {
                redis.opsForSet().remove(USER_REFRESH_PREFIX + userId, oldRefreshJti);
            }
        }
        saveSession(sid, newAccessJti, newRefreshJti, userId, clientType, accessTtlMillis, refreshTtlMillis);
    }

    /** 退出登录：按 Access jti 找到 sid 并撤销整段会话。 */
    public void revokeSessionByAccessJti(String accessJti) {
        if (accessJti == null) {
            return;
        }
        String sid = redis.opsForValue().get(ACCESS_KEY_PREFIX + accessJti);
        if (sid == null) {
            redis.delete(ACCESS_KEY_PREFIX + accessJti);
            return;
        }
        // 刷新后会话记录指向新的 Access jti，这里额外清理调用方携带的旧 jti
        SessionRecord session = getSession(sid);
        redis.delete(ACCESS_KEY_PREFIX + accessJti);
        if (session != null && session.userId() != null) {
            redis.opsForSet().remove(USER_ACCESS_PREFIX + session.userId(), accessJti);
        }
        revokeSession(sid);
    }

    /** 按 sid 撤销会话（Access、Refresh、会话元数据同时删除）。 */
    public void revokeSession(String sid) {
        if (sid == null || sid.isBlank()) {
            return;
        }
        SessionRecord session = getSession(sid);
        redis.delete(SESSION_KEY_PREFIX + sid);
        if (session == null) {
            return;
        }
        redis.delete(ACCESS_KEY_PREFIX + session.accessJti());
        redis.delete(REFRESH_KEY_PREFIX + session.refreshJti());
        if (session.userId() != null) {
            redis.opsForSet().remove(USER_ACCESS_PREFIX + session.userId(), session.accessJti());
            redis.opsForSet().remove(USER_REFRESH_PREFIX + session.userId(), session.refreshJti());
            redis.opsForSet().remove(USER_SESSIONS_PREFIX + session.userId(), sid);
        }
    }

    /** 禁用用户 / 重置密码：撤销该用户全部会话。 */
    public void revokeAllForUser(Long userId) {
        if (userId == null) {
            return;
        }
        Set<String> accessJtis = members(USER_ACCESS_PREFIX + userId);
        Set<String> refreshJtis = members(USER_REFRESH_PREFIX + userId);
        Set<String> sessions = members(USER_SESSIONS_PREFIX + userId);

        deleteKeys(accessJtis, ACCESS_KEY_PREFIX);
        deleteKeys(refreshJtis, REFRESH_KEY_PREFIX);
        deleteKeys(sessions, SESSION_KEY_PREFIX);

        redis.delete(USER_ACCESS_PREFIX + userId);
        redis.delete(USER_REFRESH_PREFIX + userId);
        redis.delete(USER_SESSIONS_PREFIX + userId);
    }

    private void addToSet(String key, String member, long ttlMillis) {
        redis.opsForSet().add(key, member);
        redis.expire(key, Duration.ofMillis(Math.max(ttlMillis, 1_000L)));
    }

    private Set<String> members(String key) {
        Set<String> values = redis.opsForSet().members(key);
        return values == null ? Collections.emptySet() : values;
    }

    private void deleteKeys(Set<String> members, String prefix) {
        if (!members.isEmpty()) {
            redis.delete(members.stream().map(member -> prefix + member).toList());
        }
    }

    private static String encodeSession(Long userId, String accessJti, String refreshJti, String clientType) {
        return (userId == null ? "" : userId.toString())
                + "|" + accessJti
                + "|" + refreshJti
                + "|" + (clientType == null ? "" : clientType);
    }

    private static SessionRecord parseSession(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 4) {
            return null;
        }
        try {
            Long userId = parts[0].isBlank() ? null : Long.valueOf(parts[0]);
            return new SessionRecord(userId, parts[1], parts[2], parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
