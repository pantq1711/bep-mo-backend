package com.bepmo.recentproof.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import com.bepmo.media.service.MediaUploadSessionService;
import com.bepmo.media.service.MediaVerificationService;
import com.bepmo.recentproof.dto.RecentProofDtos.*;
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
    private final MediaUploadSessionService mediaUploadSessionService;
    private final MediaVerificationService mediaVerificationService;
    private final RecentProofFinalizationService finalizationService;

    /** Cloudinary external I/O is deliberately outside the finalization DB transaction. */
    public RecentProofResponse create(Long restaurantId, Long ownerId, CreateRecentProofRequest request) {
        var session = mediaUploadSessionService.requireAuthorizedForPublish(
                request.uploadSessionId(), ownerId, restaurantId, MediaUploadPurpose.RECENT_PROOF
        );

        var existing = recentProofRepository.findByMediaUploadSessionId(request.uploadSessionId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        if (session.getStatus() == MediaUploadSessionStatus.CONSUMED) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is consumed but its proof record is missing");
        }

        TrustedMediaMetadata metadata = mediaVerificationService.verify(
                session, request.version(), request.responseSignature()
        );
        RecentProof proof = finalizationService.finalizeUpload(
                restaurantId, ownerId, request.uploadSessionId(), metadata, request.note()
        );
        return toResponse(proof);
    }

    @Transactional
    public void delete(Long restaurantId, Long proofId, Long ownerId) {
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);
        RecentProof proof = requireProofInRestaurant(proofId, restaurantId);
        proof.setStatus(RecentProofStatus.DELETED);
        transparencyScoreService.evictCache(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<RecentProofResponse> listRecentActive(Long restaurantId, Long currentUserId) {
        restaurantService.requireViewableRestaurant(restaurantId, currentUserId);
        return recentProofRepository.findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(
                        restaurantId, RecentProofStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
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
