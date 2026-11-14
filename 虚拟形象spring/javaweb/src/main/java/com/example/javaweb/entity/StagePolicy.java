package com.example.javaweb.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * stage_policy 只读行。config 是 JSON 字符串，包含 tone/banned/topics/address 等。
 */
@Data
public class StagePolicy {
    private String code;
    private Integer version;
    private String config;
    private Boolean active;
    private LocalDateTime updatedAt;
}
