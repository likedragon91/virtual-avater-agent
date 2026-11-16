package com.example.javaweb.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.javaweb.config.SearchProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 博查 Web Search API 客户端。
 * 只接受搜索词，不接受任意 URL，避免 SSRF；结果写入 Redis 短缓存以降低调用成本。
 */
@Service
public class WebSearchClient {
    private static final Logger log = LoggerFactory.getLogger(WebSearchClient.class);
    private static final String CACHE_PREFIX = "search:bocha:";

    @Resource
    private SearchProperties props;

    @Resource
    private StringRedisTemplate redis;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(props.getTimeoutMs(), 1000)))
                .build();
    }

    public boolean isConfigured() {
        return props.isEnabled()
                && props.getApiKey() != null && !props.getApiKey().isBlank()
                && props.getEndpoint() != null && !props.getEndpoint().isBlank();
    }

    public SearchResult search(String query, String freshness, int count) {
        if (!isConfigured()) {
            throw new SearchException("SEARCH_NOT_CONFIGURED", "联网搜索未配置");
        }

        int actualCount = Math.max(1, Math.min(count, props.getCount()));
        String actualFreshness = normalizeFreshness(freshness);
        String cacheKey = CACHE_PREFIX + DigestUtil.sha256Hex(
                query + "|" + actualFreshness + "|" + actualCount);

        JSONObject cached = readCache(cacheKey);
        if (cached != null) {
            return parseResponse(query, cached);
        }

        JSONObject body = new JSONObject();
        body.set("query", query);
        body.set("freshness", actualFreshness);
        body.set("summary", true);
        body.set("count", actualCount);

        HttpRequest request = HttpRequest.newBuilder(URI.create(props.getEndpoint()))
                .timeout(Duration.ofMillis(Math.max(props.getTimeoutMs(), 1000)))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("bocha search http failed, status={}", response.statusCode());
                throw new SearchException("SEARCH_HTTP_" + response.statusCode(), "搜索服务暂时不可用");
            }

            JSONObject root = JSONUtil.parseObj(response.body());
            Integer code = root.getInt("code");
            if (code != null && code != 200) {
                String message = root.getStr("msg");
                throw new SearchException("SEARCH_FAILED",
                        message == null || message.isBlank() ? "搜索失败，请稍后再试" : message);
            }
            JSONObject data = root.getJSONObject("data");
            if (data == null) {
                throw new SearchException("SEARCH_EMPTY", "搜索没有返回结果");
            }

            writeCache(cacheKey, data);
            return parseResponse(query, data);
        } catch (HttpTimeoutException e) {
            throw new SearchException("SEARCH_TIMEOUT", "搜索超时，请稍后再试");
        } catch (IOException e) {
            throw new SearchException("SEARCH_IO_ERROR", "搜索服务连接失败，请稍后再试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchException("SEARCH_INTERRUPTED", "搜索已中断", e);
        }
    }

    private SearchResult parseResponse(String query, JSONObject data) {
        JSONObject webPages = data.getJSONObject("webPages");
        JSONArray values = webPages == null ? null : webPages.getJSONArray("value");
        if (values == null) {
            values = data.getJSONArray("value");
        }

        List<SearchItem> items = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                JSONObject item = values.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String url = firstNonBlank(item.getStr("url"), item.getStr("link"));
                if (url == null) {
                    continue;
                }
                String title = firstNonBlank(item.getStr("name"), item.getStr("title"), url);
                String snippet = firstNonBlank(item.getStr("summary"), item.getStr("snippet"), "");
                items.add(new SearchItem(
                        title,
                        url,
                        cleanText(snippet),
                        item.getStr("siteName"),
                        firstNonBlank(item.getStr("datePublished"), item.getStr("publishedAt"))));
            }
        }
        return new SearchResult(query, items);
    }

    private JSONObject readCache(String key) {
        try {
            String cached = redis.opsForValue().get(key);
            return cached == null || cached.isBlank() ? null : JSONUtil.parseObj(cached);
        } catch (Exception e) {
            log.warn("read search cache failed, key={}", key, e);
            return null;
        }
    }

    private void writeCache(String key, JSONObject data) {
        int ttl = props.getCacheTtlSeconds();
        if (ttl <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(key, data.toString(), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("write search cache failed, key={}", key, e);
        }
    }

    private static String normalizeFreshness(String value) {
        if (value == null || value.isBlank()) {
            return "noLimit";
        }
        String text = value.trim();
        if (text.matches("\\d{4}-\\d{2}-\\d{2}(\\.\\.\\d{4}-\\d{2}-\\d{2})?")) {
            return text;
        }
        return switch (text.toLowerCase()) {
            case "day", "oneday" -> "oneDay";
            case "week", "oneweek" -> "oneWeek";
            case "month", "onemonth" -> "oneMonth";
            case "year", "oneyear" -> "oneYear";
            default -> "noLimit";
        };
    }

    private static String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HtmlUtil.unescape(HtmlUtil.cleanHtmlTag(value)).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record SearchResult(String query, List<SearchItem> items) {
    }

    public record SearchItem(String title, String url, String snippet,
                             String siteName, String publishedAt) {
    }

    public static class SearchException extends RuntimeException {
        private final String code;

        public SearchException(String code, String message) {
            super(message);
            this.code = code;
        }

        public SearchException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
