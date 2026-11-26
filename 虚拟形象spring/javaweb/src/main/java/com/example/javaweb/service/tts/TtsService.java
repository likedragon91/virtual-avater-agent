package com.example.javaweb.service.tts;

import com.example.javaweb.config.TtsProperties;
import com.example.javaweb.dto.request.TtsSynthesizeCommandDTO;
import com.example.javaweb.dto.response.TtsConfigResponseDTO;
import com.example.javaweb.dto.response.TtsVoiceDTO;
import com.example.javaweb.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * TTS 业务层：校验、缓存、配额、音色白名单。
 * 上游供应商由 TtsProvider 抽象，当前实现为火山引擎。
 */
@Service
public class TtsService {
    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    @Resource
    private TtsProperties properties;

    @PostConstruct
    void logStatus() {
        TtsProperties.Volcengine config = properties.getVolcengine();
        boolean hasApiKey = TtsProperties.hasText(config.getApiKey());
        boolean hasLegacy = TtsProperties.hasText(config.getAppId())
                && TtsProperties.hasText(config.getAccessKey());
        String keyMode = hasApiKey ? "api-key" : (hasLegacy ? "legacy" : "none");
        log.info("TTS status: enabled={}, configured={}, keyMode={}, resourceId={}, speaker={}",
                properties.isEnabled(), properties.isVolcengineConfigured(), keyMode,
                config.getResourceId(), config.getSpeaker());
        if (properties.isEnabled() && !properties.isVolcengineConfigured()) {
            log.warn("TTS 已开放但未配置密钥：请设置 VOLC_TTS_API_KEY（或旧版 APP_ID/ACCESS_KEY）后重启后端");
        }
    }

    @Resource
    private TtsProvider provider;

    @Resource
    private TtsAudioCache audioCache;

    @Resource
    private TtsUsageService usageService;

    public TtsResult synthesize(Long userId, TtsSynthesizeCommandDTO command) {
        if (!properties.isVolcengineConfigured()) {
            throw new BusinessException("TTS_NOT_CONFIGURED", "语音合成未配置");
        }

        String text = command.getText() == null ? "" : command.getText().trim();
        if (text.isEmpty()) {
            throw new BusinessException("PARAM_ERROR", "语音文本不能为空");
        }
        if (text.length() > properties.getMaxCharsPerRequest()) {
            throw new BusinessException("TTS_TEXT_TOO_LONG",
                    "单次语音文本不能超过 " + properties.getMaxCharsPerRequest() + " 字");
        }

        TtsProperties.Volcengine config = properties.getVolcengine();
        String speaker = resolveSpeaker(command.getSpeaker());
        Integer speed = command.getSpeed();
        String cacheKey = cacheKey(text, speaker, speed);

        byte[] cached = audioCache.get(cacheKey);
        if (cached != null && cached.length > 0) {
            TtsAudio audio = new TtsAudio(cached,
                    TtsAudio.contentTypeFor(config.getFormat()),
                    config.getSampleRate(), 1);
            return new TtsResult(audio, true);
        }

        usageService.checkAndConsume(userId, text.length());
        TtsAudio audio = provider.synthesize(text, speaker, speed, String.valueOf(userId));
        audioCache.put(cacheKey, audio.bytes(), properties.getCacheTtlSeconds());
        return new TtsResult(audio, false);
    }

    public TtsConfigResponseDTO config(Long userId) {
        TtsProperties.Volcengine config = properties.getVolcengine();
        TtsConfigResponseDTO dto = new TtsConfigResponseDTO();
        // 入口是否展示只看 tts.enabled；是否可真正合成看密钥与音色是否齐全
        dto.setEnabled(properties.isEnabled());
        dto.setConfigured(properties.isVolcengineConfigured());
        dto.setProvider(properties.getProvider());
        dto.setDefaultSpeaker(config.getSpeaker());
        dto.setFormat(config.getFormat());
        dto.setSampleRate(config.getSampleRate());
        dto.setMaxCharsPerRequest(properties.getMaxCharsPerRequest());
        dto.setDailyCharQuota(properties.getDailyCharQuota());
        dto.setUsedChars(usageService.used(userId));
        dto.setRemainingChars(usageService.remaining(userId));

        List<TtsVoiceDTO> voices = new ArrayList<>();
        if (config.getVoices() != null) {
            for (TtsProperties.Voice voice : config.getVoices()) {
                if (voice == null || !TtsProperties.hasText(voice.getId())) {
                    continue;
                }
                voices.add(toVoice(voice));
            }
        }
        boolean hasDefault = voices.stream()
                .anyMatch(voice -> voice.getId().equals(config.getSpeaker()));
        if (!hasDefault && TtsProperties.hasText(config.getSpeaker())) {
            TtsVoiceDTO fallback = new TtsVoiceDTO();
            fallback.setId(config.getSpeaker());
            fallback.setName("默认音色");
            fallback.setLanguage("zh-CN");
            voices.add(0, fallback);
        }
        dto.setVoices(voices);
        return dto;
    }

    private String resolveSpeaker(String requested) {
        TtsProperties.Volcengine config = properties.getVolcengine();
        String fallback = config.getSpeaker();
        if (!TtsProperties.hasText(requested)) {
            return fallback;
        }
        if (config.getVoices() == null || config.getVoices().isEmpty()) {
            return requested.equals(fallback) ? requested : fallback;
        }
        boolean allowed = config.getVoices().stream()
                .anyMatch(voice -> voice != null && requested.equals(voice.getId()));
        return allowed ? requested : fallback;
    }

    private String cacheKey(String text, String speaker, Integer speed) {
        TtsProperties.Volcengine config = properties.getVolcengine();
        int effectiveSpeed = speed != null ? speed : config.getSpeechRate();
        String raw = String.join("|",
                config.getResourceId(),
                speaker,
                config.getModel() == null ? "" : config.getModel(),
                config.getFormat(),
                String.valueOf(config.getSampleRate()),
                String.valueOf(config.getBitRate()),
                String.valueOf(config.getLoudnessRate()),
                String.valueOf(effectiveSpeed),
                text);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return "tts:audio:" + hex;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 必然存在；兜底用 hash 避免直接拒绝请求
            return "tts:audio:" + Integer.toHexString(raw.hashCode());
        }
    }

    private static TtsVoiceDTO toVoice(TtsProperties.Voice voice) {
        TtsVoiceDTO dto = new TtsVoiceDTO();
        dto.setId(voice.getId());
        dto.setName(TtsProperties.hasText(voice.getName()) ? voice.getName() : voice.getId());
        dto.setLanguage(TtsProperties.hasText(voice.getLanguage()) ? voice.getLanguage() : "zh-CN");
        dto.setGender(voice.getGender());
        return dto;
    }

    public record TtsResult(TtsAudio audio, boolean cacheHit) {
    }
}
