package com.example.javaweb.agent.performance;

import java.util.Set;

/**
 * 按句切分「可朗读文本」。
 *
 * 只影响 TTS，不影响聊天显示：代码块、行内 code、URL、图片地址与 Markdown 标记会被剔除。
 * 句子边界以中英文强标点和换行为主，ASCII 句点只在非小数/版本号/常见缩写时分句。
 */
public final class SpeechChunker {

    public interface Sink {
        void sentence(String text);
    }

    private static final Set<Character> STRONG_BOUNDARIES =
            Set.of('。', '！', '？', '!', '?', '；', ';', '…');
    private static final Set<String> ABBREVIATIONS =
            Set.of("e.g", "i.e", "mr", "mrs", "ms", "dr", "prof", "vs", "etc", "no");

    private final int maxChars;
    private final int minChars;
    private final StringBuilder buffer = new StringBuilder();

    private boolean inFence = false;
    private boolean inInlineCode = false;
    private boolean atLineStart = true;
    private char fenceChar = 0;
    private int fenceRun = 0;
    private boolean pendingDot = false;
    /** '!' 可能是句末，也可能是 Markdown 图片 ![alt](url) 的开头，等下一个字符再决定 */
    private boolean pendingBang = false;

    public SpeechChunker() {
        this(120, 12);
    }

    public SpeechChunker(int maxChars, int minChars) {
        this.maxChars = Math.max(maxChars, 20);
        this.minChars = Math.max(0, Math.min(minChars, this.maxChars - 1));
    }

    public void accept(String cleanText, Sink sink) {
        if (cleanText == null || cleanText.isEmpty()) {
            return;
        }
        for (int i = 0; i < cleanText.length(); i++) {
            char c = cleanText.charAt(i);

            boolean wasInFence = inFence;
            boolean fenceMarker = trackFence(c);
            if (fenceMarker && !wasInFence && inFence) {
                // 代码块开始：先把前面的正文读掉
                flush(sink);
            }
            if (inFence) {
                if (c == '\n') {
                    atLineStart = true;
                    fenceRun = 0;
                }
                continue;
            }

            boolean wasInline = inInlineCode;
            boolean inlineMarker = !fenceMarker && trackInlineCode(c);
            if (inlineMarker) {
                if (wasInline && !inInlineCode) {
                    buffer.append(' ');
                }
                continue;
            }
            if (inInlineCode) {
                continue;
            }
            if (fenceMarker) {
                // 关闭围栏的三个标记本身不朗读
                continue;
            }

            if (pendingBang) {
                pendingBang = false;
                if (c == '[') {
                    // Markdown 图片，保留 '!' 交给 flush 时的清洗逻辑
                    buffer.append('!');
                } else {
                    buffer.append('!');
                    flush(sink);
                }
            }

            if (pendingDot) {
                pendingDot = false;
                buffer.append('.');
                if (Character.isWhitespace(c)) {
                    if (!isAbbreviationTail()) {
                        flush(sink);
                    }
                } else if (!isAsciiLetterOrDigit(c)) {
                    flush(sink);
                }
            }

            if (c == '\n') {
                flush(sink);
                atLineStart = true;
                fenceRun = 0;
                continue;
            }

            if (c == '.') {
                pendingDot = true;
                continue;
            }

            if (c == '!') {
                pendingBang = true;
                continue;
            }

            buffer.append(c);
            if (atLineStart && !Character.isWhitespace(c)) {
                atLineStart = false;
            }

            if (STRONG_BOUNDARIES.contains(c)) {
                flush(sink);
            } else if (buffer.length() >= maxChars) {
                splitLong(sink);
            }
        }
    }

    /**
     * 工具调用等分段边界：把当前缓冲读成一句，但不结束整个流。
     */
    public void flushSegment(Sink sink) {
        flush(sink);
    }

    public void finish(Sink sink) {
        if (pendingDot) {
            buffer.append('.');
            pendingDot = false;
        }
        if (pendingBang) {
            buffer.append('!');
            pendingBang = false;
        }
        flush(sink);
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

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isAbbreviationTail() {
        int end = buffer.length();
        int start = end;
        while (start > 0 && !Character.isWhitespace(buffer.charAt(start - 1))) {
            start--;
        }
        String tail = buffer.substring(start, end).toLowerCase();
        while (tail.endsWith(".")) {
            tail = tail.substring(0, tail.length() - 1);
        }
        return ABBREVIATIONS.contains(tail);
    }

    private void splitLong(Sink sink) {
        int limit = Math.min(buffer.length(), maxChars);
        int split = -1;
        for (int i = limit - 1; i >= minChars; i--) {
            char c = buffer.charAt(i);
            if (c == '，' || c == ',' || c == '、' || Character.isWhitespace(c)) {
                split = i + 1;
                break;
            }
        }
        if (split <= 0) {
            split = limit;
        }
        String head = buffer.substring(0, split);
        buffer.delete(0, split);
        emit(head, sink);
    }

    private void flush(Sink sink) {
        if (pendingDot) {
            buffer.append('.');
            pendingDot = false;
        }
        if (pendingBang) {
            buffer.append('!');
            pendingBang = false;
        }
        if (buffer.length() == 0) {
            return;
        }
        String raw = buffer.toString();
        buffer.setLength(0);
        emit(raw, sink);
    }

    private void emit(String raw, Sink sink) {
        String spoken = toSpokenText(raw);
        if (!spoken.isEmpty() && hasSpeakable(spoken)) {
            sink.sentence(spoken);
        }
    }

    static String toSpokenText(String raw) {
        String text = raw == null ? "" : raw;
        text = text.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        text = text.replaceAll("https?://\\S+", " ");
        text = text.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", " ");
        text = text.replaceAll("(?m)^\\s{0,3}[-*+]\\s+", " ");
        text = text.replaceAll("(?m)^\\s{0,3}\\d+[.)]\\s+", " ");
        text = text.replaceAll("(?m)^\\s*[-:|\\s]{3,}$", " ");
        text = text.replaceAll("[*_~`>|#]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private static boolean hasSpeakable(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
