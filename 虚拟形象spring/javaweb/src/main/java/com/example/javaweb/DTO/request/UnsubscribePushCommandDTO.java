package com.example.javaweb.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UnsubscribePushCommandDTO {
    @NotBlank(message = "endpoint 不能为空")
    @Size(max = 4096, message = "endpoint 过长")
    private String endpoint;
}
