package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class SessionListResponseDTO {
    private List<ChatSessionResponseDTO> records;
    private long total;
    private long page;
    private long size;
}
