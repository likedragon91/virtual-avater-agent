package com.example.javaweb.agent.style;

import com.example.javaweb.agent.style.ResponseStylePlanner.PromptPlan;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.enums.ResponseMode;
import com.example.javaweb.persona.PersonaCatalog.Traits;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptComposerTest {

    private final SystemPromptComposer composer = new SystemPromptComposer();

    private static PromptPlan plan(ResponseMode mode) {
        return new PromptPlan(mode, false);
    }

    private static SystemPromptComposer.PromptInput input(PromptPlan plan,
                                                          boolean performance,
                                                          boolean webSearch,
                                                          List<MemoryRow> memories,
                                                          double verbosity) {
        return new SystemPromptComposer.PromptInput(
                "2026-11-18 10:00:00",
                "小明",
                "S1",
                120,
                "标准",
                "人格：默认\n风格要求：自然温和\n",
                new Traits(0.5, 0.3, 0.5, 0.5, verbosity),
                plan,
                webSearch,
                performance,
                memories,
                null);
    }

    private static MemoryRow memory(String content) {
        MemoryRow row = new MemoryRow();
        row.setMemoryType("PREFERENCE");
        row.setContent(content);
        return row;
    }

    @Test
    void modeBlockAppearsBeforeRulesAndRulesBeforePerformance() {
        String prompt = composer.compose(input(plan(ResponseMode.CASUAL), true, false, List.of(), 0.5));

        int mode = prompt.indexOf("回复风格（本轮）");
        int rules = prompt.indexOf("对话规则");
        int performance = prompt.indexOf("表演标记");

        assertThat(mode).isGreaterThan(-1);
        assertThat(rules).isGreaterThan(mode);
        assertThat(performance).isGreaterThan(rules);
    }

    @Test
    void memoriesComeLast() {
        String prompt = composer.compose(input(plan(ResponseMode.CASUAL), true, false,
                List.of(memory("喜欢喝冰美式")), 0.5));

        assertThat(prompt.indexOf("记忆参考")).isGreaterThan(prompt.indexOf("表演标记"));
        assertThat(prompt).contains("喜欢喝冰美式");
    }

    @Test
    void globalLengthPraiseIsRemoved() {
        String prompt = composer.compose(input(plan(ResponseMode.CASUAL), true, false, List.of(), 0.5));
        assertThat(prompt).doesNotContain("回答简洁");
        assertThat(prompt).contains("回复风格（本轮）");
    }

    @Test
    void precedenceLineIsPresent() {
        String prompt = composer.compose(input(plan(ResponseMode.DISCUSSION), true, false, List.of(), 0.5));
        assertThat(prompt).contains("安全与危机应对 > 关系阶段边界 > 回复长度与格式");
    }

    @Test
    void crisisTurnDropsTheLengthBand() {
        String prompt = composer.compose(input(new PromptPlan(ResponseMode.CASUAL, true),
                true, false, List.of(), 0.5));

        assertThat(prompt).contains("用户可能处于危机中");
        assertThat(prompt).doesNotContain("场景：闲聊");
        assertThat(prompt).doesNotContain("长度：1-2 句");
    }

    @Test
    void knowledgeModeAvoidsHeadingsAndWideTables() {
        String prompt = composer.compose(input(plan(ResponseMode.KNOWLEDGE), true, false, List.of(), 0.5));
        assertThat(prompt).contains("不要使用标题和宽表格");
    }

    @Test
    void performanceTagsAreExemptFromFormatRules() {
        String prompt = composer.compose(input(plan(ResponseMode.CASUAL), true, false, List.of(), 0.5));
        assertThat(prompt).contains("表演标记不受回复格式规则限制");
    }

    @Test
    void performanceBlockIsOmittedWhenDisabled() {
        String prompt = composer.compose(input(plan(ResponseMode.CASUAL), false, false, List.of(), 0.5));
        assertThat(prompt).doesNotContain("<act ");
        assertThat(prompt).doesNotContain("表演标记");
    }

    @Test
    void webSearchFlagControlsThePrompt() {
        String withSearch = composer.compose(input(plan(ResponseMode.CASUAL), false, true, List.of(), 0.5));
        String withoutSearch = composer.compose(input(plan(ResponseMode.CASUAL), false, false, List.of(), 0.5));

        assertThat(withSearch).contains("调用 search_web");
        assertThat(withoutSearch).contains("当前未启用联网搜索");
    }

    @Test
    void sessionSummaryIsInjectedBeforeMemories() {
        SystemPromptComposer.PromptInput withSummary = new SystemPromptComposer.PromptInput(
                "2026-11-18 10:00:00",
                "小明",
                "S1",
                120,
                "标准",
                "人格：默认\n",
                new Traits(0.5, 0.3, 0.5, 0.5, 0.5),
                plan(ResponseMode.CASUAL),
                false,
                false,
                List.of(memory("喜欢喝冰美式")),
                "用户最近在准备面试。");

        String prompt = composer.compose(withSummary);

        assertThat(prompt).contains("会话摘要（更早的对话");
        assertThat(prompt).contains("用户最近在准备面试。");
        assertThat(prompt.indexOf("会话摘要")).isLessThan(prompt.indexOf("记忆参考"));
    }

    @Test
    void negativeWordsGetANormalResponseWithoutAmplifying() {
        String casual = composer.compose(input(plan(ResponseMode.CASUAL), false, false, List.of(), 0.5));
        String emotional = composer.compose(input(plan(ResponseMode.EMOTIONAL), false, false, List.of(), 0.5));

        assertThat(casual).contains("按字面正常回应");
        assertThat(casual).contains("不要把话题往消极方向引");
        assertThat(emotional).contains("先接住情绪但不放大");
    }

    @Test
    void verbosityShiftsPositionWithinTheBand() {
        String quiet = composer.compose(input(plan(ResponseMode.CASUAL), false, false, List.of(), 0.2));
        String chatty = composer.compose(input(plan(ResponseMode.CASUAL), false, false, List.of(), 0.8));

        assertThat(quiet).contains("取区间下沿");
        assertThat(chatty).contains("取区间上沿");
    }
}
