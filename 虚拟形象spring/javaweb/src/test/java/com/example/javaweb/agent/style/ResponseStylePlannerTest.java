package com.example.javaweb.agent.style;

import com.example.javaweb.agent.style.ResponseStylePlanner.PromptPlan;
import com.example.javaweb.enums.ResponseMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseStylePlannerTest {

    private final ResponseStylePlanner planner = new ResponseStylePlanner();

    @Test
    void blankMessageFallsBackToCasual() {
        assertThat(planner.plan(null).mode()).isEqualTo(ResponseMode.CASUAL);
        assertThat(planner.plan("   ").mode()).isEqualTo(ResponseMode.CASUAL);
    }

    @Test
    void plainChatIsCasual() {
        assertThat(planner.plan("今天天气不错").mode()).isEqualTo(ResponseMode.CASUAL);
    }

    @Test
    void emotionalWordsSelectEmotional() {
        assertThat(planner.plan("今天真的好累").mode()).isEqualTo(ResponseMode.EMOTIONAL);
        assertThat(planner.plan("刚被领导骂了，有点委屈").mode()).isEqualTo(ResponseMode.EMOTIONAL);
    }

    @Test
    void concreteRequestSelectsTask() {
        assertThat(planner.plan("帮我写一个登录接口").mode()).isEqualTo(ResponseMode.TASK);
        assertThat(planner.plan("这段代码为什么报错，帮我排查").mode()).isEqualTo(ResponseMode.TASK);
    }

    @Test
    void knowledgeQuestionSelectsKnowledge() {
        assertThat(planner.plan("JWT 为什么要分 access 和 refresh").mode())
                .isEqualTo(ResponseMode.KNOWLEDGE);
    }

    @Test
    void discussionMarkerWinsOverQuestionMark() {
        assertThat(planner.plan("周末去爬山好不好").mode()).isEqualTo(ResponseMode.DISCUSSION);
    }

    @Test
    void fullWidthLatinIsNormalized() {
        // NFKC 把全角 ＡＰＩ 转成 api，命中任务关键词
        assertThat(planner.plan("这个 ＡＰＩ 怎么用").mode()).isEqualTo(ResponseMode.TASK);
    }

    @Test
    void urlAndCodeDoNotTriggerTaskMode() {
        assertThat(planner.plan("看这个 https://example.com/bug 怎么用").mode())
                .isEqualTo(ResponseMode.KNOWLEDGE);
        assertThat(planner.plan("`code()` 是什么意思").mode())
                .isEqualTo(ResponseMode.KNOWLEDGE);
    }

    @Test
    void englishMarkerRespectsWordBoundary() {
        // "rapid" 里包含 "api"，但不应该命中任务模式
        assertThat(planner.plan("这个 rapid 工具还不错").mode()).isEqualTo(ResponseMode.CASUAL);
    }

    @Test
    void crisisIsDetectedEvenWithExplicitMode() {
        PromptPlan plan = planner.plan("我不想活了", ResponseMode.CASUAL);
        assertThat(plan.crisis()).isTrue();
        assertThat(plan.mode()).isEqualTo(ResponseMode.CASUAL);
    }

    @Test
    void explicitModeOverridesClassification() {
        PromptPlan plan = planner.plan("今天真的好累", ResponseMode.TASK);
        assertThat(plan.crisis()).isFalse();
        assertThat(plan.mode()).isEqualTo(ResponseMode.TASK);
    }

    @Test
    void verbosityMapsToBandPosition() {
        assertThat(ResponseStylePlanner.positionInstruction(0.2)).contains("下沿");
        assertThat(ResponseStylePlanner.positionInstruction(0.5)).contains("中间");
        assertThat(ResponseStylePlanner.positionInstruction(0.8)).contains("上沿");
    }
}
