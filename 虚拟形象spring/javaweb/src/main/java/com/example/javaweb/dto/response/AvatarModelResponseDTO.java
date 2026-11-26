package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class AvatarModelResponseDTO {
    private Long id;
    private String name;
    private String driver;
    /** driver=image 时的公开图片地址；zip 模型为空，走鉴权下载 */
    private String url;
    private Long size;
    private String createdAt;
}
