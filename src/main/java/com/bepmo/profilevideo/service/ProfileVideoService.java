package com.bepmo.profilevideo.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import com.bepmo.media.service.MediaUploadSessionService;
import com.bepmo.media.service.MediaVerificationService;
import com.bepmo.profilevideo.dto.ProfileVideoDtos.*;
import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileVideoService {

    private final ProfileVideoRepository profileVideoRepository;
    private final RestaurantService restaurantService;
    private final TransparencyScoreService transparencyScoreService;
    private final MediaUploadSessionService mediaUploadSessionService;
    private final MediaVerificationService mediaVerificationService;
    private final ProfileVideoFinalizationService finalizationService;

    /**
     * Orchestration is intentionally NOT transactional. Cloudinary response verification and
     * Admin API lookup happen here before ProfileVideoFinalizationService opens the short DB tx.
     */
    public ProfileVideoResponse upload(Long restaurantId, Long ownerId, UploadVideoRequest request) {
        var session = mediaUploadSessionService.requireAuthorizedForPublish(
                request.uploadSessionId(), ownerId, restaurantId, MediaUploadPurpose.PROFILE_VIDEO
        );

        // Lost HTTP response / ordinary retry: return the already committed row without another
        // Cloudinary Admin API call. The unique DB column is also the idempotency key.
        var existing = profileVideoRepository.findByMediaUploadSessionId(request.uploadSessionId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        if (session.getStatus() == MediaUploadSessionStatus.CONSUMED) {
            throw new AppException(HttpStatus.CONFLICT, "Upload session is consumed but its video record is missing");
        }

        TrustedMediaMetadata metadata = mediaVerificationService.verify(
                session, request.version(), request.responseSignature()
        );
        ProfileVideo video = finalizationService.finalizeUpload(
                restaurantId, ownerId, request.uploadSessionId(), metadata
        );
        return toResponse(video);
    }

    @Transactional
    public void hide(Long restaurantId, Long videoId, Long ownerId) {
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);
        ProfileVideo video = requireVideoInRestaurant(videoId, restaurantId);
        if (video.getStatus() == VideoStatus.HIDDEN) return;
        if (video.getStatus() != VideoStatus.ACTIVE) {
            throw new AppException(HttpStatus.CONFLICT, "Only an active video can be hidden");
        }
        video.setStatus(VideoStatus.HIDDEN);
        transparencyScoreService.evictCache(restaurantId);
    }

    @Transactional
    public void delete(Long restaurantId, Long videoId, Long ownerId) {
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);
        ProfileVideo video = requireVideoInRestaurant(videoId, restaurantId);
        if (video.getStatus() == VideoStatus.DELETED) return;
        // Soft delete only. Cloudinary destroy/orphan reconciliation is outside the demo critical path.
        video.setStatus(VideoStatus.DELETED);
        transparencyScoreService.evictCache(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<ProfileVideoResponse> listActive(Long restaurantId, Long currentUserId) {
        restaurantService.requireViewableRestaurant(restaurantId, currentUserId);
        return profileVideoRepository.findByRestaurantIdAndStatus(restaurantId, VideoStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    private ProfileVideo requireVideoInRestaurant(Long videoId, Long restaurantId) {
        ProfileVideo video = profileVideoRepository.findById(videoId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Video not found"));
        if (!video.getRestaurantId().equals(restaurantId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Video not found in this restaurant");
        }
        return video;
    }

    private ProfileVideoResponse toResponse(ProfileVideo v) {
        return new ProfileVideoResponse(
                v.getId(), v.getRestaurantId(), v.getType(), v.getCloudinaryUrl(),
                v.getThumbnailUrl(), v.getDurationSeconds(), v.getStatus(), v.getCreatedAt()
        );
    }
}
