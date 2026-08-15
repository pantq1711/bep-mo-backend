package com.bepmo.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
 *
 * Redis chỉ là lớp revoke nhanh, không phải source of truth cho authentication.
 * Nếu Redis tạm unavailable, fail-open để các request có JWT hợp lệ vẫn hoạt động;
 * đổi lại access token vừa logout có thể còn hiệu lực tới TTL tự nhiên (tối đa 15p
 * theo cấu hình hiện tại). Refresh token vẫn được revoke trong PostgreSQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0 || jti == null || jti.isBlank()) return;

        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException ex) {
            // Best-effort revocation: Redis outage must not roll back DB refresh-token revoke/logout.
            log.warn("JWT blacklist write failed; access token will expire naturally: {}", ex.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (DataAccessException ex) {
            // Availability over immediate logout revocation when Redis is down. JWT signature + exp
            // are still verified; the temporary security trade-off is bounded by access-token TTL.
            log.warn("JWT blacklist read failed; falling back to JWT signature/expiry only: {}", ex.getMessage());
            return false;
        }
    }
}
