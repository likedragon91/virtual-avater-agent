package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSessionCommandDTO {
    @Size(max = 120, message = "标题不能超过 120 字")
    private String title;

    /** 1-正常，0-归档 */
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    /** 会话级人格；default 表示跟随用户默认人格 */
    @Size(max = 32, message = "人格编码不能超过 32 字")
    private String personaCode;
}
