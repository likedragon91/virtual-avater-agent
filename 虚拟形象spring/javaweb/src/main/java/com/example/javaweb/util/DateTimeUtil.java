package com.example.javaweb.util;

import com.example.javaweb.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 任务接口统一使用 yyyy-MM-dd HH:mm:ss；同时兼容 ISO 和纯日期。
 */
public final class DateTimeUtil {
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<DateTimeFormatter> INPUTS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    private DateTimeUtil() {
    }

    public static LocalDateTime parseFlexible(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 10) {
            try {
                return LocalDate.parse(text).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }
        }
        for (DateTimeFormatter formatter : INPUTS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试
            }
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            throw new BusinessException("PARAM_ERROR", "时间格式不正确，请使用 yyyy-MM-dd HH:mm:ss");
        }
    }

    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(OUTPUT);
    }
}
