package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class TaskListResponseDTO {
    private List<TaskItemResponseDTO> records;
    private long total;
    private long page;
    private long size;
}
