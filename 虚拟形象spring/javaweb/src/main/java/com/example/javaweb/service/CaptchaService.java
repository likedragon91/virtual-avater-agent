package com.example.javaweb.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import com.example.javaweb.dto.response.CaptchaResponseDTO;
import com.example.javaweb.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CaptchaService {
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_COUNT = 4;
    private static final int LINE_COUNT = 20;

    @Resource
    private StringRedisTemplate redis;

    //测试环境可设为 false 以跳过验证码校验
    @Value("${captcha.enabled:true}")
    private boolean enabled;

    @Value("${captcha.expire-seconds:300}")
    private long expireSeconds;

    //生成验证码，答案只保存在 Redis
    public CaptchaResponseDTO generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(WIDTH, HEIGHT, CODE_COUNT, LINE_COUNT);
        String captchaId = IdUtil.fastSimpleUUID();
        redis.opsForValue().set(
                CAPTCHA_KEY_PREFIX + captchaId,
                captcha.getCode(),
                Duration.ofSeconds(expireSeconds));
        return new CaptchaResponseDTO(captchaId, captcha.getImageBase64Data());
    }

    //校验验证码：取出即删除（GETDEL），一次性使用、忽略大小写
    public void verify(String captchaId, String captchaCode) {
        if (!enabled) {
            return;
        }
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException("验证码不能为空");
        }
        String expected = redis.opsForValue().getAndDelete(CAPTCHA_KEY_PREFIX + captchaId);
        if (expected == null) {
            throw new BusinessException("验证码已过期，请刷新");
        }
        if (!expected.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException("验证码错误");
        }
    }
}
