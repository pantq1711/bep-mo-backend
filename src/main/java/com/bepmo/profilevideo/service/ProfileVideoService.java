package com.bepmo.profilevideo.service;

import com.bepmo.common.exception.AppException;
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

    @Transactional
    public ProfileVideoResponse upload(Long restaurantId, Long ownerId, UploadVideoRequest request) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);

        // Demote video ACTIVE cùng type (nếu có) thành REPLACED trước khi insert video mới.
        // Bắt buộc làm bước này trước insert, không phải sau — nếu không sẽ đụng
        // partial unique index uq_profile_videos_one_active_per_type ngay khi insert.
        profileVideoRepository.replaceActive(restaurantId, request.type(), VideoStatus.REPLACED, VideoStatus.ACTIVE);

        ProfileVideo video = ProfileVideo.builder()
                .restaurantId(restaurantId)
                .type(request.type())
                .cloudinaryUrl(request.cloudinaryUrl())
                .cloudinaryPublicId(request.cloudinaryPublicId())
                .thumbnailUrl(request.thumbnailUrl())
                .durationSeconds(request.durationSeconds())
                .fileSizeBytes(request.fileSizeBytes())
                .status(VideoStatus.ACTIVE)
                .build();

        profileVideoRepository.save(video);

        // Video ACTIVE ảnh hưởng Completeness score — evict cache để lần đọc sau tính lại
        transparencyScoreService.evictCache(restaurantId);

        return toResponse(video);
    }

    @Transactional
    public void hide(Long restaurantId, Long videoId, Long ownerId) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);
        ProfileVideo video = requireVideoInRestaurant(videoId, restaurantId);
        video.setStatus(VideoStatus.HIDDEN);
        transparencyScoreService.evictCache(restaurantId);
    }

    @Transactional
    public void delete(Long restaurantId, Long videoId, Long ownerId) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);
        ProfileVideo video = requireVideoInRestaurant(videoId, restaurantId);
        // Soft delete — giữ record để audit, không xoá vật lý.
        // Cloudinary file gốc KHÔNG bị xoá ở bước này (backend không giữ Cloudinary API secret
        // cho write access trong luồng client-upload hiện tại) — chấp nhận là known limitation của MVP.
        video.setStatus(VideoStatus.DELETED);
        transparencyScoreService.evictCache(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<ProfileVideoResponse> listActive(Long restaurantId) {
        return profileVideoRepository.findByRestaurantIdAndStatus(restaurantId, VideoStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
