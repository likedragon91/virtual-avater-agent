package com.example.javaweb.util;


import cn.hutool.core.util.IdUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.javaweb.config.JWTConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JWTTokenUtil {
    private static final String ISSUER = "javaweb";

    @Resource
    private JWTConfig jwtConfig;

    //生成token的方法
    public String generateToken(String userId, String username, Integer roleType) {
        return generateToken(userId, username, roleType, IdUtil.fastSimpleUUID());
    }

    //生成token并指定jti，jti用于在Redis中登记token白名单
    public String generateToken(String userId, String username, Integer roleType, String jti) {
        try {
            //生成签名的算法
            Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
            //生成过期时间
            Date expiration = new Date(System.currentTimeMillis() + jwtConfig.getExpiration());
            //生成token
            return JWT.create()
                    .withClaim("userId", userId)
                    .withClaim("username", username)
                    .withClaim("roleType", roleType)
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

    //校验并解析token（校验签名、签发者、过期时间）
    public DecodedJWT verifyToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
        return JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token);
    }

}
