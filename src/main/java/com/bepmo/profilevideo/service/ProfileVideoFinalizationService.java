package com.bepmo.profilevideo.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.media.entity.MediaUploadSession;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import com.bepmo.media.repository.MediaUploadSessionRepository;
import com.bepmo.media.service.MediaUploadSessionService;
import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
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
public class ProfileVideoFinalizationService {

    private final RestaurantService restaurantService;
    private final MediaUploadSessionRepository sessionRepository;
    private final MediaUploadSessionService sessionService;
    private final ProfileVideoRepository profileVideoRepository;
    private final TransparencyScoreService transparencyScoreService;

    @Transactional
    public ProfileVideo finalizeUpload(
            Long restaurantId,
            Long ownerId,
            UUID uploadSessionId,
            TrustedMediaMetadata metadata
    ) {
        // Lock parent first: preserve the project's per-restaurant serialization protocol.
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);

        MediaUploadSession session = sessionRepository.findByIdForUpdate(uploadSessionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Upload session not found"));
        sessionService.assertBinding(session, ownerId, restaurantId, MediaUploadPurpose.PROFILE_VIDEO);

        var existing = profileVideoRepository.findByMediaUploadSessionId(uploadSessionId);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (session.getStatus() == MediaUploadSessionStatus.CONSUMED) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is consumed but its video record is missing");
        }
        if (session.getStatus() == MediaUploadSessionStatus.REJECTED
                || session.getStatus() == MediaUploadSessionStatus.EXPIRED
                || sessionService.isExpired(session)) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is no longer publishable");
        }

        OffsetDateTime now = OffsetDateTime.now();
        session.setStatus(MediaUploadSessionStatus.VALIDATED);
        session.setValidatedAt(now);

        profileVideoRepository.replaceActive(
                restaurantId,
                session.getProfileVideoType(),
                VideoStatus.REPLACED,
                VideoStatus.ACTIVE
        );

        ProfileVideo video = ProfileVideo.builder()
                .restaurantId(restaurantId)
                .type(session.getProfileVideoType())
                .cloudinaryUrl(metadata.secureUrl())
                .cloudinaryPublicId(metadata.publicId())
                .thumbnailUrl(null)
                .durationSeconds(Math.max(1, (int) Math.ceil(metadata.durationSeconds())))
                .fileSizeBytes(metadata.bytes())
                .mediaUploadSessionId(uploadSessionId)
                .status(VideoStatus.ACTIVE)
                .build();
        profileVideoRepository.save(video);

        session.setStatus(MediaUploadSessionStatus.CONSUMED);
        session.setConsumedAt(now);
        transparencyScoreService.evictCache(restaurantId);
        return video;
    }
}
