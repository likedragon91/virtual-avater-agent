package com.example.javaweb.agent.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPerformanceStreamTest {

    private static final class Events implements AssistantPerformanceStream.EventSink {
        private final List<String> events = new ArrayList<>();

        @Override
        public void delta(String text) {
            events.add("delta:" + text);
        }

        @Override
        public void act(Map<String, Object> payload) {
            events.add("act:" + payload.get("emotion") + ":" + payload.get("motion")
                    + ":" + payload.get("sentenceIndex"));
        }

        @Override
        public void speech(Map<String, Object> payload) {
            events.add("speech:" + payload.get("index") + ":" + payload.get("text"));
        }
    }

    private static AssistantPerformanceStream stream(Events events) {
        return new AssistantPerformanceStream(events, 120, 12);
    }

    private static List<String> actsAndSpeech(Events events) {
        return events.events.stream()
                .filter(event -> !event.startsWith("delta:"))
                .toList();
    }

    @Test
    void actIsEmittedBeforeItsSentence() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("<act emotion=\"joy\" motion=\"nod\"/>你好。");
        stream.finish();

        assertThat(actsAndSpeech(events)).containsExactly(
                "act:joy:nod:0",
                "speech:0:你好。");
    }

    @Test
    void controlTagsNeverLeakIntoDeltaOrAnswer() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("前<act emotion=\"joy\"/>后。");
        stream.finish();

        assertThat(stream.cleanedAnswer()).isEqualTo("前后。");
        assertThat(events.events)
                .filteredOn(event -> event.startsWith("delta:"))
                .allSatisfy(event -> assertThat(event).doesNotContain("<act"));
    }

    @Test
    void lastEmotionIsPersistedAsJson() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("<act emotion=\"sadness\"/>唉。");
        stream.finish();

        assertThat(stream.lastEmotion()).isEqualTo("sadness");
        assertThat(stream.lastEmotionJson()).isEqualTo("{\"emotion\":\"sadness\"}");
    }

    @Test
    void toolBoundaryFlushesCurrentSentence() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("第一句");
        stream.flushSegment();
        stream.accept("第二句。");
        stream.finish();

        assertThat(actsAndSpeech(events)).containsExactly(
                "speech:0:第一句",
                "speech:1:第二句。");
    }

    @Test
    void trailingActAttachesToLastSentence() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("你好。");
        stream.accept("<act motion=\"nod\"/>");
        stream.finish();

        assertThat(actsAndSpeech(events)).containsExactly(
                "speech:0:你好。",
                "act:null:nod:0");
    }

    @Test
    void tagSplitAcrossDeltasStillAligns() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("前<act emotion=\"jo");
        stream.accept("y\"/>后。");
        stream.finish();

        assertThat(actsAndSpeech(events)).containsExactly(
                "act:joy:null:0",
                "speech:0:前后。");
    }

    @Test
    void actWithoutTextStillEmittedOnFinish() throws IOException {
        Events events = new Events();
        AssistantPerformanceStream stream = stream(events);
        stream.accept("<act emotion=\"surprise\"/>");
        stream.finish();

        assertThat(actsAndSpeech(events)).containsExactly("act:surprise:null:0");
    }
}
