package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TtsConfigResponseDTO {
    /** 是否开放语音入口（只取决于 tts.enabled） */
    private boolean enabled;
    /** 是否已配置密钥与音色；未配置时合成会返回 TTS_NOT_CONFIGURED */
    private boolean configured;
    private String provider;
    private String defaultSpeaker;
    private String format;
    private int sampleRate;
    private int maxCharsPerRequest;
    private int dailyCharQuota;
    private int usedChars;
    /** -1 表示未启用日配额 */
    private int remainingChars;
    private List<TtsVoiceDTO> voices = new ArrayList<>();
}
