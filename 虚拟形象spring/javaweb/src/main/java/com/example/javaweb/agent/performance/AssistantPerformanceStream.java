package com.example.javaweb.agent.performance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 助手流式输出的表演层：清洗标签、按句切分、把 act/speech 与正文 delta 对齐。
 *
 * 句子索引从 0 开始；act 只会在对应句子的 speech 事件之前下发。
 * 调用方负责把 EventSink 映射到 SSE，以及持久化 cleanedAnswer / lastEmotion。
 */
public final class AssistantPerformanceStream {

    public interface EventSink {
        void delta(String text) throws IOException;

        void act(Map<String, Object> payload) throws IOException;

        void speech(Map<String, Object> payload) throws IOException;
    }

    private final EventSink sink;
    private final PerformanceTagParser parser = new PerformanceTagParser();
    private final SpeechChunker chunker;
    private final StringBuilder answer = new StringBuilder();
    private final List<PerformanceAct> pendingActs = new ArrayList<>();

    private int sentenceIndex = 0;
    private int speechCount = 0;
    private String lastEmotion;
    private boolean finished = false;

    private final PerformanceTagParser.Sink parserSink = new PerformanceTagParser.Sink() {
        @Override
        public void text(String text) {
            try {
                answer.append(text);
                sink.delta(text);
                chunker.accept(text, sentenceSink);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void act(PerformanceAct act) {
            try {
                acceptAct(act);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    private final SpeechChunker.Sink sentenceSink = this::emitSentence;

    public AssistantPerformanceStream(EventSink sink, int maxSpeechChars, int minSpeechChars) {
        this.sink = sink;
        this.chunker = new SpeechChunker(maxSpeechChars, minSpeechChars);
    }

    public void accept(String delta) throws IOException {
        if (delta == null || delta.isEmpty() || finished) {
            return;
        }
        try {
            parser.accept(delta, parserSink);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * 工具调用等分段边界：先读完当前句，避免调用前后的文本粘成一句。
     */
    public void flushSegment() throws IOException {
        if (finished) {
            return;
        }
        try {
            chunker.flushSegment(sentenceSink);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * 流结束：flush 剩余文本，并把悬空的 act 挂到最后一句。
     */
    public void finish() throws IOException {
        if (finished) {
            return;
        }
        finished = true;
        try {
            parser.finish(parserSink);
            chunker.finish(sentenceSink);
            if (!pendingActs.isEmpty()) {
                int index = Math.max(0, sentenceIndex - 1);
                for (PerformanceAct act : pendingActs) {
                    sink.act(act.toEventPayload(index));
                }
                pendingActs.clear();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private void acceptAct(PerformanceAct act) throws IOException {
        if (act == null || act.isEmpty()) {
            return;
        }
        if (act.emotion() != null) {
            lastEmotion = act.emotion();
        }
        pendingActs.add(act);
    }

    private void emitSentence(String text) {
        try {
            int index = sentenceIndex;
            for (PerformanceAct act : pendingActs) {
                sink.act(act.toEventPayload(index));
            }
            pendingActs.clear();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("index", index);
            payload.put("text", text);
            if (lastEmotion != null) {
                payload.put("emotion", lastEmotion);
            }
            sink.speech(payload);

            sentenceIndex++;
            speechCount++;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String cleanedAnswer() {
        return answer.toString();
    }

    /**
     * 供 chat_message.emotion 持久化的最小 JSON；未出现情绪时为 null。
     */
    public String lastEmotionJson() {
        return lastEmotion == null ? null : "{\"emotion\":\"" + lastEmotion + "\"}";
    }

    public String lastEmotion() {
        return lastEmotion;
    }

    public int speechCount() {
        return speechCount;
    }

    public int sentenceIndex() {
        return sentenceIndex;
    }
}
