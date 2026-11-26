package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TtsSynthesizeCommandDTO {

    @NotBlank(message = "文本不能为空")
    @Size(max = 300, message = "单次语音文本不能超过 300 字")
    private String text;

    @Size(max = 64, message = "音色 ID 过长")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "音色 ID 不合法")
    private String speaker;

    /** 语速 -50..100，空表示使用服务端默认值 */
    @Min(value = -50, message = "语速不能小于 -50")
    @Max(value = 100, message = "语速不能大于 100")
    private Integer speed;
}
