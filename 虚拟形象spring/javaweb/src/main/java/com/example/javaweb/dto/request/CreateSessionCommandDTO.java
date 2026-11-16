package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionCommandDTO {
    @Size(max = 120, message = "标题不能超过 120 字")
    private String title;

    @Size(max = 32, message = "角色编码不能超过 32 字")
    private String personaCode;
}
