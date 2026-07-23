package com.bepmo.recentproof.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.recentproof.dto.RecentProofDtos.*;
import com.bepmo.recentproof.entity.MediaKind;
import com.bepmo.recentproof.entity.ProofType;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecentProofService {

    private final RecentProofRepository recentProofRepository;
    private final RestaurantService restaurantService;
    private final TransparencyScoreService transparencyScoreService;

    @Transactional
    public RecentProofResponse create(Long restaurantId, Long ownerId, CreateRecentProofRequest request) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);

        RecentProof proof = RecentProof.builder()
                .restaurantId(restaurantId)
                .proofType(request.proofType())
                .mediaKind(deriveMediaKind(request.proofType()))
                .mediaUrl(request.mediaUrl())
                .cloudinaryPublicId(request.cloudinaryPublicId())
                .note(request.note())
                .status(RecentProofStatus.ACTIVE)
                .build();

        recentProofRepository.save(proof);

        // Proof mới nhất quyết định Freshness score — evict cache ngay
        transparencyScoreService.evictCache(restaurantId);

        return toResponse(proof);
    }

    @Transactional
    public void delete(Long restaurantId, Long proofId, Long ownerId) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);
        RecentProof proof = requireProofInRestaurant(proofId, restaurantId);
        proof.setStatus(RecentProofStatus.DELETED);
        transparencyScoreService.evictCache(restaurantId);
    }

    // Public profile chỉ hiện 3 proof gần nhất — theo đúng scope MVP (không có timeline lịch sử)
    @Transactional(readOnly = true)
    public List<RecentProofResponse> listRecentActive(Long restaurantId) {
        return recentProofRepository.findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(
                        restaurantId, RecentProofStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Compatibility rule mục 5: RECEIVING_VIDEO -> VIDEO, còn lại -> IMAGE.
    // Áp dụng ở application layer song song với CHECK constraint ở DB (double validation).
    private MediaKind deriveMediaKind(ProofType type) {
        return type == ProofType.RECEIVING_VIDEO ? MediaKind.VIDEO : MediaKind.IMAGE;
    }

    private RecentProof requireProofInRestaurant(Long proofId, Long restaurantId) {
        RecentProof proof = recentProofRepository.findById(proofId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Recent proof not found"));

        if (!proof.getRestaurantId().equals(restaurantId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Recent proof not found in this restaurant");
        }

        return proof;
    }

    private RecentProofResponse toResponse(RecentProof p) {
        return new RecentProofResponse(
                p.getId(), p.getRestaurantId(), p.getProofType(), p.getMediaKind(),
                p.getMediaUrl(), p.getNote(), p.getStatus(), p.getUploadedAt()
        );
    }
}
