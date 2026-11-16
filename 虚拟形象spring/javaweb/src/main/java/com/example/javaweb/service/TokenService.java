package com.example.javaweb.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * JWT 白名单。
 * JWT 本身只负责携带身份，token 是否仍然有效由 Redis 集中管理：
 * - 登录成功：写入 auth:token:<jti>，并记录到 auth:user-tokens:<userId>
 * - 每次请求：校验 jti 是否还在白名单中
 * - 退出登录：删除对应 jti，token 立即失效
 * - 禁用用户 / 重置密码：删除该用户全部 jti
 */
@Service
public class TokenService {
    private static final String TOKEN_KEY_PREFIX = "auth:token:";
    private static final String USER_TOKENS_PREFIX = "auth:user-tokens:";

    @Resource
    private StringRedisTemplate redis;

    //白名单有效期与 token 有效期保持一致
    @Value("${jwt.expiration:3600000}")
    private long expirationMillis;

    //登录成功后登记 token
    public void save(String jti, Long userId) {
        Duration ttl = Duration.ofMillis(expirationMillis);
        redis.opsForValue().set(
                TOKEN_KEY_PREFIX + jti,
                userId == null ? "" : userId.toString(),
                ttl);
        if (userId != null) {
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            redis.opsForSet().add(userTokensKey, jti);
            redis.expire(userTokensKey, ttl);
        }
    }

    //校验 token 是否仍在白名单中
    public boolean isValid(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY_PREFIX + jti));
    }

    //退出登录时撤销 token
    public void revoke(String jti) {
        if (jti == null) {
            return;
        }
        String userId = redis.opsForValue().get(TOKEN_KEY_PREFIX + jti);
        redis.delete(TOKEN_KEY_PREFIX + jti);
        if (userId != null && !userId.isBlank()) {
            redis.opsForSet().remove(USER_TOKENS_PREFIX + userId, jti);
        }
    }

    //禁用用户或重置密码后，撤销该用户全部 token
    public void revokeAllForUser(Long userId) {
        if (userId == null) {
            return;
        }
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        Set<String> jtis = redis.opsForSet().members(userTokensKey);
        if (jtis != null && !jtis.isEmpty()) {
            redis.delete(jtis.stream().map(jti -> TOKEN_KEY_PREFIX + jti).toList());
        }
        redis.delete(userTokensKey);
    }
}
