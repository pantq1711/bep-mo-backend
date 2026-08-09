package com.bepmo.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Blacklist access token khi logout — thu hẹp "vulnerability window" đã ghi
 * trong đề cương (access token vẫn sống tới khi tự hết hạn dù đã logout).
 *
 * Thiết kế: chỉ lưu jti (không lưu cả token) trong Redis, TTL = thời gian còn
 * lại tới khi token hết hạn tự nhiên -> key tự biến mất đúng lúc, không cần
 * job dọn dẹp riêng, không phình Redis theo thời gian.
 */
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) return; // token đã hết hạn tự nhiên, không cần blacklist nữa
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
