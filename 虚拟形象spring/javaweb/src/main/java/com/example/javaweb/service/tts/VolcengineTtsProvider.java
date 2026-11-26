package com.example.javaweb.service.tts;

import cn.hutool.json.JSONObject;
import com.example.javaweb.config.TtsProperties;
import com.example.javaweb.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * 火山引擎豆包语音：HTTP Chunked 单向流式 V3。
 *
 * 文档：
 * https://docs.volcengine.com/docs/doubao-voice/http-chunked-sse-unidirectional-streaming-v3
 *
 * 请求一次性提交文本，响应按行返回 base64 音频块；这里解码后返回完整音频。
 * 密钥只在服务端使用，错误信息不回显密钥或上游原始报错。
 */
@Service
public class VolcengineTtsProvider implements TtsProvider {
    private static final Logger log = LoggerFactory.getLogger(VolcengineTtsProvider.class);

    @Resource
    private TtsProperties properties;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        TtsProperties.Volcengine config = properties.getVolcengine();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(config.getConnectTimeoutMs(), 1000)))
                .build();
    }

    @Override
    public TtsAudio synthesize(String text, String speaker, Integer speechRate, String uid) {
        if (!properties.isVolcengineConfigured()) {
            throw new BusinessException("TTS_NOT_CONFIGURED", "语音合成未配置");
        }
        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        int maxRetries = Math.max(properties.getVolcengine().getMaxRetries(), 0);

        for (int attempt = 0; ; attempt++) {
            try {
                TtsAudio audio = doSynthesize(text, speaker, speechRate, uid, requestId);
                log.info("tts ok, requestId={}, chars={}, bytes={}, costMs={}",
                        requestId, text.length(), audio.bytes().length,
                        System.currentTimeMillis() - start);
                return audio;
            } catch (RetryableTtsException e) {
                if (attempt >= maxRetries) {
                    log.warn("tts failed, requestId={}, code={}, costMs={}",
                            requestId, e.getCode(), System.currentTimeMillis() - start);
                    throw e;
                }
                sleep(300L * (attempt + 1));
            }
        }
    }

    private TtsAudio doSynthesize(String text, String speaker, Integer speechRate,
                                  String uid, String requestId) {
        TtsProperties.Volcengine config = properties.getVolcengine();
        HttpRequest request = buildRequest(config, text, speaker, speechRate, uid, requestId);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new RetryableTtsException("TTS_TIMEOUT", "语音合成超时，请重试");
        } catch (IOException e) {
            throw new RetryableTtsException("TTS_UNAVAILABLE", "语音合成服务连接失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("TTS_INTERRUPTED", "语音合成已中断");
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new BusinessException("TTS_AUTH_FAILED", "语音合成鉴权失败，请检查配置");
        }
        if (status == 429) {
            throw new RetryableTtsException("TTS_RATE_LIMITED", "语音合成请求过于频繁，请稍后重试");
        }
        if (status >= 500) {
            throw new RetryableTtsException("TTS_UNAVAILABLE", "语音合成服务暂时不可用");
        }
        if (status != 200) {
            readAll(response.body());
            throw new BusinessException("TTS_UNAVAILABLE", "语音合成失败，请稍后重试");
        }

        TtsChunkParser parser = new TtsChunkParser(properties.getMaxAudioBytes());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parser.acceptLine(line);
                if (parser.hasError()) {
                    throw mapUpstreamError(parser);
                }
                if (parser.isFinished()) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RetryableTtsException("TTS_UNAVAILABLE", "语音合成连接中断，请重试");
        }

        byte[] audio = parser.audio();
        if (audio.length == 0) {
            throw new BusinessException("TTS_EMPTY_AUDIO", "语音合成没有返回音频");
        }
        return new TtsAudio(audio, TtsAudio.contentTypeFor(config.getFormat()),
                config.getSampleRate(), 1);
    }

    private HttpRequest buildRequest(TtsProperties.Volcengine config, String text,
                                     String speaker, Integer speechRate,
                                     String uid, String requestId) {
        JSONObject body = new JSONObject();
        JSONObject user = new JSONObject();
        user.set("uid", anonymizeUid(uid));
        body.set("user", user);

        JSONObject req = new JSONObject();
        req.set("text", text);
        req.set("speaker", speaker);
        if (TtsProperties.hasText(config.getModel())) {
            req.set("model", config.getModel());
        }

        JSONObject audioParams = new JSONObject();
        String format = config.getFormat() == null ? "mp3" : config.getFormat().toLowerCase(Locale.ROOT);
        audioParams.set("format", format);
        audioParams.set("sample_rate", config.getSampleRate());
        if (!"pcm".equals(format) && !"wav".equals(format)) {
            audioParams.set("bit_rate", config.getBitRate());
        }
        audioParams.set("speech_rate", speechRate != null ? speechRate : config.getSpeechRate());
        audioParams.set("loudness_rate", config.getLoudnessRate());
        req.set("audio_params", audioParams);
        body.set("req_params", req);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.getEndpoint()))
                .timeout(Duration.ofMillis(Math.max(config.getRequestTimeoutMs(), 1000)))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("X-Api-Resource-Id", config.getResourceId())
                .header("X-Api-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8));

        if (TtsProperties.hasText(config.getApiKey())) {
            builder.header("X-Api-Key", config.getApiKey());
        } else {
            builder.header("X-Api-App-Key", config.getAppId());
            builder.header("X-Api-Access-Key", config.getAccessKey());
        }
        return builder.build();
    }

    private BusinessException mapUpstreamError(TtsChunkParser parser) {
        String code = parser.errorCode();
        if ("55000000".equals(code)) {
            return new BusinessException("TTS_SPEAKER_MISMATCH",
                    "音色与资源 ID 不匹配，请检查语音配置");
        }
        if ("TTS_AUDIO_TOO_LARGE".equals(code)) {
            return new BusinessException("TTS_AUDIO_TOO_LARGE", "语音合成结果过大");
        }
        if ("TTS_DECODE_ERROR".equals(code)) {
            return new RetryableTtsException("TTS_UNAVAILABLE", "语音合成结果解析失败");
        }
        return new RetryableTtsException("TTS_UNAVAILABLE", "语音合成失败，请稍后重试");
    }

    private static String anonymizeUid(String uid) {
        String raw = uid == null ? "anonymous" : uid;
        return "u" + Integer.toHexString(raw.hashCode());
    }

    private static void readAll(InputStream input) {
        try (InputStream in = input) {
            in.readAllBytes();
        } catch (IOException ignored) {
            // 错误响应体只用于关闭连接
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class RetryableTtsException extends BusinessException {
        RetryableTtsException(String code, String message) {
            super(code, message);
        }
    }
}
