package com.example.javaweb.dto.projection;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * occurrence + task + 已有回应 的联表查询行。
 */
@Data
public class OccurrenceRow {
    private Long id;
    private Long taskId;
    private Long userId;
    private LocalDateTime occurAt;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime firedAt;
    private LocalDateTime respondedAt;
    private String title;
    private String note;
    private Integer priority;
    private Integer responseWindowMinutes;
    private Long responseId;
    private Integer completionRatio;
    private String responseNote;
    private BigDecimal intimacyDelta;
    private LocalDateTime revisedAt;
}
