package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class IntimacyLogListResponseDTO {
    private List<IntimacyLogResponseDTO> records;
    private long total;
    private long page;
    private long size;
}
