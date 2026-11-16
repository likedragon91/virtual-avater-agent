package com.example.javaweb.service;

import com.example.javaweb.entity.User;
import com.example.javaweb.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 认证时使用的用户实时状态。
 *
 * JWT 本身不携带“账号是否被禁用”的最新状态，这里用 Redis 缓存 30 秒，
 * 避免每个请求都查库，同时让管理员禁用账号后能在很短时间内生效。
 */
@Service
public class UserAuthStateService {
    private static final Logger log = LoggerFactory.getLogger(UserAuthStateService.class);
    private static final String KEY_PREFIX = "auth:user-state:";
    private static final Duration TTL = Duration.ofSeconds(30);

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate redis;

    public UserAuthState get(Long userId) {
        if (userId == null) {
            return null;
        }
        String key = KEY_PREFIX + userId;
        try {
            String cached = redis.opsForValue().get(key);
            UserAuthState parsed = parse(cached);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("read user auth state cache failed, userId={}", userId, e);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        UserAuthState state = new UserAuthState(
                user.getId(), user.getUsername(), user.getUserType(), user.getStatus());
        try {
            redis.opsForValue().set(key,
                    state.userType() + "|" + state.status() + "|" + state.username(), TTL);
        } catch (Exception e) {
            log.warn("write user auth state cache failed, userId={}", userId, e);
        }
        return state;
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("evict user auth state cache failed, userId={}", userId, e);
        }
    }

    private static UserAuthState parse(String cached) {
        if (cached == null || cached.isBlank()) {
            return null;
        }
        String[] parts = cached.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new UserAuthState(
                    null,
                    parts[2],
                    Integer.valueOf(parts[0]),
                    Integer.valueOf(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record UserAuthState(Long userId, String username, Integer userType, Integer status) {
    }
}
