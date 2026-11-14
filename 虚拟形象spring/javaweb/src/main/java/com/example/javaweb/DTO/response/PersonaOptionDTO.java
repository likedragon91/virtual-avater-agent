package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class PersonaOptionDTO {
    private String code;
    private String name;
    private String description;
    private String sample;
    private PersonaTraitsDTO defaultTraits;
}
