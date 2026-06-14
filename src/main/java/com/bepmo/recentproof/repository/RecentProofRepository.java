package com.bepmo.recentproof.repository;

import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecentProofRepository extends JpaRepository<RecentProof, Long> {

    // Score service: latest ACTIVE proof
    Optional<RecentProof> findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(
            Long restaurantId, RecentProofStatus status);

    // Public profile: 3 most recent ACTIVE proofs
    List<RecentProof> findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(
            Long restaurantId, RecentProofStatus status);

    List<RecentProof> findByRestaurantId(Long restaurantId);
}
