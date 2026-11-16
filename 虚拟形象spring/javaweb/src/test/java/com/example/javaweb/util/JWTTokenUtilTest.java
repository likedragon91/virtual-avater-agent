package com.example.javaweb.util;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.javaweb.config.JWTConfig;
import com.example.javaweb.enums.ClientType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JWTTokenUtilTest {

    private JWTTokenUtil tokenUtil;

    @BeforeEach
    void setUp() {
        JWTConfig config = new JWTConfig();
        config.setSecret("test-secret-at-least-32-characters-long-123");
        config.setAccessExpirationDesktop(3_600_000L);
        config.setAccessExpirationWeb(1_800_000L);
        config.setRefreshExpirationDesktop(2_592_000_000L);
        config.setRefreshExpirationWeb(1_209_600_000L);

        tokenUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(tokenUtil, "jwtConfig", config);
    }

    @Test
    void accessTokenCarriesSessionClaims() {
        String token = tokenUtil.generateAccessToken(
                "42", "alice", 2, "sid-1", ClientType.DESKTOP, "jti-1");

        DecodedJWT jwt = tokenUtil.verifyToken(token);

        assertThat(tokenUtil.isAccessToken(jwt)).isTrue();
        assertThat(tokenUtil.isRefreshToken(jwt)).isFalse();
        assertThat(tokenUtil.getSid(jwt)).isEqualTo("sid-1");
        assertThat(tokenUtil.getUserId(jwt)).isEqualTo(42L);
        assertThat(tokenUtil.getClientType(jwt)).isEqualTo("DESKTOP");
        assertThat(jwt.getId()).isEqualTo("jti-1");
        assertThat(jwt.getClaim("roleType").asInt()).isEqualTo(2);
    }

    @Test
    void refreshTokenUsesWebRefreshTtl() {
        String token = tokenUtil.generateRefreshToken(
                "42", "alice", 1, "sid-2", ClientType.WEB, "jti-2");

        DecodedJWT jwt = tokenUtil.verifyToken(token);
        long ttlSeconds = (jwt.getExpiresAt().getTime() - jwt.getIssuedAt().getTime()) / 1000;

        assertThat(tokenUtil.isRefreshToken(jwt)).isTrue();
        assertThat(tokenUtil.isAccessToken(jwt)).isFalse();
        assertThat(ttlSeconds).isBetween(1_209_000L, 1_210_000L);
    }

    @Test
    void accessTtlDiffersByClientType() {
        DecodedJWT desktop = tokenUtil.verifyToken(tokenUtil.generateAccessToken(
                "1", "u", 1, "sid", ClientType.DESKTOP, "d1"));
        DecodedJWT web = tokenUtil.verifyToken(tokenUtil.generateAccessToken(
                "1", "u", 1, "sid", ClientType.WEB, "w1"));

        long desktopTtl = (desktop.getExpiresAt().getTime() - desktop.getIssuedAt().getTime()) / 1000;
        long webTtl = (web.getExpiresAt().getTime() - web.getIssuedAt().getTime()) / 1000;

        assertThat(desktopTtl).isBetween(3_599L, 3_600L);
        assertThat(webTtl).isBetween(1_799L, 1_800L);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String token = tokenUtil.generateAccessToken(
                "1", "u", 1, "sid", ClientType.WEB, "x1");

        JWTConfig otherConfig = new JWTConfig();
        otherConfig.setSecret("another-secret-at-least-32-characters-999");
        JWTTokenUtil otherUtil = new JWTTokenUtil();
        ReflectionTestUtils.setField(otherUtil, "jwtConfig", otherConfig);

        assertThatThrownBy(() -> otherUtil.verifyToken(token)).isInstanceOf(Exception.class);
    }
}
