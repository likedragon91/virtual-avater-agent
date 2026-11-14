package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户与角色的关系状态。userId 即主键。
 */
@Data
@TableName("relationship_state")
public class RelationshipState {
    @TableId(type = IdType.INPUT)
    private Long userId;

    private BigDecimal intimacyTotal;

    /** S0 / S1 / S2 / S3 / S4 */
    private String stageCode;

    private LocalDateTime stageSince;

    private String userCapStage;

    private LocalDateTime updatedAt;
}
