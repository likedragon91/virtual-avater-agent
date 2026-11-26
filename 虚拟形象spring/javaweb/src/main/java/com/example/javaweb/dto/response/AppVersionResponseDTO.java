package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class AppVersionResponseDTO {
    private Long id;
    private String version;
    private String notes;
    private String fileName;
    private Long fileSize;
    /** /api/files/updates/xxx.exe（前端按环境补全域名） */
    private String downloadUrl;
    private String createdAt;
}
