package com.example.javaweb.service;

import com.example.javaweb.dto.request.UpdateNotificationSettingsCommandDTO;
import com.example.javaweb.dto.projection.NotificationSettingsRow;
import com.example.javaweb.dto.response.NotificationSettingsResponseDTO;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.UserProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 通知设置：免打扰窗口、主动消息开关。
 * 约定：dnd_enabled=false 时把 dnd_start / dnd_end 都写成 00:00。
 */
@Service
public class NotificationSettingsService {
    public static final LocalTime DEFAULT_DND_START = LocalTime.of(23, 0);
    public static final LocalTime DEFAULT_DND_END = LocalTime.of(8, 0);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Resource
    private UserProfileMapper userProfileMapper;

    public NotificationSettingsResponseDTO getSettings(Long userId) {
        NotificationSettingsRow row = ensureRow(userId);
        LocalTime start = row != null && row.getDndStart() != null
                ? row.getDndStart() : DEFAULT_DND_START;
        LocalTime end = row != null && row.getDndEnd() != null
                ? row.getDndEnd() : DEFAULT_DND_END;
        boolean dndEnabled = !start.equals(end);
        boolean proactiveEnabled = row == null || row.getProactiveEnabled() == null
                || row.getProactiveEnabled();
        return toDto(dndEnabled, start, end, proactiveEnabled);
    }

    public NotificationSettingsResponseDTO save(Long userId,
                                                UpdateNotificationSettingsCommandDTO command) {
        boolean dndEnabled = command.getDndEnabled() == null || command.getDndEnabled();
        LocalTime start;
        LocalTime end;
        if (!dndEnabled) {
            start = LocalTime.MIDNIGHT;
            end = LocalTime.MIDNIGHT;
        } else {
            start = parseTime(command.getDndStart(), DEFAULT_DND_START);
            end = parseTime(command.getDndEnd(), DEFAULT_DND_END);
            if (start.equals(end)) {
                throw new BusinessException("PARAM_ERROR", "免打扰开始和结束时间不能相同");
            }
        }

        Boolean proactive = command.getProactiveEnabled();
        if (proactive == null) {
            proactive = getSettings(userId).isProactiveEnabled();
        }

        ensureRow(userId);
        userProfileMapper.updateNotificationSettings(userId, start, end, proactive);
        return getSettings(userId);
    }

    /**
     * 当前时间是否处于免打扰窗口。
     * 支持跨天，例如 23:00 ~ 08:00。
     */
    public boolean isDnd(Long userId, LocalTime time) {
        NotificationSettingsResponseDTO settings = getSettings(userId);
        if (!settings.isDndEnabled()) {
            return false;
        }
        LocalTime start = parseTime(settings.getDndStart(), DEFAULT_DND_START);
        LocalTime end = parseTime(settings.getDndEnd(), DEFAULT_DND_END);
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    private NotificationSettingsRow ensureRow(Long userId) {
        NotificationSettingsRow row = userProfileMapper.selectNotificationSettings(userId);
        if (row != null) {
            return row;
        }
        try {
            userProfileMapper.insert(userId, "{}");
        } catch (DuplicateKeyException ignored) {
            // 并发创建时忽略
        }
        return userProfileMapper.selectNotificationSettings(userId);
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(value.trim(), HH_MM);
        } catch (DateTimeParseException e) {
            throw new BusinessException("PARAM_ERROR", "时间格式应为 HH:mm");
        }
    }

    private static NotificationSettingsResponseDTO toDto(boolean dndEnabled,
                                                         LocalTime start,
                                                         LocalTime end,
                                                         boolean proactiveEnabled) {
        NotificationSettingsResponseDTO dto = new NotificationSettingsResponseDTO();
        dto.setDndEnabled(dndEnabled);
        dto.setDndStart(format(start));
        dto.setDndEnd(format(end));
        dto.setProactiveEnabled(proactiveEnabled);
        return dto;
    }

    private static String format(LocalTime time) {
        return time.format(HH_MM);
    }
}
