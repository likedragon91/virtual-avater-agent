package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AvatarCatalogResponseDTO {
    private String driver;
    private String defaultPresetId;
    private Map<String, String> defaultPalette = new LinkedHashMap<>();
    private List<AvatarPaletteFieldDTO> paletteFields = new ArrayList<>();
    private List<AvatarPresetDTO> presets = new ArrayList<>();
}
