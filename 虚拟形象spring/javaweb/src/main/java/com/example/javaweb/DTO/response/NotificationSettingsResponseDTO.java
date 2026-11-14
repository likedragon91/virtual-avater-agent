package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class NotificationSettingsResponseDTO {
    private boolean dndEnabled;
    /** HH:mm */
    private String dndStart;
    /** HH:mm */
    private String dndEnd;
    private boolean proactiveEnabled;
}
