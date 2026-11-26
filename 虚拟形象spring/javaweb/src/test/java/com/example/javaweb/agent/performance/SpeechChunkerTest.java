package com.example.javaweb.agent.performance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechChunkerTest {

    private static final class Collector implements SpeechChunker.Sink {
        private final List<String> sentences = new ArrayList<>();

        @Override
        public void sentence(String text) {
            sentences.add(text);
        }
    }

    private static List<String> chunk(String input) {
        return chunk(input, 120, 12);
    }

    private static List<String> chunk(String input, int maxChars, int minChars) {
        Collector collector = new Collector();
        SpeechChunker chunker = new SpeechChunker(maxChars, minChars);
        chunker.accept(input, collector);
        chunker.finish(collector);
        return collector.sentences;
    }

    @Test
    void chinesePunctuationSplits() {
        assertThat(chunk("今天也辛苦了。明天见！"))
                .containsExactly("今天也辛苦了。", "明天见！");
    }

    @Test
    void englishSentenceSplits() {
        assertThat(chunk("Hello world. How are you?"))
                .containsExactly("Hello world.", "How are you?");
    }

    @Test
    void decimalsAndVersionsNotSplit() {
        assertThat(chunk("3.14 是版本 v1.2 的示例。"))
                .containsExactly("3.14 是版本 v1.2 的示例。");
    }

    @Test
    void abbreviationNotSplit() {
        assertThat(chunk("e.g. this works."))
                .containsExactly("e.g. this works.");
    }

    @Test
    void codeFenceExcludedFromSpeech() {
        List<String> sentences = chunk("```\nconst a = 1;\n```\n你好。");
        assertThat(sentences).containsExactly("你好。");
    }

    @Test
    void inlineCodeExcludedFromSpeech() {
        List<String> sentences = chunk("这是 `code` 示例。");
        assertThat(sentences).containsExactly("这是 示例。");
    }

    @Test
    void linkLabelKeptAndUrlRemoved() {
        assertThat(chunk("看[文档](https://example.com)吧。"))
                .containsExactly("看文档吧。");
    }

    @Test
    void urlRemoved() {
        assertThat(chunk("访问 https://example.com/path 获取。"))
                .containsExactly("访问 获取。");
    }

    @Test
    void imageRemoved() {
        assertThat(chunk("![图](a.png)看这里。"))
                .containsExactly("看这里。");
    }

    @Test
    void markdownMarkersStripped() {
        assertThat(chunk("## 标题\n- 第一条\n- 第二条"))
                .containsExactly("标题", "第一条", "第二条");
    }

    @Test
    void overlongTextSplitsAtComma() {
        List<String> sentences = chunk(
                "一二三四五六七八九十，十一十二，十三十四十五十六，十七十八十九二十。",
                24, 5);
        assertThat(sentences).hasSizeGreaterThanOrEqualTo(2);
        assertThat(String.join("", sentences)).contains("，");
    }

    @Test
    void finishFlushesWithoutPunctuation() {
        assertThat(chunk("没有句号")).containsExactly("没有句号");
    }

    @Test
    void punctuationOnlySkipped() {
        assertThat(chunk("。！？……")).isEmpty();
    }
}
