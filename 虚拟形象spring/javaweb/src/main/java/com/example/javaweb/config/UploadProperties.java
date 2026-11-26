package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传目录与大小限制。multipart 总上限在 application.properties 中配置；
 * 超过 file-size-threshold 的文件由容器落盘，不占堆内存。
 */
@Data
@ConfigurationProperties(prefix = "app.upload")
@Component
public class UploadProperties {
    /** 上传根目录，相对路径按进程工作目录解析 */
    private String dir = "./uploads";
    /** 公开图片访问前缀，由 FileController 提供 */
    private String publicPath = "/api/files";
    private long maxImageBytes = 20L * 1024 * 1024;
    private long maxModelBytes = 500L * 1024 * 1024;
    /** 桌面端安装包（exe）上限；multipart 内存阈值仍为 20MB，超过部分落盘 */
    private long maxInstallerBytes = 300L * 1024 * 1024;
}
