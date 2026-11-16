package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RespondTaskCommandDTO {
    @NotNull(message = "完成度不能为空")
    @Min(value = 0, message = "完成度不能小于 0")
    @Max(value = 100, message = "完成度不能大于 100")
    private Integer completionRatio;

    @Size(max = 100, message = "备注不能超过 100 字")
    private String note;

    /** 客户端生成，用于幂等 */
    @NotBlank(message = "idempotencyKey 不能为空")
    @Size(max = 64, message = "idempotencyKey 不能超过 64 字")
    private String idempotencyKey;
}
