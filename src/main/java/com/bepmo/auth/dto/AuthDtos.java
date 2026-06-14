package com.bepmo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// ── Request DTOs ──────────────────────────────────────────────────────────────

public class AuthDtos {

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password
    ) {}

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────────

    public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn  // seconds
    ) {
        public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs) {
            return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000);
        }
    }

    public record MessageResponse(String message) {}
}
