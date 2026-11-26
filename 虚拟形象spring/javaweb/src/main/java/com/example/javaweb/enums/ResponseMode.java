package com.example.javaweb.enums;

import java.util.Locale;

/**
 * 本轮回复的长度与格式档位。
 * 只约束“说多少、怎么排版”，不干涉人格语气和关系阶段边界。
 */
public enum ResponseMode {

    CASUAL("闲聊", "1-2 句",
            "只用自然口语，回复正文不要使用标题、编号或项目符号列表。"),

    EMOTIONAL("情感陪伴", "1-3 句，先接住情绪但不放大，不做长篇分析",
            "只用自然口语，回复正文不要使用标题、编号或项目符号列表。"),

    DISCUSSION("日常讨论", "2-4 句，保持轻松交流",
            "只用自然口语，回复正文不要使用标题、编号或项目符号列表。"),

    KNOWLEDGE("知识问答", "先给结论，再补充必要解释",
            "用自然段说明，不要使用标题和宽表格；需要强调时用加粗短语领起即可。"),

    TASK("复杂任务", "按实际需要展开，不牺牲完整性",
            "为了讲清步骤可以使用编号列表，但不要写客套的导语或总结。");

    private final String label;
    private final String lengthBand;
    private final String formatRule;

    ResponseMode(String label, String lengthBand, String formatRule) {
        this.label = label;
        this.lengthBand = lengthBand;
        this.formatRule = formatRule;
    }

    public String label() {
        return label;
    }

    public String lengthBand() {
        return lengthBand;
    }

    public String formatRule() {
        return formatRule;
    }

    /** 适合用 token 上限兜底短回复的模式 */
    public boolean isShort() {
        return this == CASUAL || this == EMOTIONAL;
    }

    /** 大小写不敏感；非法值返回 null，交给自动分类 */
    public static ResponseMode from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ResponseMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
