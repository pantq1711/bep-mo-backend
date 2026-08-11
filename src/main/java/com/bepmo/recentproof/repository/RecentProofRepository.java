package com.bepmo.recentproof.repository;

import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecentProofRepository extends JpaRepository<RecentProof, Long> {

    // Score service: latest ACTIVE proof
    Optional<RecentProof> findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(
            Long restaurantId, RecentProofStatus status);

    // Public profile: 3 most recent ACTIVE proofs
    List<RecentProof> findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(
            Long restaurantId, RecentProofStatus status);

    List<RecentProof> findByRestaurantId(Long restaurantId);

    Optional<RecentProof> findByMediaUploadSessionId(UUID mediaUploadSessionId);

    @Query("SELECT p.restaurantId FROM RecentProof p WHERE p.id = :id")
    Optional<Long> findRestaurantIdById(@Param("id") Long id);
}
