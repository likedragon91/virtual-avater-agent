package com.example.javaweb.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IntimacyLogResponseDTO {
    private Long id;
    private BigDecimal delta;
    private BigDecimal balanceAfter;
    private String reason;
    private String reasonLabel;
    private String refType;
    private Long refId;
    private String createdAt;
}
