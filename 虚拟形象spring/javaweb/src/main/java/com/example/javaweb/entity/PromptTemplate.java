package com.example.javaweb.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * prompt_template 只读行。使用 (code, version) 复合主键，查询走自定义 Mapper。
 */
@Data
public class PromptTemplate {
    private String code;
    private Integer version;
    private String content;
    private Boolean active;
    private LocalDateTime updatedAt;
}
