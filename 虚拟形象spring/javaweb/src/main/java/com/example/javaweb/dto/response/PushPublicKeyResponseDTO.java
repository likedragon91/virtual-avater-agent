package com.example.javaweb.dto.response;

import lombok.Data;

@Data
public class PushPublicKeyResponseDTO {
    private boolean enabled;
    private String publicKey;
}
