package com.example.javaweb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    @InjectMocks
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
    }

    @Test
    void saveSessionRegistersAccessRefreshAndSession() {
        tokenService.saveSession("sid1", "a1", "r1", 7L, "WEB", 1_800_000L, 1_209_600_000L);

        verify(valueOps).set("auth:token:a1", "sid1", Duration.ofMillis(1_800_000L));
        verify(valueOps).set("auth:refresh:r1", "sid1", Duration.ofMillis(1_209_600_000L));
        verify(valueOps).set("auth:session:sid1", "7|a1|r1|WEB", Duration.ofMillis(1_209_600_000L));
        verify(setOps).add("auth:user-tokens:7", "a1");
        verify(setOps).add("auth:user-refresh:7", "r1");
        verify(setOps).add("auth:user-sessions:7", "sid1");
    }

    @Test
    void isValidChecksWhitelist() {
        when(redis.hasKey("auth:token:a1")).thenReturn(true);
        when(redis.hasKey("auth:token:a2")).thenReturn(false);

        assertThat(tokenService.isValid("a1")).isTrue();
        assertThat(tokenService.isValid("a2")).isFalse();
        assertThat(tokenService.isValid(null)).isFalse();
    }

    @Test
    void rotateMarksOldRefreshAsUsedAndWritesNewPair() {
        tokenService.rotate("sid1", "oldRefresh", "newAccess", "newRefresh",
                7L, "WEB", 1_800_000L, 1_209_600_000L);

        verify(valueOps).set("auth:refresh-used:oldRefresh", "7", Duration.ofMinutes(2));
        verify(redis).delete("auth:refresh:oldRefresh");
        verify(setOps).remove("auth:user-refresh:7", "oldRefresh");
        verify(valueOps).set("auth:token:newAccess", "sid1", Duration.ofMillis(1_800_000L));
        verify(valueOps).set("auth:refresh:newRefresh", "sid1", Duration.ofMillis(1_209_600_000L));
    }

    @Test
    void revokeSessionDeletesAccessAndRefresh() {
        when(valueOps.get("auth:session:sid1")).thenReturn("7|a1|r1|WEB");

        tokenService.revokeSession("sid1");

        verify(redis).delete("auth:session:sid1");
        verify(redis).delete("auth:token:a1");
        verify(redis).delete("auth:refresh:r1");
        verify(setOps).remove("auth:user-tokens:7", "a1");
        verify(setOps).remove("auth:user-refresh:7", "r1");
    }

    @Test
    void revokeAllForUserClearsAllSessions() {
        Set<String> access = new LinkedHashSet<>(Set.of("a1"));
        Set<String> refresh = new LinkedHashSet<>(Set.of("r1"));
        Set<String> sessions = new LinkedHashSet<>(Set.of("sid1"));
        when(setOps.members("auth:user-tokens:7")).thenReturn(access);
        when(setOps.members("auth:user-refresh:7")).thenReturn(refresh);
        when(setOps.members("auth:user-sessions:7")).thenReturn(sessions);

        tokenService.revokeAllForUser(7L);

        verify(redis, times(3)).delete(anyCollection());
        verify(redis).delete("auth:user-tokens:7");
        verify(redis).delete("auth:user-refresh:7");
        verify(redis).delete("auth:user-sessions:7");
    }
}
