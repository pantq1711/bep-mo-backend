package com.bepmo.auth;

import com.bepmo.auth.dto.AuthDtos.*;
import com.bepmo.auth.service.AuthService;
import com.bepmo.common.exception.AppException;
import com.bepmo.config.JwtProperties;
import com.bepmo.security.util.JwtUtil;
import com.bepmo.user.entity.RefreshToken;
import com.bepmo.user.entity.User;
import com.bepmo.user.entity.UserRole;
import com.bepmo.user.entity.UserStatus;
import com.bepmo.user.repository.RefreshTokenRepository;
import com.bepmo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock JwtProperties jwtProperties;

    @InjectMocks AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .email("owner@bepmo.com")
                .passwordHash("$2a$hash")
                .role(UserRole.RESTAURANT_OWNER)
                .status(UserStatus.ACTIVE)
                .build();

        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900_000L);
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604_800_000L);
        when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn("access-token-mock");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: success → returns token pair")
    void register_success() {
        when(userRepository.existsByEmail("owner@bepmo.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenReturn(activeUser);

        AuthResponse response = authService.register(
                new RegisterRequest("owner@bepmo.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access-token-mock");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: duplicate email → 409 CONFLICT")
    void register_duplicateEmail() {
        when(userRepository.existsByEmail("owner@bepmo.com")).thenReturn(true);

        assertThatThrownBy(() ->
            authService.register(new RegisterRequest("owner@bepmo.com", "password123")))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: wrong password → 401 UNAUTHORIZED")
    void login_wrongPassword() {
        when(userRepository.findByEmail("owner@bepmo.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("owner@bepmo.com", "wrong")))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("login: disabled account → 403 FORBIDDEN")
    void login_disabledAccount() {
        User disabled = User.builder()
                .id(2L).email("d@bepmo.com").passwordHash("hash")
                .role(UserRole.RESTAURANT_OWNER).status(UserStatus.DISABLED).build();

        when(userRepository.findByEmail("d@bepmo.com")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("d@bepmo.com", "pass")))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh: valid token → rotates token, returns new pair")
    void refresh_validToken() {
        RefreshToken stored = RefreshToken.builder()
                .id(1L).userId(1L)
                .tokenHash("will-be-computed-via-hash")
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        // Bất kỳ hash nào → return stored token
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        AuthResponse response = authService.refresh(new RefreshRequest("any-raw-token"));

        assertThat(response.accessToken()).isEqualTo("access-token-mock");
        assertThat(stored.getRevokedAt()).isNotNull(); // old token revoked
        verify(refreshTokenRepository, times(2)).save(any()); // revoke old + save new
    }

    @Test
    @DisplayName("refresh: reuse detected (already revoked) → revoke all family, 401")
    void refresh_reuseDetected() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(1L).userId(1L)
                .tokenHash("hash")
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revokedAt(OffsetDateTime.now().minusHours(1)) // already revoked
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() ->
            authService.refresh(new RefreshRequest("reused-token")))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    @DisplayName("refresh: expired token → 401")
    void refresh_expiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .id(1L).userId(1L).tokenHash("hash")
                .expiresAt(OffsetDateTime.now().minusHours(1)) // past
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() ->
            authService.refresh(new RefreshRequest("expired-token")))
            .isInstanceOf(AppException.class)
            .satisfies(ex -> assertThat(((AppException) ex).getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: valid token → revoked")
    void logout_revokesToken() {
        RefreshToken active = RefreshToken.builder()
                .id(1L).userId(1L).tokenHash("hash")
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(active));

        authService.logout("any-raw-token");

        assertThat(active.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(active);
    }

    @Test
    @DisplayName("logout: token not found → no exception (idempotent)")
    void logout_tokenNotFound_noException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout("ghost-token"))
                .doesNotThrowAnyException();
    }
}
