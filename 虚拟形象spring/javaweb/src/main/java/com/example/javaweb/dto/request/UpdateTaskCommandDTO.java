package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTaskCommandDTO {
    @Size(max = 200, message = "任务标题不能超过 200 字")
    private String title;

    @Size(max = 2000, message = "备注不能超过 2000 字")
    private String note;

    private String dueAt;

    @Size(max = 64, message = "时区不能超过 64 字")
    private String tz;

    @Size(max = 255, message = "重复规则不能超过 255 字")
    private String rrule;

    @Min(value = 1, message = "优先级只能是 1~3")
    @Max(value = 3, message = "优先级只能是 1~3")
    private Integer priority;

    @Min(value = 15, message = "回应窗口不能小于 15 分钟")
    @Max(value = 720, message = "回应窗口不能超过 12 小时")
    private Integer responseWindowMinutes;

    private Boolean allowNote;

    @Pattern(regexp = "ACTIVE|PAUSED|ARCHIVED", message = "任务状态不合法")
    private String status;
}
