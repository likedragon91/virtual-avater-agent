package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AvatarPresetDTO {
    private String id;
    private String name;
    private String description;
    private Map<String, String> palette = new LinkedHashMap<>();
}
