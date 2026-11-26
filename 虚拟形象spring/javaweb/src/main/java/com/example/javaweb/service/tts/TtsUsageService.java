package com.example.javaweb.service.tts;

import com.example.javaweb.config.TtsProperties;
import com.example.javaweb.exception.BusinessException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 按用户/按天的字符配额。缓存命中不扣配额。
 * Redis 异常时放行请求并记录告警，避免语音功能被缓存故障拖垮。
 */
@Service
public class TtsUsageService {
    private static final Logger log = LoggerFactory.getLogger(TtsUsageService.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TtsProperties properties;

    public void checkAndConsume(Long userId, int chars) {
        int quota = properties.getDailyCharQuota();
        if (quota <= 0 || chars <= 0) {
            return;
        }
        String key = quotaKey(userId);
        try {
            Long used = stringRedisTemplate.opsForValue().increment(key, chars);
            if (used != null && used == chars) {
                stringRedisTemplate.expire(key, Duration.ofDays(2));
            }
            if (used != null && used > quota) {
                stringRedisTemplate.opsForValue().decrement(key, chars);
                throw new BusinessException("TTS_QUOTA_EXCEEDED", "今日语音额度已用完");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("tts quota check failed, allow request, userId={}", userId, e);
        }
    }

    public int used(Long userId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(quotaKey(userId));
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            log.warn("tts quota read failed, userId={}", userId, e);
            return 0;
        }
    }

    public int remaining(Long userId) {
        int quota = properties.getDailyCharQuota();
        if (quota <= 0) {
            return -1;
        }
        return Math.max(0, quota - used(userId));
    }

    private static String quotaKey(Long userId) {
        return "tts:quota:" + userId + ":" + LocalDate.now();
    }
}
