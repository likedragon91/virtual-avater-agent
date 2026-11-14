package com.example.javaweb.dto.request;

import com.example.javaweb.dto.response.PersonaTraitsDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePersonaCommandDTO {
    @NotBlank(message = "人格编码不能为空")
    @Size(max = 64, message = "人格编码不能超过 64 字")
    private String personaCode;

    /** 可选；不传时使用该人格的默认特质 */
    private PersonaTraitsDTO traits;
}
