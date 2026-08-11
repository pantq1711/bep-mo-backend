package com.bepmo.media.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.dto.MediaUploadDtos.CreateUploadSessionRequest;
import com.bepmo.media.dto.MediaUploadDtos.UploadSessionResponse;
import com.bepmo.media.entity.*;
import com.bepmo.media.gateway.CloudinaryMediaGateway;
import com.bepmo.media.gateway.SignedUploadParameters;
import com.bepmo.media.repository.MediaUploadSessionRepository;
import com.bepmo.recentproof.entity.ProofType;
import com.bepmo.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaUploadSessionService {

    private final MediaUploadSessionRepository sessionRepository;
    private final RestaurantService restaurantService;
    private final CloudinaryMediaGateway cloudinaryGateway;
    private final MediaUploadSessionStateService stateService;

    @Value("${app.cloudinary.upload-session-ttl-seconds:600}")
    private long uploadSessionTtlSeconds;

    @Transactional
    public UploadSessionResponse issue(Long ownerId, CreateUploadSessionRequest request) {
        restaurantService.requireOwnedRestaurant(request.restaurantId(), ownerId);
        validatePurposeBinding(request);

        MediaResourceType resourceType = deriveResourceType(request);
        UUID sessionId = UUID.randomUUID();
        String publicId = buildPublicId(request.restaurantId(), request.purpose(), sessionId);
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusSeconds(Math.max(60, uploadSessionTtlSeconds));

        MediaUploadSession session = MediaUploadSession.builder()
                .id(sessionId)
                .ownerId(ownerId)
                .restaurantId(request.restaurantId())
                .purpose(request.purpose())
                .profileVideoType(request.profileVideoType())
                .recentProofType(request.recentProofType())
                .resourceType(resourceType)
                .expectedPublicId(publicId)
                .status(MediaUploadSessionStatus.ISSUED)
                .expiresAt(expiresAt)
                .build();
        sessionRepository.save(session);

        SignedUploadParameters signed = cloudinaryGateway.signBrowserUpload(publicId, resourceType);
        return new UploadSessionResponse(
                sessionId,
                signed.uploadUrl(),
                signed.cloudName(),
                signed.apiKey(),
                signed.timestamp(),
                signed.signature(),
                signed.publicId(),
                signed.resourceType(),
                signed.overwrite(),
                signed.uploadPreset(),
                expiresAt
        );
    }

    @Transactional(readOnly = true)
    public MediaUploadSession requireAuthorizedForPublish(
            UUID sessionId,
            Long ownerId,
            Long restaurantId,
            MediaUploadPurpose expectedPurpose
    ) {
        MediaUploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Upload session not found"));
        assertBinding(session, ownerId, restaurantId, expectedPurpose);

        // A successfully consumed capability stays idempotent even after its original TTL.
        if (session.getStatus() == MediaUploadSessionStatus.CONSUMED) {
            return session;
        }
        if (session.getStatus() == MediaUploadSessionStatus.REJECTED) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session was rejected; issue a new session");
        }
        if (session.getStatus() == MediaUploadSessionStatus.EXPIRED || isExpired(session)) {
            stateService.expireIfOpen(sessionId);
            throw new AppException(HttpStatus.CONFLICT, "Upload session has expired; issue a new session");
        }
        return session;
    }

    public void assertBinding(
            MediaUploadSession session,
            Long ownerId,
            Long restaurantId,
            MediaUploadPurpose expectedPurpose
    ) {
        if (ownerId == null || !session.getOwnerId().equals(ownerId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Upload session does not belong to this owner");
        }
        if (!session.getRestaurantId().equals(restaurantId)) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is bound to another restaurant");
        }
        if (session.getPurpose() != expectedPurpose) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session purpose does not match this endpoint");
        }
    }

    public boolean isExpired(MediaUploadSession session) {
        return !OffsetDateTime.now().isBefore(session.getExpiresAt());
    }

    private void validatePurposeBinding(CreateUploadSessionRequest request) {
        switch (request.purpose()) {
            case PROFILE_VIDEO -> {
                if (request.profileVideoType() == null || request.recentProofType() != null) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "PROFILE_VIDEO requires profileVideoType and forbids recentProofType"
                    );
                }
            }
            case RECENT_PROOF -> {
                if (request.recentProofType() == null || request.profileVideoType() != null) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "RECENT_PROOF requires recentProofType and forbids profileVideoType"
                    );
                }
            }
        }
    }

    private MediaResourceType deriveResourceType(CreateUploadSessionRequest request) {
        if (request.purpose() == MediaUploadPurpose.PROFILE_VIDEO) {
            return MediaResourceType.VIDEO;
        }
        return request.recentProofType() == ProofType.RECEIVING_VIDEO
                ? MediaResourceType.VIDEO
                : MediaResourceType.IMAGE;
    }

    private String buildPublicId(Long restaurantId, MediaUploadPurpose purpose, UUID sessionId) {
        String purposeSegment = purpose == MediaUploadPurpose.PROFILE_VIDEO ? "profile-videos" : "recent-proofs";
        return "bep-mo/restaurants/" + restaurantId + "/" + purposeSegment + "/" + sessionId;
    }

}
