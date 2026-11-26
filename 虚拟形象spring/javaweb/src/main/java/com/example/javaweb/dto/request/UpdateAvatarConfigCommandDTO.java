package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateAvatarConfigCommandDTO {

    /** pixel / live2d / image；为空时按 modelId 推断 */
    @Size(max = 16, message = "driver 过长")
    private String driver;

    @Size(max = 32, message = "预设 ID 过长")
    private String presetId;

    /** driver=live2d 时必填，必须是当前用户导入的模型 */
    private Long modelId;

    /** 颜色为 #RRGGBB；未知键与非法值会被服务端过滤 */
    private Map<String, String> palette;

    /** 乐观锁版本；首次保存传 0 或空 */
    private Integer revision;
}
