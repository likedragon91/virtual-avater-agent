package com.example.javaweb.dto.projection;

import lombok.Data;

import java.time.LocalTime;

/**
 * user_profile 中的通知设置行。
 */
@Data
public class NotificationSettingsRow {
    private LocalTime dndStart;
    private LocalTime dndEnd;
    private Boolean proactiveEnabled;
}
