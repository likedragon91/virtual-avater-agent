package com.example.javaweb.service.tts;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;

/**
 * TTS 音频缓存：相同文本 + 相同音色直接复用，避免重复计费。
 * Redis 不可用时降级为不缓存，但不阻塞合成。
 */
@Service
public class TtsAudioCache {
    private static final Logger log = LoggerFactory.getLogger(TtsAudioCache.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public byte[] get(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? null : Base64.getDecoder().decode(value);
        } catch (Exception e) {
            log.warn("tts cache read failed, key={}", key, e);
            return null;
        }
    }

    public void put(String key, byte[] audio, long ttlSeconds) {
        if (audio == null || audio.length == 0 || ttlSeconds <= 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key,
                    Base64.getEncoder().encodeToString(audio),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("tts cache write failed, key={}", key, e);
        }
    }
}
