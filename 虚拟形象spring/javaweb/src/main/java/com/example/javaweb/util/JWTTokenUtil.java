package com.example.javaweb.util;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.javaweb.config.JWTConfig;
import com.example.javaweb.enums.ClientType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JWTTokenUtil {
    private static final String ISSUER = "javaweb";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    @Resource
    private JWTConfig jwtConfig;

    /** 签发 Access Token：typ=access，携带 sid 与 clientType。 */
    public String generateAccessToken(String userId, String username, Integer roleType,
                                      String sid, ClientType clientType, String jti) {
        return generate(userId, username, roleType, sid, clientType, TYPE_ACCESS,
                jwtConfig.accessExpiration(clientType), jti);
    }

    /** 签发 Refresh Token：typ=refresh，与 Access 共用 sid。 */
    public String generateRefreshToken(String userId, String username, Integer roleType,
                                       String sid, ClientType clientType, String jti) {
        return generate(userId, username, roleType, sid, clientType, TYPE_REFRESH,
                jwtConfig.refreshExpiration(clientType), jti);
    }

    private String generate(String userId, String username, Integer roleType, String sid,
                            ClientType clientType, String tokenType, long ttlMillis, String jti) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
            Date expiration = new Date(System.currentTimeMillis() + ttlMillis);
            ClientType type = clientType == null ? ClientType.WEB : clientType;
            return JWT.create()
                    .withClaim("userId", userId)
                    .withClaim("username", username)
                    .withClaim("roleType", roleType)
                    .withClaim("sid", sid)
                    .withClaim("clientType", type.name())
                    .withClaim("typ", tokenType)
                    .withJWTId(jti)//token唯一标识，用于Redis白名单
                    .withExpiresAt(expiration)//过期时间
                    .withIssuedAt(new Date())//签发时间
                    .withIssuer(ISSUER)//签发者
                    .sign(algorithm);//签名
        } catch (Exception e) {
            throw new RuntimeException("生成token失败", e);
        }
    }

    //从解析后的token中取用户ID（解析失败返回null）
    public Long getUserId(DecodedJWT decodedJWT) {
        try {
            String userId = decodedJWT.getClaim("userId").asString();
            return userId != null ? Long.valueOf(userId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getSid(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim("sid").asString();
    }

    public String getTokenType(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim("typ").asString();
    }

    public String getClientType(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim("clientType").asString();
    }

    /**
     * 是否为 Access Token。
     * 兼容升级前签发的旧 token：旧 token 没有 typ 声明，按 Access 处理。
     */
    public boolean isAccessToken(DecodedJWT decodedJWT) {
        String type = getTokenType(decodedJWT);
        return type == null || type.isBlank() || TYPE_ACCESS.equals(type);
    }

    public boolean isRefreshToken(DecodedJWT decodedJWT) {
        return TYPE_REFRESH.equals(getTokenType(decodedJWT));
    }

    //校验并解析token（校验签名、签发者、过期时间）
    public DecodedJWT verifyToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
        return JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token);
    }

}
