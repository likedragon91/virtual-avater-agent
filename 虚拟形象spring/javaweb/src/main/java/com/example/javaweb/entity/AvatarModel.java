package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户导入的 Live2D 模型包（zip）。file_path 相对上传根目录。
 */
@Data
@TableName("avatar_model")
public class AvatarModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String driver;

    private String filePath;

    private Long fileSize;

    private LocalDateTime createdAt;
}
