package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class PersonaCurrentResponseDTO {
    private String code;
    private String name;
    private String description;
    private String sample;
    private PersonaTraitsDTO traits;
}
