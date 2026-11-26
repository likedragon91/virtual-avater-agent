package com.example.javaweb.agent.performance;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式表演标签解析器。
 *
 * 只识别 &lt;act .../&gt; 与 &lt;delay .../&gt; 两种标签；其余 &lt; 原样作为正文输出。
 * 标签可以跨任意 delta 切分；不完整标签在 finish() 时丢弃，避免泄漏到正文。
 * 代码围栏（``` / ~~~）与行内 code 内不解析标签。
 */
public final class PerformanceTagParser {

    public interface Sink {
        void text(String text);

        void act(PerformanceAct act);
    }

    private static final int MAX_TAG_LENGTH = 256;
    private static final String ACT = "act";
    private static final String DELAY = "delay";
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private enum State {
        TEXT, TAG_NAME, TAG_BODY
    }

    private State state = State.TEXT;
    private final StringBuilder out = new StringBuilder();
    private final StringBuilder candidate = new StringBuilder();
    private final StringBuilder tagBody = new StringBuilder();
    private String tagName = "";

    // 代码状态
    private boolean inFence = false;
    private boolean inInlineCode = false;
    private boolean atLineStart = true;
    private char fenceChar = 0;
    private int fenceRun = 0;
    /** 超长畸形标签：丢弃直到下一个 '>'，避免属性尾巴泄漏到正文 */
    private boolean suppressUntilGt = false;

    public void accept(String delta, Sink sink) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        for (int i = 0; i < delta.length(); i++) {
            char c = delta.charAt(i);
            boolean fenceMarker = trackFence(c);
            boolean inlineMarker = !fenceMarker && trackInlineCode(c);

            if (inFence || inInlineCode || fenceMarker || inlineMarker) {
                resetTagState();
                out.append(c);
            } else {
                consumeTagChar(c, sink);
            }

            if (c == '\n') {
                atLineStart = true;
                fenceRun = 0;
            }
        }
        flushText(sink);
    }

    /**
     * 结束本次流。未闭合的控制标签直接丢弃。
     */
    public void finish(Sink sink) {
        resetTagState();
        flushText(sink);
    }

    private boolean trackFence(char c) {
        if (!atLineStart) {
            return false;
        }
        if (c == '`' || c == '~') {
            if (fenceRun == 0) {
                fenceChar = c;
            }
            if (c == fenceChar) {
                fenceRun++;
                if (fenceRun >= 3) {
                    inFence = !inFence;
                    fenceRun = 0;
                    atLineStart = false;
                }
                return true;
            }
            fenceRun = 0;
            atLineStart = false;
            return false;
        }
        if (!Character.isWhitespace(c)) {
            atLineStart = false;
            fenceRun = 0;
        }
        return false;
    }

    private boolean trackInlineCode(char c) {
        if (inFence || c != '`' || atLineStart) {
            return false;
        }
        inInlineCode = !inInlineCode;
        return true;
    }

    private void consumeTagChar(char c, Sink sink) {
        if (suppressUntilGt) {
            if (c == '>') {
                suppressUntilGt = false;
            }
            return;
        }
        switch (state) {
            case TEXT -> {
                if (c == '<') {
                    candidate.setLength(0);
                    candidate.append(c);
                    state = State.TAG_NAME;
                } else {
                    out.append(c);
                }
            }
            case TAG_NAME -> consumeTagName(c, sink);
            case TAG_BODY -> consumeTagBody(c, sink);
        }
    }

    private void consumeTagName(char c, Sink sink) {
        if (c == '\n') {
            // 标签名不跨行，按普通文本输出
            out.append(candidate);
            candidate.setLength(0);
            state = State.TEXT;
            out.append(c);
            return;
        }
        candidate.append(c);
        String body = candidate.substring(1).toLowerCase(Locale.ROOT);

        if (ACT.startsWith(body)) {
            if (body.length() == ACT.length()) {
                enterTagBody(ACT);
            }
            return;
        }
        if (DELAY.startsWith(body)) {
            if (body.length() == DELAY.length()) {
                enterTagBody(DELAY);
            }
            return;
        }

        // 不是控制标签，原样吐出（例如 <b>、<3）
        out.append(candidate);
        candidate.setLength(0);
        state = State.TEXT;
    }

    private void enterTagBody(String name) {
        tagName = name;
        tagBody.setLength(0);
        state = State.TAG_BODY;
    }

    private void consumeTagBody(char c, Sink sink) {
        tagBody.append(c);

        if (tagBody.length() == 1 && !isBoundaryAfterName(c)) {
            // <actx ...> / <delayx ...>：不是控制标签
            out.append('<').append(tagName).append(c);
            tagBody.setLength(0);
            state = State.TEXT;
            return;
        }

        if (tagBody.length() > MAX_TAG_LENGTH) {
            // 超长未闭合，丢弃直到 '>'，避免属性尾巴泄漏
            resetTagState();
            suppressUntilGt = true;
            return;
        }

        if (c != '>') {
            return;
        }

        String body = tagBody.substring(0, tagBody.length() - 1);
        PerformanceAct act = parseTag(tagName, body);
        tagBody.setLength(0);
        state = State.TEXT;
        tagName = "";
        if (act != null) {
            flushText(sink);
            sink.act(act);
        }
    }

    private static boolean isBoundaryAfterName(char c) {
        return Character.isWhitespace(c) || c == '/' || c == '>';
    }

    private static PerformanceAct parseTag(String name, String rawBody) {
        // delay 在 MVP 中识别但忽略
        if (!ACT.equals(name)) {
            return null;
        }

        String body = rawBody == null ? "" : rawBody.trim();
        while (body.endsWith("/")) {
            body = body.substring(0, body.length() - 1).trim();
        }

        String emotion = null;
        String motion = null;
        double intensity = 0.6;

        Matcher matcher = ATTRIBUTE_PATTERN.matcher(body);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (value == null) {
                continue;
            }
            value = value.trim();
            switch (key) {
                case "emotion" -> {
                    String normalized = value.toLowerCase(Locale.ROOT);
                    if (PerformanceAct.EMOTIONS.contains(normalized)) {
                        emotion = normalized;
                    }
                }
                case "motion" -> {
                    String normalized = value.toLowerCase(Locale.ROOT);
                    if (PerformanceAct.MOTIONS.contains(normalized)) {
                        motion = normalized;
                    }
                }
                case "intensity" -> intensity = parseIntensity(value, intensity);
                default -> {
                    // 未知属性忽略
                }
            }
        }

        if (emotion == null && motion == null) {
            return null;
        }
        return new PerformanceAct(emotion, motion, intensity);
    }

    private static double parseIntensity(String value, double fallback) {
        try {
            return PerformanceAct.clamp01(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void flushText(Sink sink) {
        if (out.length() == 0) {
            return;
        }
        String text = out.toString();
        out.setLength(0);
        sink.text(text);
    }

    private void resetTagState() {
        if (state != State.TEXT) {
            candidate.setLength(0);
            tagBody.setLength(0);
            tagName = "";
            state = State.TEXT;
        }
        suppressUntilGt = false;
    }
}
