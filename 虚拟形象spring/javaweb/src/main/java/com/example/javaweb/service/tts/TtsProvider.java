package com.example.javaweb.service.tts;

public interface TtsProvider {

    /**
     * 合成一段文本。
     *
     * @param text       已校验长度的文本
     * @param speaker    音色 ID
     * @param speechRate 语速覆盖值，null 表示用配置默认值
     * @param uid        上游统计用的用户标识，不包含敏感信息
     */
    TtsAudio synthesize(String text, String speaker, Integer speechRate, String uid);
}
