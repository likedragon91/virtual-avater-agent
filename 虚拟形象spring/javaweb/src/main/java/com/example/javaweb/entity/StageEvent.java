package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阶段升级事件。唯一键 (user_id, to_stage) 防止重复记录。
 */
@Data
@TableName("stage_event")
public class StageEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fromStage;

    private String toStage;

    private LocalDateTime createdAt;
}
