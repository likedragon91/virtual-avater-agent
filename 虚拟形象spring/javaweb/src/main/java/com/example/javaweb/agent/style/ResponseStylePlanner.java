package com.example.javaweb.agent.style;

import com.example.javaweb.enums.ResponseMode;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 根据最新一条用户消息选择本轮回复模式。
 * 纯关键词启发式：零额外模型调用，误判只会影响长度提示，不影响事实与工具。
 */
@Component
public class ResponseStylePlanner {

    public record PromptPlan(ResponseMode mode, boolean crisis) {
    }

    /** 命中即进入危机处理：不套用长度/格式限制，也不做角色扮演式调侃 */
    private static final List<String> CRISIS_MARKERS = List.of(
            "自杀", "想死", "不想活", "活不下去", "轻生", "结束生命", "自残", "割腕", "跳楼",
            "不如死了", "伤害自己", "撑不下去", "suicide", "kill myself", "end my life", "self-harm");

    /** 需要交付物的具体请求；刻意不收录裸“帮我”，避免误判情绪求助 */
    private static final List<String> TASK_MARKERS = List.of(
            "写一个", "写个", "写一段", "帮我写", "生成", "做个", "做一份", "制定", "排查", "修复",
            "部署", "安装", "配置", "教我", "翻译", "总结一下", "整理一下", "改写", "润色",
            "怎么实现", "如何实现", "具体步骤", "代码", "报错", "脚本", "命令", "接口文档",
            "code", "bug", "debug", "api");

    private static final List<String> EMOTIONAL_MARKERS = List.of(
            "累", "难过", "伤心", "想哭", "哭了", "烦", "焦虑", "压力", "崩溃", "孤独", "寂寞",
            "委屈", "难受", "不开心", "疲惫", "失望", "痛苦", "失恋", "分手", "害怕", "紧张",
            "睡不着", "失眠", "想你了", "好开心", "太开心", "激动", "生气", "愤怒", "无助",
            "迷茫", "心累", "被骂", "不想上班");

    private static final List<String> DISCUSSION_MARKERS = List.of(
            "要不要", "好不好", "该不该", "你觉得呢", "你觉得怎么样", "你怎么看",
            "是不是应该", "我们聊聊", "聊聊吧");

    private static final List<String> KNOWLEDGE_MARKERS = List.of(
            "为什么", "怎么", "如何", "什么是", "是什么", "有什么区别", "区别", "有哪些",
            "能不能", "可不可以", "是不是", "原理", "什么意思",
            "why", "how", "what", "difference");

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern FENCED_CODE_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`]*`");

    public PromptPlan plan(String latestUserMessage) {
        return plan(latestUserMessage, null);
    }

    /**
     * @param override 客户端显式指定的模式；为空时走自动分类。危机识别始终生效。
     */
    public PromptPlan plan(String latestUserMessage, ResponseMode override) {
        String text = normalize(latestUserMessage);
        boolean crisis = containsAny(text, CRISIS_MARKERS);
        if (crisis) {
            return new PromptPlan(override != null ? override : ResponseMode.EMOTIONAL, true);
        }
        if (override != null) {
            return new PromptPlan(override, false);
        }
        return new PromptPlan(classify(text), false);
    }

    /** verbosity 只决定落在长度带的哪个位置，不改变长度带本身 */
    public static String positionInstruction(double verbosity) {
        if (verbosity <= 0.3) {
            return "话量偏少：能一句说清就用一句，取区间下沿。";
        }
        if (verbosity >= 0.7) {
            return "话量偏多：可以取区间上沿，但不要超出区间。";
        }
        return "取区间中间，自然就好。";
    }

    private static ResponseMode classify(String text) {
        if (text.isBlank()) {
            return ResponseMode.CASUAL;
        }
        if (containsAny(text, TASK_MARKERS)) {
            return ResponseMode.TASK;
        }
        if (containsAny(text, EMOTIONAL_MARKERS)) {
            return ResponseMode.EMOTIONAL;
        }
        if (containsAny(text, DISCUSSION_MARKERS)) {
            return ResponseMode.DISCUSSION;
        }
        if (containsAny(text, KNOWLEDGE_MARKERS)) {
            return ResponseMode.KNOWLEDGE;
        }
        return ResponseMode.CASUAL;
    }

    /** NFKC 统一全角标点，去掉链接与代码，避免代码里的关键词干扰分类 */
    private static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String text = Normalizer.normalize(message, Normalizer.Form.NFKC);
        text = URL_PATTERN.matcher(text).replaceAll(" ");
        text = FENCED_CODE_PATTERN.matcher(text).replaceAll(" ");
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll(" ");
        return text.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, List<String> markers) {
        for (String marker : markers) {
            if (containsMarker(text, marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMarker(String text, String marker) {
        if (marker.isEmpty()) {
            return false;
        }
        if (isAscii(marker)) {
            // 英文关键词按词边界匹配，避免 “api” 命中 “rapid”
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(marker) + "(?![a-z0-9])")
                    .matcher(text).find();
        }
        return text.contains(marker);
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }
}
