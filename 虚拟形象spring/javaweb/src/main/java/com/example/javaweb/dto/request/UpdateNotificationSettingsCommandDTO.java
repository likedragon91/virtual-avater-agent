package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNotificationSettingsCommandDTO {
    private Boolean dndEnabled;

    /** HH:mm */
    @Size(max = 5, message = "时间格式应为 HH:mm")
    private String dndStart;

    /** HH:mm */
    @Size(max = 5, message = "时间格式应为 HH:mm")
    private String dndEnd;

    private Boolean proactiveEnabled;
}
