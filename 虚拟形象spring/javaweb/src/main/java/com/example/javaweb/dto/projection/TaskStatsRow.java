package com.example.javaweb.dto.projection;

import lombok.Data;

import java.math.BigDecimal;

/**
 * query_task_stats 工具的聚合结果。
 */
@Data
public class TaskStatsRow {
    private Long total;
    private BigDecimal avgCompletion;
    private Long completed;
}
