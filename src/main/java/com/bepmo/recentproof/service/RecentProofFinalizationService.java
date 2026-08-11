package com.bepmo.recentproof.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaResourceType;
import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.media.entity.MediaUploadSession;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import com.bepmo.media.repository.MediaUploadSessionRepository;
import com.bepmo.media.service.MediaUploadSessionService;
import com.bepmo.recentproof.entity.MediaKind;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecentProofFinalizationService {

    private final RestaurantService restaurantService;
    private final MediaUploadSessionRepository sessionRepository;
    private final MediaUploadSessionService sessionService;
    private final RecentProofRepository recentProofRepository;
    private final TransparencyScoreService transparencyScoreService;

    @Transactional
    public RecentProof finalizeUpload(
            Long restaurantId,
            Long ownerId,
            UUID uploadSessionId,
            TrustedMediaMetadata metadata,
            String note
    ) {
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);

        MediaUploadSession session = sessionRepository.findByIdForUpdate(uploadSessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Upload session not found"));
        sessionService.assertBinding(session, ownerId, restaurantId, MediaUploadPurpose.RECENT_PROOF);

        var existing = recentProofRepository.findByMediaUploadSessionId(uploadSessionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (session.getStatus() == MediaUploadSessionStatus.CONSUMED) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is consumed but its proof record is missing");
        }
        if (session.getStatus() == MediaUploadSessionStatus.REJECTED
                || session.getStatus() == MediaUploadSessionStatus.EXPIRED
                || sessionService.isExpired(session)) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is no longer publishable");
        }

        OffsetDateTime now = OffsetDateTime.now();
        session.setStatus(MediaUploadSessionStatus.VALIDATED);
        session.setValidatedAt(now);

        RecentProof proof = RecentProof.builder()
                .restaurantId(restaurantId)
                .proofType(session.getRecentProofType())
                .mediaKind(session.getResourceType() == MediaResourceType.VIDEO ? MediaKind.VIDEO : MediaKind.IMAGE)
                .mediaUrl(metadata.secureUrl())
                .cloudinaryPublicId(metadata.publicId())
                .mediaUploadSessionId(uploadSessionId)
                .note(note)
                .status(RecentProofStatus.ACTIVE)
                .build();
        recentProofRepository.save(proof);

        session.setStatus(MediaUploadSessionStatus.CONSUMED);
        session.setConsumedAt(now);
        transparencyScoreService.evictCache(restaurantId);
        return proof;
    }
}
