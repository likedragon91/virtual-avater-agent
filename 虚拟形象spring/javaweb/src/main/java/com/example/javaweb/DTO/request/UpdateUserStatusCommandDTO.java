package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改用户状态：0-禁用，1-正常。
 */
@Data
public class UpdateUserStatusCommandDTO {
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
