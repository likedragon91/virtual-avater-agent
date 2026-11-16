package com.example.javaweb.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubscribePushCommandDTO {
    @NotBlank(message = "endpoint 不能为空")
    @Size(max = 4096, message = "endpoint 过长")
    private String endpoint;

    @NotNull(message = "keys 不能为空")
    @Valid
    private Keys keys;

    @Size(max = 255, message = "userAgent 过长")
    private String userAgent;

    @Data
    public static class Keys {
        @NotBlank(message = "p256dh 不能为空")
        @Size(max = 512, message = "p256dh 过长")
        private String p256dh;

        @NotBlank(message = "auth 不能为空")
        @Size(max = 512, message = "auth 过长")
        private String auth;
    }
}
