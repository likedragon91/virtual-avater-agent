package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.entity.User;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.UserMapper;
import com.example.javaweb.util.Sm3Util;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码找回密码。
 *
 * 安全约定：
 * - 邮箱不存在时也返回成功，避免枚举账号；
 * - 验证码只存哈希，6 位数字，10 分钟有效，最多尝试 5 次；
 * - 同一邮箱 60 秒内只能发送一次；
 * - 重置成功后撤销该用户全部 token。
 */
@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String CODE_PREFIX = "auth:reset:code:";
    private static final String COOLDOWN_PREFIX = "auth:reset:cooldown:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate redis;

    @Resource
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Resource
    private TokenService tokenService;

    @Resource
    private UserAuthStateService userAuthStateService;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${mail.from:}")
    private String mailFrom;

    @Value("${auth.reset.code-ttl-seconds:600}")
    private long codeTtlSeconds;

    @Value("${auth.reset.resend-cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${auth.reset.max-attempts:5}")
    private int maxAttempts;

    @PostConstruct
    void logMailConfig() {
        boolean configured = mailHost != null && !mailHost.isBlank();
        log.info("password reset mail config: configured={}, host={}, usernameConfigured={}, fromConfigured={}",
                configured,
                mailHost,
                mailUsername != null && !mailUsername.isBlank(),
                mailFrom != null && !mailFrom.isBlank());
    }

    /**
     * 请求发送验证码。无论邮箱是否存在，调用方都展示同一句提示。
     */
    public void requestReset(String email) {
        String normalized = normalizeEmail(email);
        User user = findByEmail(normalized);
        if (user == null) {
            log.info("password reset requested for unknown email");
            return;
        }

        String cooldownKey = COOLDOWN_PREFIX + normalized;
        Boolean allowed = redis.opsForValue().setIfAbsent(
                cooldownKey, "1", Duration.ofSeconds(Math.max(cooldownSeconds, 1)));
        if (!Boolean.TRUE.equals(allowed)) {
            return;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String salt = Sm3Util.generateSalt();
        JSONObject payload = new JSONObject();
        payload.set("hash", Sm3Util.backendEncrypt(code, salt));
        payload.set("salt", salt);
        payload.set("attempts", 0);

        String codeKey = CODE_PREFIX + normalized;
        redis.opsForValue().set(codeKey, payload.toString(),
                Duration.ofSeconds(Math.max(codeTtlSeconds, 60)));

        if (sendMail(user.getEmail(), code)) {
            log.info("password reset mail sent, userId={}", user.getId());
        } else {
            // 邮件没发出去时允许用户重试，避免白白占用冷却时间
            redis.delete(codeKey);
            redis.delete(cooldownKey);
            log.warn("password reset mail not sent, userId={}", user.getId());
        }
    }

    /**
     * 校验验证码并重置密码。newPassword 是前端 SM3 后的 64 位十六进制摘要。
     */
    public void reset(String email, String code, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("两次输入密码不一致");
        }
        String normalized = normalizeEmail(email);
        String codeKey = CODE_PREFIX + normalized;
        String raw = redis.opsForValue().get(codeKey);
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("RESET_CODE_INVALID", "验证码无效或已过期");
        }

        JSONObject payload;
        try {
            payload = JSONUtil.parseObj(raw);
        } catch (Exception e) {
            redis.delete(codeKey);
            throw new BusinessException("RESET_CODE_INVALID", "验证码无效或已过期");
        }

        int attempts = payload.getInt("attempts", 0);
        if (attempts >= maxAttempts) {
            redis.delete(codeKey);
            throw new BusinessException("RESET_CODE_INVALID", "验证码尝试次数过多，请重新获取");
        }

        String salt = payload.getStr("salt");
        String hash = payload.getStr("hash");
        if (salt == null || hash == null
                || !Sm3Util.constantTimeEquals(Sm3Util.backendEncrypt(code, salt), hash)) {
            attempts++;
            payload.set("attempts", attempts);
            Long ttl = redis.getExpire(codeKey, TimeUnit.SECONDS);
            if (attempts >= maxAttempts || ttl == null || ttl <= 0) {
                redis.delete(codeKey);
            } else {
                redis.opsForValue().set(codeKey, payload.toString(), Duration.ofSeconds(ttl));
            }
            throw new BusinessException("RESET_CODE_INVALID", "验证码错误");
        }

        User user = findByEmail(normalized);
        if (user == null) {
            redis.delete(codeKey);
            throw new BusinessException("RESET_CODE_INVALID", "验证码无效或已过期");
        }

        String newSalt = Sm3Util.generateSalt();
        user.setSalt(newSalt);
        user.setPassword(Sm3Util.backendEncrypt(newPassword, newSalt));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        redis.delete(codeKey);
        redis.delete(COOLDOWN_PREFIX + normalized);
        tokenService.revokeAllForUser(user.getId());
        userAuthStateService.evict(user.getId());
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .last("LIMIT 1"));
    }

    private boolean sendMail(String to, String code) {
        if (mailHost == null || mailHost.isBlank()) {
            log.warn("password reset mail skipped: MAIL_HOST is empty");
            return false;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("password reset mail skipped: JavaMailSender bean not created");
            return false;
        }
        String from = mailFrom != null && !mailFrom.isBlank() ? mailFrom : mailUsername;
        if (from == null || from.isBlank()) {
            log.warn("password reset mail skipped: MAIL_FROM and MAIL_USERNAME are empty");
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("虚拟形象 - 密码重置验证码");
        message.setText("你的验证码是：" + code + "\n\n"
                + "验证码 " + Math.max(codeTtlSeconds / 60, 1) + " 分钟内有效。\n"
                + "如非本人操作，请忽略本邮件。");
        try {
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.error("send password reset mail failed", e);
            return false;
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
