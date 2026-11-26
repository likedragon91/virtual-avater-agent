package com.example.javaweb.avatar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 内置形象预设与配色白名单。
 *
 * 像素驱动用 tint 实现配色差异，不依赖额外美术资源；
 * 后续接入分层 Sprite / 用户模型包时，只需扩展此处目录与 driver。
 */
public final class AvatarCatalog {
    public static final String DEFAULT_PRESET_ID = "preset_default";
    public static final String DRIVER_PIXEL = "pixel";

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public static final Set<String> PALETTE_KEYS =
            Set.of("hair", "top", "bottom", "shoes", "skin", "background");

    public static final Map<String, String> PALETTE_LABELS = new LinkedHashMap<>();

    static {
        PALETTE_LABELS.put("hair", "头发");
        PALETTE_LABELS.put("top", "上衣");
        PALETTE_LABELS.put("bottom", "下装");
        PALETTE_LABELS.put("shoes", "鞋子");
        PALETTE_LABELS.put("skin", "肤色");
        PALETTE_LABELS.put("background", "背景");
    }

    public record Preset(String id, String name, String description, Map<String, String> palette) {
    }

    public static final Map<String, String> DEFAULT_PALETTE = Map.of(
            "hair", "#6b4b3e",
            "top", "#4f86c6",
            "bottom", "#37415f",
            "shoes", "#2b2b33",
            "skin", "#f2c9a0",
            "background", "#101827"
    );

    public static final List<Preset> PRESETS = List.of(
            new Preset(DEFAULT_PRESET_ID, "默认", "蓝衫与深色下装，像素舞台的初始配色",
                    new LinkedHashMap<>(DEFAULT_PALETTE)),
            new Preset("preset_warm", "暖阳", "棕色头发与暖橙上衣，适合日常闲聊",
                    palette("#7a4a2f", "#d9825b", "#5a4032", "#3a2a24", "#f4c7a1", "#1a1410")),
            new Preset("preset_cool", "冷夜", "深蓝发色与冷色上衣，适合安静的时刻",
                    palette("#39465e", "#5c8fd6", "#2c3550", "#1f2533", "#f0c9ae", "#0c1220")),
            new Preset("preset_night", "星夜", "紫调衣服与深色头发，夜间更柔和",
                    palette("#2b2b3a", "#6a5acd", "#24243a", "#15151f", "#e8c4a8", "#0a0f1c"))
    );

    private AvatarCatalog() {
    }

    public static Preset find(String id) {
        if (id == null) {
            return null;
        }
        for (Preset preset : PRESETS) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * 合并默认值并过滤非法键/颜色，避免把任意 JSON 写进配置。
     */
    public static Map<String, String> sanitize(Map<String, String> palette) {
        Map<String, String> result = new LinkedHashMap<>(DEFAULT_PALETTE);
        if (palette == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : palette.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && PALETTE_KEYS.contains(key) && isValidColor(value)) {
                result.put(key, value.toLowerCase());
            }
        }
        return result;
    }

    public static boolean isValidColor(String value) {
        return value != null && COLOR_PATTERN.matcher(value).matches();
    }

    private static Map<String, String> palette(String hair, String top, String bottom,
                                               String shoes, String skin, String background) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("hair", hair);
        map.put("top", top);
        map.put("bottom", bottom);
        map.put("shoes", shoes);
        map.put("skin", skin);
        map.put("background", background);
        return map;
    }
}
