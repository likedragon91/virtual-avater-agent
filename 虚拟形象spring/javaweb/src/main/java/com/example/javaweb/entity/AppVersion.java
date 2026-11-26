package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 桌面端安装包版本。每次上传即发布，最新一条供检查更新页和全站通知使用。
 */
@Data
@TableName("app_version")
public class AppVersion {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;

    private String notes;

    private String filePath;

    private String fileName;

    private Long fileSize;

    private Long createdBy;

    private LocalDateTime createdAt;
}
