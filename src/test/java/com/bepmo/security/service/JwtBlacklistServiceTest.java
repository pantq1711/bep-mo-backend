package com.bepmo.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new JwtBlacklistService(redisTemplate);
    }

    @Test
    void blacklistWritesJtiWithRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklist("jti-1", 60);

        verify(valueOperations).set("jwt:blacklist:jti-1", "1", Duration.ofSeconds(60));
    }

    @Test
    void blacklistSkipsExpiredTokenWithoutCallingRedis() {
        service.blacklist("jti-1", 0);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void blacklistDoesNotFailLogoutWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(valueOperations)
                .set("jwt:blacklist:jti-1", "1", Duration.ofSeconds(60));

        assertDoesNotThrow(() -> service.blacklist("jti-1", 60));
    }

    @Test
    void isBlacklistedReturnsRedisValueWhenAvailable() {
        when(redisTemplate.hasKey("jwt:blacklist:jti-1")).thenReturn(true);

        assertTrue(service.isBlacklisted("jti-1"));
    }

    @Test
    void isBlacklistedFailsOpenWhenRedisIsUnavailable() {
        when(redisTemplate.hasKey("jwt:blacklist:jti-1"))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertFalse(service.isBlacklisted("jti-1"));
    }
}
