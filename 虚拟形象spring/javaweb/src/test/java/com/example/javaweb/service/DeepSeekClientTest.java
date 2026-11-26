package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import com.example.javaweb.config.DeepSeekProperties;
import com.example.javaweb.dto.agent.LlmStreamResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekClientTest {

    private static DeepSeekProperties baseProps() {
        DeepSeekProperties props = new DeepSeekProperties();
        props.setApiKey("test-key");
        props.setRequestTimeoutMs(5000);
        return props;
    }

    private static DeepSeekClient client(DeepSeekProperties props) {
        DeepSeekClient client = new DeepSeekClient();
        ReflectionTestUtils.setField(client, "props", props);
        client.init();
        return client;
    }

    private static DeepSeekClient client(int maxTokens) {
        DeepSeekProperties props = baseProps();
        props.setMaxTokens(maxTokens);
        return client(props);
    }

    // ---------- 请求体 ----------

    @Test
    void usesGlobalMaxTokensWhenNoOverride() {
        JSONObject body = client(4096).buildBody(List.of(), List.of(), null);
        assertThat(body.getInt("max_tokens")).isEqualTo(4096);
    }

    @Test
    void appliesOverride() {
        JSONObject body = client(4096).buildBody(List.of(), List.of(), 1024);
        assertThat(body.getInt("max_tokens")).isEqualTo(1024);
    }

    @Test
    void overrideCannotExceedGlobal() {
        JSONObject body = client(4096).buildBody(List.of(), List.of(), 9999);
        assertThat(body.getInt("max_tokens")).isEqualTo(4096);
    }

    @Test
    void overrideIsClampedToAtLeastOneToken() {
        JSONObject body = client(4096).buildBody(List.of(), List.of(), 0);
        assertThat(body.getInt("max_tokens")).isEqualTo(1);
    }

    @Test
    void requestShapeIsUnchanged() {
        JSONObject body = client(4096).buildBody(
                List.of(Map.of("role", "user", "content", "hi")),
                List.of(Map.of("type", "function")),
                1024);

        assertThat(body.getStr("model")).isEqualTo("deepseek-chat");
        assertThat(body.getBool("stream")).isTrue();
        assertThat(body.getJSONArray("tools")).hasSize(1);
        assertThat(body.getStr("tool_choice")).isEqualTo("auto");
        assertThat(body.getJSONObject("stream_options").getBool("include_usage")).isTrue();
    }

    // ---------- 上游选择 ----------

    @Test
    void proxyDisabledUsesDirectOnly() {
        DeepSeekProperties props = baseProps();
        props.setProxyEnabled(false);
        props.setBaseUrl("https://api.deepseek.com/");

        assertThat(client(props).resolveBaseUrls()).containsExactly("https://api.deepseek.com");
    }

    @Test
    void proxyEnabledUsesProxyThenDirect() {
        DeepSeekProperties props = baseProps();
        props.setProxyEnabled(true);
        props.setBaseUrl("https://api.deepseek.com");
        props.setProxyUrl("http://127.0.0.1:8787/bili/https://api.deepseek.com");

        assertThat(client(props).resolveBaseUrls()).containsExactly(
                "http://127.0.0.1:8787/bili/https://api.deepseek.com",
                "https://api.deepseek.com");
    }

    @Test
    void blankProxyFallsBackToDirectOnly() {
        DeepSeekProperties props = baseProps();
        props.setProxyEnabled(true);
        props.setProxyUrl("  ");
        props.setBaseUrl("https://api.deepseek.com");

        assertThat(client(props).resolveBaseUrls()).containsExactly("https://api.deepseek.com");
    }

    @Test
    void fallbackDisabledUsesProxyOnly() {
        DeepSeekProperties props = baseProps();
        props.setProxyEnabled(true);
        props.setFallbackToDirect(false);
        props.setProxyUrl("http://127.0.0.1:8787/bili/https://api.deepseek.com");
        props.setBaseUrl("https://api.deepseek.com");

        assertThat(client(props).resolveBaseUrls()).containsExactly(
                "http://127.0.0.1:8787/bili/https://api.deepseek.com");
    }

    // ---------- 代理与回退 ----------

    @Test
    void usesProxyWhenAvailableAndSendsSessionKey() throws Exception {
        AtomicReference<String> proxySession = new AtomicReference<>();
        AtomicBoolean directCalled = new AtomicBoolean(false);
        HttpServer proxy = startServer(exchange -> {
            proxySession.set(exchange.getRequestHeaders().getFirst("x-session-id"));
            sendSse(exchange, "proxied");
        });
        HttpServer direct = startServer(exchange -> {
            directCalled.set(true);
            sendSse(exchange, "direct");
        });
        try {
            DeepSeekClient client = proxyClient(proxy, direct);

            StringBuilder answer = new StringBuilder();
            LlmStreamResult result = client.streamChat(
                    List.of(Map.of("role", "user", "content", "hi")), null, null, "7", answer::append);

            assertThat(answer.toString()).isEqualTo("proxied");
            assertThat(result.getFinishReason()).isEqualTo("stop");
            assertThat(proxySession.get()).isEqualTo("7");
            assertThat(directCalled).isFalse();
        } finally {
            stop(proxy, direct);
        }
    }

    @Test
    void fallsBackToDirectWhenProxyFailsBeforeEmitting() throws Exception {
        AtomicReference<String> proxySession = new AtomicReference<>();
        AtomicReference<String> directSession = new AtomicReference<>();
        HttpServer proxy = startServer(exchange -> {
            proxySession.set(exchange.getRequestHeaders().getFirst("x-session-id"));
            exchange.sendResponseHeaders(503, -1);
        });
        HttpServer direct = startServer(exchange -> {
            directSession.set(exchange.getRequestHeaders().getFirst("x-session-id"));
            sendSse(exchange, "hi");
        });
        try {
            DeepSeekClient client = proxyClient(proxy, direct);

            StringBuilder answer = new StringBuilder();
            LlmStreamResult result = client.streamChat(
                    List.of(Map.of("role", "user", "content", "hi")), null, null, "42", answer::append);

            assertThat(answer.toString()).isEqualTo("hi");
            assertThat(result.getFinishReason()).isEqualTo("stop");
            assertThat(proxySession.get()).isEqualTo("42");
            // 回退到直连时不把内部会话 id 送给上游
            assertThat(directSession.get()).isNull();
        } finally {
            stop(proxy, direct);
        }
    }

    private static DeepSeekClient proxyClient(HttpServer proxy, HttpServer direct) {
        DeepSeekProperties props = baseProps();
        props.setBaseUrl("http://127.0.0.1:" + direct.getAddress().getPort());
        props.setProxyUrl("http://127.0.0.1:" + proxy.getAddress().getPort());
        props.setProxyEnabled(true);
        props.setFallbackToDirect(true);
        props.setMaxRetries(0);
        return client(props);
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static void sendSse(HttpExchange exchange, String text) throws IOException {
        byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"" + text
                + "\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void stop(HttpServer... servers) {
        for (HttpServer server : servers) {
            server.stop(0);
        }
    }
}
