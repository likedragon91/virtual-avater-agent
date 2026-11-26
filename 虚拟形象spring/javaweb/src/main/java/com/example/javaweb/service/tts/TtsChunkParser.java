package com.example.javaweb.service.tts;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 解析火山 HTTP Chunked/SSE 单向流式的行分隔 JSON。
 *
 * 成功响应：{"code":0,"data":"<base64 音频块>"}，最后一行为 code=20000000。
 * 这里只负责协议，不访问网络，便于单测。
 */
public final class TtsChunkParser {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_FINISHED = 20000000;

    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
    private final int maxAudioBytes;

    private boolean finished;
    private String errorCode;
    private String errorMessage;

    public TtsChunkParser(int maxAudioBytes) {
        this.maxAudioBytes = Math.max(maxAudioBytes, 1024);
    }

    /**
     * @return 本次行解码出的音频块；无音频时返回空数组
     */
    public byte[] acceptLine(String rawLine) {
        if (rawLine == null) {
            return new byte[0];
        }
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith(":")) {
            return new byte[0];
        }
        if (line.startsWith("data:")) {
            line = line.substring(5).trim();
        }
        if (line.isEmpty() || "[DONE]".equals(line)) {
            if ("[DONE]".equals(line)) {
                finished = true;
            }
            return new byte[0];
        }

        JSONObject object;
        try {
            object = JSONUtil.parseObj(line);
        } catch (Exception e) {
            // 非 JSON 行（心跳/注释）直接忽略，不打断整段音频
            return new byte[0];
        }

        Integer code = object.getInt("code");
        if (code == null) {
            return new byte[0];
        }

        if (code == CODE_FINISHED) {
            finished = true;
        }
        if (code != CODE_SUCCESS && code != CODE_FINISHED) {
            errorCode = String.valueOf(code);
            String message = object.getStr("message");
            if (message == null || message.isBlank()) {
                JSONObject header = object.getJSONObject("header");
                message = header == null ? null : header.getStr("message");
            }
            errorMessage = message == null || message.isBlank() ? "语音合成失败" : message;
            return new byte[0];
        }

        String data = object.getStr("data");
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }
        try {
            byte[] chunk = Base64.getDecoder().decode(data);
            if (audio.size() + chunk.length > maxAudioBytes) {
                errorCode = "TTS_AUDIO_TOO_LARGE";
                errorMessage = "语音合成结果过大";
                return new byte[0];
            }
            audio.writeBytes(chunk);
            return chunk;
        } catch (IllegalArgumentException e) {
            errorCode = "TTS_DECODE_ERROR";
            errorMessage = "语音合成结果解析失败";
            return new byte[0];
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean hasError() {
        return errorCode != null;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public byte[] audio() {
        return audio.toByteArray();
    }
}
