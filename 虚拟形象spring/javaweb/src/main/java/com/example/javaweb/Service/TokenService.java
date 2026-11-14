package com.example.javaweb.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT 白名单。
 * JWT 本身只负责携带身份，token 是否仍然有效由 Redis 集中管理：
 * - 登录成功：写入 auth:token:<jti>
 * - 每次请求：校验 jti 是否还在白名单中
 * - 退出登录：删除对应 jti，token 立即失效
 */
@Service
public class TokenService {
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    @Resource
    private StringRedisTemplate redis;

    //白名单有效期与 token 有效期保持一致
    @Value("${jwt.expiration:3600000}")
    private long expirationMillis;

    //登录成功后登记 token
    public void save(String jti, Long userId) {
        redis.opsForValue().set(
                TOKEN_KEY_PREFIX + jti,
                userId == null ? "" : userId.toString(),
                Duration.ofMillis(expirationMillis));
    }

    //校验 token 是否仍在白名单中
    public boolean isValid(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY_PREFIX + jti));
    }

    //退出登录时撤销 token
    public void revoke(String jti) {
        if (jti != null) {
            redis.delete(TOKEN_KEY_PREFIX + jti);
        }
    }
}
