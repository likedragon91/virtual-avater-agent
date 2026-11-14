package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Web Push / VAPID 配置。私钥只放本地 application.properties 或环境变量。
 */
@Data
@ConfigurationProperties(prefix = "webpush")
@Component
public class WebPushProperties {
    private boolean enabled;
    private Vapid vapid = new Vapid();

    @Data
    public static class Vapid {
        private String publicKey;
        private String privateKey;
        private String subject = "mailto:admin@example.com";
    }
}
