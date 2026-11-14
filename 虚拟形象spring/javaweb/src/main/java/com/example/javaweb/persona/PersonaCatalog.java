package com.example.javaweb.persona;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人格预设目录。展示元数据在代码里，风格正文存放在 prompt_template。
 */
public final class PersonaCatalog {
    public static final String DEFAULT_CODE = "persona_default";
    /** chat_session.persona_code 的默认值，表示“跟随用户默认人格” */
    public static final String SESSION_DEFAULT_CODE = "default";

    public record Traits(double warmth, double humor, double directness,
                         double initiative, double verbosity) {
    }

    public record Preset(String code, String name, String description,
                         String sample, Traits defaultTraits) {
    }

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    static {
        add(new Preset(
                "persona_default", "默认",
                "自然、温和的陪伴",
                "我在呢，慢慢说。",
                new Traits(0.50, 0.30, 0.50, 0.50, 0.50)));
        add(new Preset(
                "persona_warm", "温柔陪伴",
                "先接住情绪，再一起想办法",
                "听起来今天挺累的，先歇一会儿也没关系。",
                new Traits(0.85, 0.25, 0.40, 0.50, 0.50)));
        add(new Preset(
                "persona_lively", "元气活泼",
                "轻快、有活力，主动找话题",
                "来啦！今天有什么想分享的？",
                new Traits(0.50, 0.70, 0.50, 0.75, 0.55)));
        add(new Preset(
                "persona_calm", "冷静理性",
                "直接、有条理，先给结论",
                "先给结论：这件事可以拆成两步。",
                new Traits(0.40, 0.20, 0.80, 0.40, 0.45)));
        add(new Preset(
                "persona_teasing", "善意毒舌",
                "轻微调侃，分寸感优先",
                "又拖到最后一天了？嗯，我一点也不意外。",
                new Traits(0.50, 0.80, 0.65, 0.55, 0.50)));
    }

    private PersonaCatalog() {
    }

    private static void add(Preset preset) {
        PRESETS.put(preset.code(), preset);
    }

    public static List<Preset> all() {
        return List.copyOf(PRESETS.values());
    }

    public static Preset find(String code) {
        return code == null ? null : PRESETS.get(code);
    }

    public static boolean isValid(String code) {
        return find(code) != null;
    }

    public static String normalize(String code) {
        return isValid(code) ? code : DEFAULT_CODE;
    }

    public static Traits defaults(String code) {
        Preset preset = find(code);
        return preset == null ? find(DEFAULT_CODE).defaultTraits() : preset.defaultTraits();
    }

    public static Traits clamp(Traits traits) {
        if (traits == null) {
            return defaults(DEFAULT_CODE);
        }
        return new Traits(
                clamp01(traits.warmth()),
                clamp01(traits.humor()),
                clamp01(traits.directness()),
                clamp01(traits.initiative()),
                clamp01(traits.verbosity()));
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
