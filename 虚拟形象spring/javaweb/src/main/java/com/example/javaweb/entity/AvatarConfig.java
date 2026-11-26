package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户形象配置。config 是 JSON：{presetId, palette:{hair,top,bottom,shoes,skin,background}}。
 */
@Data
@TableName("avatar_config")
public class AvatarConfig {
    @TableId(type = IdType.INPUT)
    private Long userId;

    private String driver;

    private String config;

    private Integer revision;

    private LocalDateTime updatedAt;
}
