package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TTS 配置。密钥只从环境变量读取，禁止写入仓库。
 * 当前供应商为火山引擎豆包语音（HTTP Chunked 单向流式 V3）。
 */
@Data
@ConfigurationProperties(prefix = "tts")
@Component
public class TtsProperties {
    private boolean enabled = false;
    private String provider = "volcengine";
    private int maxCharsPerRequest = 300;
    private int dailyCharQuota = 5000;
    private long cacheTtlSeconds = 604800;
    private int maxAudioBytes = 2 * 1024 * 1024;
    private Volcengine volcengine = new Volcengine();

    @Data
    public static class Volcengine {
        /** 新版控制台 API Key；与 appId + accessKey 二选一 */
        private String apiKey;
        /** 旧版控制台 AppID */
        private String appId;
        /** 旧版控制台 Access Token */
        private String accessKey;
        private String resourceId = "seed-tts-2.0";
        private String endpoint = "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
        private String speaker = "zh_female_vv_uranus_bigtts";
        /** 仅复刻音色需要 */
        private String model;
        private String format = "mp3";
        private int sampleRate = 24000;
        private int bitRate = 64000;
        private int speechRate = 0;
        private int loudnessRate = 0;
        private int connectTimeoutMs = 5000;
        private int requestTimeoutMs = 15000;
        private int maxRetries = 1;
        /** 可选音色清单；为空时只提供默认音色 */
        private List<Voice> voices = new ArrayList<>();
    }

    @Data
    public static class Voice {
        private String id;
        private String name;
        private String language = "zh-CN";
        private String gender;
    }

    public boolean isVolcengineConfigured() {
        if (!enabled || volcengine == null) {
            return false;
        }
        boolean apiKeyMode = hasText(volcengine.getApiKey());
        boolean legacyMode = hasText(volcengine.getAppId()) && hasText(volcengine.getAccessKey());
        return hasText(volcengine.getEndpoint())
                && hasText(volcengine.getResourceId())
                && hasText(volcengine.getSpeaker())
                && (apiKeyMode || legacyMode);
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
