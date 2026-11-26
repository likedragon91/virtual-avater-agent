package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AvatarConfigResponseDTO {
    /** pixel / live2d */
    private String driver;
    private String presetId;
    private Map<String, String> palette = new LinkedHashMap<>();
    /** driver=live2d 时使用 */
    private Long modelId;
    private String modelName;
    /** driver=image 时的公开图片地址 */
    private String modelUrl;
    private Integer revision;
    private String updatedAt;
}
