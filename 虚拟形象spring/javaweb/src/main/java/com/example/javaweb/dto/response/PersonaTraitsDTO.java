package com.example.javaweb.dto.response;

import lombok.Data;

/**
 * 风格强度，取值 0~1。
 */
@Data
public class PersonaTraitsDTO {
    private Double warmth;
    private Double humor;
    private Double directness;
    private Double initiative;
    private Double verbosity;
}
