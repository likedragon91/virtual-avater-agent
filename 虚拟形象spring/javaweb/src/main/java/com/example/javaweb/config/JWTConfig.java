package com.example.javaweb.config;


import com.example.javaweb.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
@Component
@Data
public class JWTConfig {
    @NotBlank(message = "必须通过环境变量 JWT_SECRET 配置 JWT 签名密钥")
    @Size(min = 32, message = "JWT_SECRET 长度至少为 32 个字符")
    private String secret;
    private long expiration;
    private long refreshExpiration;
    private String tokenPrefix;
    private String header;

    /** Electron 桌面端：1 小时 */
    private long accessExpirationDesktop = 3_600_000L;
    /** 浏览器 / PWA：30 分钟 */
    private long accessExpirationWeb = 1_800_000L;
    /** Electron 桌面端：30 天 */
    private long refreshExpirationDesktop = 2_592_000_000L;
    /** 浏览器 / PWA：14 天 */
    private long refreshExpirationWeb = 1_209_600_000L;

    /** Access Token 有效期；未配置分环境值时回退到旧 expiration。 */
    public long accessExpiration(ClientType clientType) {
        long configured = clientType == ClientType.DESKTOP
                ? accessExpirationDesktop
                : accessExpirationWeb;
        if (configured > 0) {
            return configured;
        }
        return expiration > 0 ? expiration : 3_600_000L;
    }

    /** Refresh Token 有效期；未配置分环境值时回退到旧 refreshExpiration。 */
    public long refreshExpiration(ClientType clientType) {
        long configured = clientType == ClientType.DESKTOP
                ? refreshExpirationDesktop
                : refreshExpirationWeb;
        if (configured > 0) {
            return configured;
        }
        if (refreshExpiration > 0) {
            return refreshExpiration;
        }
        return accessExpiration(clientType) * 24;
    }
}
