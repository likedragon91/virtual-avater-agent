package com.example.javaweb.agent.performance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceTagParserTest {

    private static final class Collector implements PerformanceTagParser.Sink {
        private final StringBuilder text = new StringBuilder();
        private final List<PerformanceAct> acts = new ArrayList<>();

        @Override
        public void text(String chunk) {
            text.append(chunk);
        }

        @Override
        public void act(PerformanceAct act) {
            acts.add(act);
        }

        String text() {
            return text.toString();
        }
    }

    private static Collector parse(String... deltas) {
        Collector collector = new Collector();
        PerformanceTagParser parser = new PerformanceTagParser();
        for (String delta : deltas) {
            parser.accept(delta, collector);
        }
        parser.finish(collector);
        return collector;
    }

    @Test
    void plainTextPassesThrough() {
        Collector result = parse("你好，今天怎么样？");
        assertThat(result.text()).isEqualTo("你好，今天怎么样？");
        assertThat(result.acts).isEmpty();
    }

    @Test
    void intactTagProducesAct() {
        Collector result = parse("<act emotion=\"joy\" motion=\"nod\" intensity=\"0.8\"/>你好。");
        assertThat(result.text()).isEqualTo("你好。");
        assertThat(result.acts).hasSize(1);
        assertThat(result.acts.get(0).emotion()).isEqualTo("joy");
        assertThat(result.acts.get(0).motion()).isEqualTo("nod");
        assertThat(result.acts.get(0).intensity()).isEqualTo(0.8);
    }

    @Test
    void tagSplitAtEveryPositionParsesOnce() {
        String input = "前<act emotion=\"joy\" motion=\"nod\"/>后";
        for (int i = 1; i < input.length(); i++) {
            Collector result = parse(input.substring(0, i), input.substring(i));
            assertThat(result.text())
                    .as("split at %s", i)
                    .isEqualTo("前后");
            assertThat(result.acts)
                    .as("split at %s", i)
                    .hasSize(1);
            assertThat(result.acts.get(0).emotion()).isEqualTo("joy");
        }
    }

    @Test
    void multipleTagsInOneDelta() {
        Collector result = parse("<act emotion=\"joy\"/>哈哈。<act emotion=\"sadness\"/>唉。");
        assertThat(result.text()).isEqualTo("哈哈。唉。");
        assertThat(result.acts).hasSize(2);
        assertThat(result.acts.get(0).emotion()).isEqualTo("joy");
        assertThat(result.acts.get(1).emotion()).isEqualTo("sadness");
    }

    @Test
    void codeFencePreservesTagAsText() {
        Collector result = parse("```\n<act emotion=\"joy\"/>\n```");
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).contains("<act emotion=\"joy\"/>");
    }

    @Test
    void inlineCodePreservesTagAsText() {
        Collector result = parse("示例 `<act emotion=\"joy\"/>` 结束");
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).contains("<act emotion=\"joy\"/>");
    }

    @Test
    void malformedTagDroppedAtFinish() {
        Collector result = parse("<act emotion=\"joy\"");
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void nonControlTagsPassThrough() {
        Collector result = parse("<b>加粗</b> 和 1<3");
        assertThat(result.text()).isEqualTo("<b>加粗</b> 和 1<3");
        assertThat(result.acts).isEmpty();
    }

    @Test
    void activityTagIsNotParsed() {
        Collector result = parse("<activity>内容</activity>");
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).isEqualTo("<activity>内容</activity>");
    }

    @Test
    void uppercaseAndSingleQuotesAccepted() {
        Collector result = parse("<ACT EMOTION='JOY' MOTION='WAVE'/>");
        assertThat(result.acts).hasSize(1);
        assertThat(result.acts.get(0).emotion()).isEqualTo("joy");
        assertThat(result.acts.get(0).motion()).isNull();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void invalidValuesDropped() {
        Collector result = parse("<act emotion=\"furious\" motion=\"backflip\"/>文本");
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).isEqualTo("文本");
    }

    @Test
    void overlongTagDoesNotLeakAttributes() {
        StringBuilder body = new StringBuilder("<act emotion=\"joy\" ");
        while (body.length() < 300) {
            body.append("x=\"y\" ");
        }
        body.append("/>后文");
        Collector result = parse(body.toString());
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).doesNotContain("<act");
        assertThat(result.text()).doesNotContain("x=\"y\"");
        assertThat(result.text()).contains("后文");
    }

    @Test
    void delayIsRecognizedButIgnored() {
        Collector result = parse("<delay seconds=\"0.8\"/>稍等。");
        assertThat(result.acts).isEmpty();
        assertThat(result.text()).isEqualTo("稍等。");
    }
}
