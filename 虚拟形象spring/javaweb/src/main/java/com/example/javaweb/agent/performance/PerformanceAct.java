package com.example.javaweb.agent.performance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 一次表演指令：情绪、动作与强度。
 * 字段都可能为空；三个字段全空时视为无效指令，调用方应丢弃。
 */
public record PerformanceAct(String emotion, String motion, double intensity) {

    public static final Set<String> EMOTIONS =
            Set.of("neutral", "joy", "sadness", "anger", "surprise");

    public static final Set<String> MOTIONS =
            Set.of("nod", "shake", "tilt", "bounce", "hop", "look_away");

    public boolean isEmpty() {
        return emotion == null && motion == null;
    }

    public Map<String, Object> toEventPayload(int sentenceIndex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (emotion != null) {
            payload.put("emotion", emotion);
        }
        if (motion != null) {
            payload.put("motion", motion);
        }
        payload.put("intensity", clamp01(intensity));
        payload.put("sentenceIndex", Math.max(sentenceIndex, 0));
        return payload;
    }

    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
