package com.example.javaweb.service.tts;

/**
 * 合成结果。bytes 为解码后的音频（不是 base64）。
 */
public record TtsAudio(byte[] bytes, String contentType, int sampleRate, int channels) {

    public static String contentTypeFor(String format) {
        String value = format == null ? "mp3" : format.toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg_opus" -> "audio/ogg";
            case "pcm" -> "audio/L16";
            default -> "application/octet-stream";
        };
    }
}
