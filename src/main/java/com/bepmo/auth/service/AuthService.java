package com.bepmo.auth.service;

import com.bepmo.auth.dto.AuthDtos.*;
import com.bepmo.common.exception.AppException;
import com.bepmo.config.JwtProperties;
import com.bepmo.security.util.JwtUtil;
import com.bepmo.user.entity.RefreshToken;
import com.bepmo.user.entity.User;
import com.bepmo.user.entity.UserRole;
import com.bepmo.user.entity.UserStatus;
import com.bepmo.user.repository.RefreshTokenRepository;
import com.bepmo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.RESTAURANT_OWNER)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
        log.info("New user registered: id={}, email={}", user.getId(), user.getEmail());

        return issueTokenPair(user);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AppException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        return issueTokenPair(user);
    }

    // ── Refresh with reuse detection ──────────────────────────────────────────

    /**
     * Refresh token rotation + reuse detection.
     *
     * Flow:
     * 1. Hash incoming token, lookup DB.
     * 2. If not found → token never issued or already cleaned up → 401.
     * 3. If found but already revoked → REUSE DETECTED.
     *    Revoke ALL tokens of this user (family invalidation) → 401.
     * 4. If expired → 401.
     * 5. Valid: revoke current token, issue new pair.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = hashToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isRevoked()) {
            // Reuse detected — revoke entire family
            log.warn("Refresh token reuse detected for userId={}", stored.getUserId());
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected. Please login again.");
        }

        if (stored.isExpired()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        // Rotate: revoke old token
        stored.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AppException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        return issueTokenPair(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Logout: revoke the specific refresh token.
     * Access token hết hạn tự nhiên sau 15 phút — không blacklist trong MVP.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
        });
        // Không throw nếu token không tồn tại — logout idempotent
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = generateRawToken();
        String hash = hashToken(rawRefreshToken);

        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash)
                .expiresAt(OffsetDateTime.now()
                        .plusSeconds(jwtProperties.getRefreshTokenExpirationMs() / 1000))
                .build();

        refreshTokenRepository.save(rt);

        return AuthResponse.of(accessToken, rawRefreshToken,
                jwtProperties.getAccessTokenExpirationMs());
    }

    /**
     * Tạo refresh token ngẫu nhiên 32 bytes → Base64 URL-safe string.
     * Không phải JWT — không mang thông tin, chỉ là opaque token.
     */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hash trước khi lưu DB.
     * Nếu DB bị leak, attacker không có raw token.
     */
    private String hashToken(String rawToken) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
