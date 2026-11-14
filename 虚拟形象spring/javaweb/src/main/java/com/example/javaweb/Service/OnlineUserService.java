package com.example.javaweb.service;

import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线用户注册表（Redis 集中存储）。
 * - 登录成功时登记
 * - 每次携带有效 token 的请求刷新最后活跃时间
 * - 退出登录时移除
 * Redis key 自带 TTL，超过 online.timeout-minutes 分钟没有请求的会话自动过期。
 */
@Service
public class OnlineUserService {
    private static final String ONLINE_KEY_PREFIX = "auth:online:";

    @Resource
    private StringRedisTemplate redis;

    //无请求多久后视为离线（分钟）
    @Value("${online.timeout-minutes:30}")
    private long timeoutMinutes;

    //登记或刷新在线会话
    public void register(String token, Long userId, String username, Integer userType) {
        String key = sessionKey(userId, username);
        if (key == null) {
            return;
        }
        long now = System.currentTimeMillis();
        HashOperations<String, String, String> hash = redis.opsForHash();
        String loginTime = hash.get(key, "loginTime");
        long effectiveLoginTime = now;
        if (loginTime != null) {
            try {
                effectiveLoginTime = Long.parseLong(loginTime);
            } catch (NumberFormatException ignored) {
                //历史脏数据按当前时间处理
            }
        }

        Map<String, String> session = new HashMap<>();
        session.put("token", token);
        if (userId != null) {
            session.put("userId", userId.toString());
        }
        if (username != null) {
            session.put("username", username);
        }
        if (userType != null) {
            session.put("userType", userType.toString());
        }
        session.put("loginTime", String.valueOf(effectiveLoginTime));
        session.put("lastActiveTime", String.valueOf(now));
        hash.putAll(key, session);
        redis.expire(key, Duration.ofMinutes(Math.max(timeoutMinutes, 1)));
    }

    //刷新活跃时间；服务重启后 Redis 会话不存在时按 token 内信息补登记
    public void touch(String token, Long userId, String username, Integer userType) {
        register(token, userId, username, userType);
    }

    //移除会话（仅当该用户当前保存的 token 就是退出所用的 token，避免旧会话退出影响新登录）
    public void remove(Long userId, String username, String token) {
        String key = sessionKey(userId, username);
        if (key == null || token == null) {
            return;
        }
        HashOperations<String, String, String> hash = redis.opsForHash();
        String storedToken = hash.get(key, "token");
        if (token.equals(storedToken)) {
            redis.delete(key);
        }
    }

    //获取仍活跃的会话
    public List<OnlineSession> listActive() {
        List<OnlineSession> sessions = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(ONLINE_KEY_PREFIX + "*")
                .count(100)
                .build();
        HashOperations<String, String, String> hash = redis.opsForHash();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Map<String, String> fields = hash.entries(key);
                OnlineSession session = toSession(fields);
                if (session != null) {
                    sessions.add(session);
                }
            }
        }
        sessions.sort(Comparator.comparing(OnlineSession::getLastActiveTime).reversed());
        return sessions;
    }

    private static OnlineSession toSession(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        Long lastActive = parseLong(fields.get("lastActiveTime"));
        Long login = parseLong(fields.get("loginTime"));
        if (lastActive == null || login == null) {
            return null;
        }
        OnlineSession session = new OnlineSession();
        session.setToken(fields.get("token"));
        session.setUserId(parseLong(fields.get("userId")));
        session.setUsername(fields.get("username"));
        session.setUserType(parseInteger(fields.get("userType")));
        session.setLoginTime(toLocalDateTime(login));
        session.setLastActiveTime(toLocalDateTime(lastActive));
        return session;
    }

    private static String sessionKey(Long userId, String username) {
        if (userId != null) {
            return ONLINE_KEY_PREFIX + userId;
        }
        return username != null ? ONLINE_KEY_PREFIX + username : null;
    }

    private static Long parseLong(String value) {
        try {
            return value != null ? Long.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return value != null ? Integer.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    //单个在线会话
    @Data
    public static class OnlineSession {
        private String token;
        private Long userId;
        private String username;
        private Integer userType;
        private LocalDateTime loginTime;
        private LocalDateTime lastActiveTime;
    }
}
