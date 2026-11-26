package com.example.javaweb.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.javaweb.dto.agent.LlmStreamResult;
import com.example.javaweb.dto.agent.LlmToolCall;
import com.example.javaweb.config.DeepSeekProperties;
import com.example.javaweb.exception.LlmException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * DeepSeek OpenAI 兼容接口的流式客户端。
 * 只支持 deepseek-chat（function calling）；deepseek-reasoner 不支持工具调用。
 */
@Service
public class DeepSeekClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    @Resource
    private DeepSeekProperties props;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(props.getConnectTimeoutMs(), 1000)))
                // 本地上下文代理只处理 HTTP/1.1；HTTP/2 的 h2c upgrade 会被 426 拒绝
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        log.info("llm proxy enabled={}, url={}, fallbackToDirect={}, upstreams={}",
                props.isProxyEnabled(), props.getProxyUrl(), props.isFallbackToDirect(),
                resolveBaseUrls());
    }

    /**
     * 发起一次流式请求。同步读取到结束，每收到一段文本就回调 onDelta。
     * 工具调用会在流结束后一次性返回。
     */
    public LlmStreamResult streamChat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      Consumer<String> onDelta) {
        return streamChat(messages, tools, null, null, onDelta);
    }

    public LlmStreamResult streamChat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      Integer maxTokensOverride,
                                      Consumer<String> onDelta) {
        return streamChat(messages, tools, maxTokensOverride, null, onDelta);
    }

    /**
     * @param maxTokensOverride 单次调用覆盖 max_tokens；null 表示使用全局配置。
     * @param sessionKey        透传给上下文代理的会话键（x-session-id）；null 表示不发送。
     *                          代理未启用时该参数无副作用。
     */
    public LlmStreamResult streamChat(List<Map<String, Object>> messages,
                                      List<Map<String, Object>> tools,
                                      Integer maxTokensOverride,
                                      String sessionKey,
                                      Consumer<String> onDelta) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new LlmException("LLM_NOT_CONFIGURED",
                    "未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }

        long start = System.currentTimeMillis();
        AtomicBoolean emitted = new AtomicBoolean(false);
        List<String> bases = resolveBaseUrls();

        for (int index = 0; index < bases.size(); index++) {
            String baseUrl = bases.get(index);
            boolean lastBase = index == bases.size() - 1;
            // 有回退目标时，代理只试一次，避免本地代理未启动时每条消息都等重试延迟
            int retries = lastBase ? Math.max(props.getMaxRetries(), 0) : 0;
            try {
                LlmStreamResult result = streamWithRetries(
                        baseUrl, messages, tools, maxTokensOverride, sessionKey,
                        onDelta, emitted, retries);
                result.setLatencyMs(System.currentTimeMillis() - start);
                return result;
            } catch (LlmException e) {
                // 代理的任意失败（连接失败、4xx/5xx、超时）都不应阻塞聊天：
                // 只要还没吐字且还有直连目标，就回退。直连自身的错误才向上抛。
                if (emitted.get() || lastBase || !props.isFallbackToDirect()) {
                    throw e;
                }
                log.warn("llm upstream {} failed ({}: {}), falling back to {}",
                        baseUrl, e.getCode(), e.getMessage(), bases.get(index + 1));
            }
        }
        throw new LlmException("LLM_NO_UPSTREAM", "模型服务不可用");
    }

    private LlmStreamResult streamWithRetries(String baseUrl,
                                              List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools,
                                              Integer maxTokensOverride,
                                              String sessionKey,
                                              Consumer<String> onDelta,
                                              AtomicBoolean emitted,
                                              int maxRetries) {
        for (int attempt = 0; ; attempt++) {
            try {
                return doStream(baseUrl, messages, tools, maxTokensOverride,
                        sessionKey, onDelta, emitted);
            } catch (RetryableLlmException e) {
                // 已经吐字或超过重试次数就不再重试，避免重复输出
                if (attempt >= maxRetries || emitted.get()) {
                    throw e;
                }
                sleep(400L * (attempt + 1));
            }
        }
    }

    /**
     * 非交互场景（摘要、后台整理）用：内部收集流式内容后返回完整文本。
     */
    public String complete(List<Map<String, Object>> messages, int maxTokens) {
        return complete(messages, maxTokens, null);
    }

    public String complete(List<Map<String, Object>> messages, int maxTokens, String sessionKey) {
        StringBuilder answer = new StringBuilder();
        streamChat(messages, null, maxTokens, sessionKey, answer::append);
        return answer.toString();
    }

    /** 代理启用时返回 [代理, 直连]；否则只返回直连。 */
    List<String> resolveBaseUrls() {
        String direct = normalizeBaseUrl(props.getBaseUrl());
        if (!props.isProxyEnabled()) {
            return List.of(direct);
        }
        String proxy = normalizeBaseUrl(props.getProxyUrl());
        if (proxy.isBlank()) {
            log.warn("deepseek proxy enabled but proxy-url is blank, using direct upstream");
            return List.of(direct);
        }
        if (props.isFallbackToDirect() && !proxy.equals(direct)) {
            return List.of(proxy, direct);
        }
        return List.of(proxy);
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null) {
            return "";
        }
        String base = value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private LlmStreamResult doStream(String baseUrl,
                                     List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools,
                                     Integer maxTokensOverride,
                                     String sessionKey,
                                     Consumer<String> onDelta,
                                     AtomicBoolean emitted) {
        JSONObject body = buildBody(messages, tools, maxTokensOverride);
        boolean proxy = props.isProxyEnabled()
                && normalizeBaseUrl(baseUrl).equals(normalizeBaseUrl(props.getProxyUrl()));
        long timeoutMs = proxy
                ? Math.max(props.getProxyTimeoutMs(), 1000)
                : Math.max(props.getRequestTimeoutMs(), 1000);
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(baseUrl))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + props.getApiKey());
        // 会话键只给代理用，不把内部会话 id 送给上游
        if (proxy && sessionKey != null && !sessionKey.isBlank()) {
            builder.header("x-session-id", sessionKey);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new LlmException("LLM_TIMEOUT", "模型响应超时，请重试", e);
        } catch (IOException e) {
            throw new RetryableLlmException("LLM_IO_ERROR", "模型服务连接失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM_INTERRUPTED", "模型调用已中断", e);
        }

        if (response.statusCode() != 200) {
            String raw = readAll(response.body());
            String message = extractErrorMessage(raw);
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new RetryableLlmException("LLM_HTTP_" + response.statusCode(),
                        "模型服务繁忙，请稍后重试", null);
            }
            throw new LlmException("LLM_HTTP_" + response.statusCode(), message);
        }

        return parseStream(response.body(), onDelta, emitted);
    }

    private LlmStreamResult parseStream(InputStream input,
                                        Consumer<String> onDelta,
                                        AtomicBoolean emitted) {
        LlmStreamResult result = new LlmStreamResult();
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }

                JSONObject chunk = JSONUtil.parseObj(payload);
                JSONObject usage = chunk.getJSONObject("usage");
                if (usage != null) {
                    result.setPromptTokens(usage.getInt("prompt_tokens"));
                    result.setCompletionTokens(usage.getInt("completion_tokens"));
                }

                JSONArray choices = chunk.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                JSONObject choice = choices.getJSONObject(0);
                if (choice == null) {
                    continue;
                }

                JSONObject delta = choice.getJSONObject("delta");
                if (delta != null) {
                    Object textObject = delta.get("content");
                    if (textObject instanceof String text && !text.isEmpty()) {
                        content.append(text);
                        emitted.set(true);
                        onDelta.accept(text);
                    }

                    JSONArray deltaToolCalls = delta.getJSONArray("tool_calls");
                    if (deltaToolCalls != null) {
                        accumulateToolCalls(deltaToolCalls, toolCalls);
                    }
                }

                String finishReason = choice.getStr("finish_reason");
                if (finishReason != null && !finishReason.isBlank()) {
                    result.setFinishReason(finishReason);
                }
            }
        } catch (IOException e) {
            throw new LlmException("LLM_STREAM_ERROR", "模型流式响应中断，请重试", e);
        }

        result.setContent(content.toString());
        List<LlmToolCall> calls = new ArrayList<>();
        toolCalls.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey()))
                .forEach(entry -> {
                    ToolCallAccumulator acc = entry.getValue();
                    if (acc.name != null && !acc.name.isBlank()) {
                        LlmToolCall call = new LlmToolCall();
                        call.setId(acc.id);
                        call.setName(acc.name);
                        call.setArguments(acc.arguments.toString());
                        calls.add(call);
                    }
                });
        result.setToolCalls(calls);
        if (result.getFinishReason() == null) {
            result.setFinishReason(calls.isEmpty() ? "stop" : "tool_calls");
        }
        return result;
    }

    private void accumulateToolCalls(JSONArray deltaToolCalls,
                                     Map<Integer, ToolCallAccumulator> target) {
        for (int i = 0; i < deltaToolCalls.size(); i++) {
            JSONObject item = deltaToolCalls.getJSONObject(i);
            if (item == null) {
                continue;
            }
            Integer indexValue = item.getInt("index");
            int index = indexValue != null ? indexValue : i;
            ToolCallAccumulator acc = target.computeIfAbsent(index, key -> new ToolCallAccumulator());

            String id = item.getStr("id");
            if (id != null && !id.isBlank()) {
                acc.id = id;
            }

            JSONObject function = item.getJSONObject("function");
            if (function == null) {
                continue;
            }
            String name = function.getStr("name");
            if (name != null && !name.isBlank()) {
                acc.name = name;
            }
            Object arguments = function.get("arguments");
            if (arguments instanceof String text) {
                acc.arguments.append(text);
            } else if (arguments != null) {
                acc.arguments.append(JSONUtil.toJsonStr(arguments));
            }
        }
    }

    JSONObject buildBody(List<Map<String, Object>> messages,
                         List<Map<String, Object>> tools,
                         Integer maxTokensOverride) {
        JSONObject body = new JSONObject();
        body.set("model", props.getModel());
        body.set("messages", messages);
        body.set("stream", true);
        body.set("max_tokens", resolveMaxTokens(maxTokensOverride));
        if (props.getTemperature() != null) {
            body.set("temperature", props.getTemperature());
        }
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.set("tool_choice", "auto");
        }
        if (props.isIncludeUsage()) {
            JSONObject options = new JSONObject();
            options.set("include_usage", true);
            body.set("stream_options", options);
        }
        return body;
    }

    /** 覆盖值只能收紧，不能突破全局配置；同时保证至少 1 token */
    private int resolveMaxTokens(Integer override) {
        int base = props.getMaxTokens();
        if (override == null) {
            return base;
        }
        return Math.min(base, Math.max(override, 1));
    }

    private URI buildUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + "/chat/completions");
    }

    private static String readAll(InputStream input) {
        try (InputStream in = input) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String extractErrorMessage(String raw) {
        try {
            JSONObject object = JSONUtil.parseObj(raw);
            JSONObject error = object.getJSONObject("error");
            if (error != null) {
                String message = error.getStr("message");
                if (message != null && !message.isBlank()) {
                    return truncate(message, 300);
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体按通用文案处理，避免把原始内容透给前端
        }
        return "模型服务暂时不可用，请稍后重试";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }

    private static class RetryableLlmException extends LlmException {
        private RetryableLlmException(String code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }
}
