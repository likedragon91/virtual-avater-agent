package com.example.javaweb.enums;

import java.util.Locale;

/**
 * 客户端类型：决定 Access / Refresh Token 的有效期。
 * Electron 桌面端为长会话，浏览器与移动端为短会话。
 */
public enum ClientType {
    DESKTOP,
    WEB;

    public static ClientType from(String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }
        try {
            return ClientType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return WEB;
        }
    }
}
