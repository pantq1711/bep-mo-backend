package com.bepmo.user.repository;

import com.bepmo.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoke all active tokens của một user — dùng khi detect reuse hoặc logout all
    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revokedAt = CURRENT_TIMESTAMP
        WHERE rt.userId = :userId AND rt.revokedAt IS NULL
        """)
    int revokeAllByUserId(Long userId);
}
