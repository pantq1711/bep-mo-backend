package com.bepmo.profilevideo;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaResourceType;
import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.media.entity.MediaUploadSession;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import com.bepmo.media.service.MediaUploadSessionService;
import com.bepmo.media.service.MediaVerificationService;
import com.bepmo.profilevideo.dto.ProfileVideoDtos.UploadVideoRequest;
import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.profilevideo.service.ProfileVideoFinalizationService;
import com.bepmo.profilevideo.service.ProfileVideoService;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileVideoServiceTest {

    @Mock ProfileVideoRepository profileVideoRepository;
    @Mock RestaurantService restaurantService;
    @Mock TransparencyScoreService transparencyScoreService;
    @Mock MediaUploadSessionService mediaUploadSessionService;
    @Mock MediaVerificationService mediaVerificationService;
    @Mock ProfileVideoFinalizationService finalizationService;

    @InjectMocks ProfileVideoService profileVideoService;

    private Restaurant restaurant;
    private ProfileVideo video;
    private MediaUploadSession session;
    private TrustedMediaMetadata metadata;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        restaurant = Restaurant.builder().id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
        video = ProfileVideo.builder()
                .id(50L).restaurantId(1L).type(VideoType.KITCHEN)
                .cloudinaryUrl("https://cdn/x.mp4").cloudinaryPublicId("pub-1")
                .durationSeconds(20).fileSizeBytes(1000L)
                .mediaUploadSessionId(sessionId)
                .status(VideoStatus.ACTIVE)
                .build();
        session = MediaUploadSession.builder()
                .id(sessionId).ownerId(10L).restaurantId(1L)
                .purpose(MediaUploadPurpose.PROFILE_VIDEO)
                .profileVideoType(VideoType.KITCHEN)
                .resourceType(MediaResourceType.VIDEO)
                .expectedPublicId("bep-mo/test")
                .status(MediaUploadSessionStatus.ISSUED)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();
        metadata = new TrustedMediaMetadata(
                "bep-mo/test", 7L, MediaResourceType.VIDEO, "upload", "mp4",
                1000L, 1280, 720, 20.0, "https://cdn/new.mp4"
        );
    }

    @Test
    @DisplayName("upload: Cloudinary verification xảy ra trước short DB finalization")
    void upload_verifiesBeforeFinalization() {
        when(mediaUploadSessionService.requireAuthorizedForPublish(
                sessionId, 10L, 1L, MediaUploadPurpose.PROFILE_VIDEO)).thenReturn(session);
        when(profileVideoRepository.findByMediaUploadSessionId(sessionId)).thenReturn(Optional.empty());
        when(mediaVerificationService.verify(session, 7L, "sig")).thenReturn(metadata);
        when(finalizationService.finalizeUpload(1L, 10L, sessionId, metadata)).thenReturn(video);

        profileVideoService.upload(1L, 10L, new UploadVideoRequest(sessionId, 7L, "sig"));

        InOrder order = inOrder(mediaVerificationService, finalizationService);
        order.verify(mediaVerificationService).verify(session, 7L, "sig");
        order.verify(finalizationService).finalizeUpload(1L, 10L, sessionId, metadata);
    }

    @Test
    @DisplayName("upload retry: CONSUMED session trả record cũ và bypass Cloudinary verification")
    void upload_consumedRetryBypassesCloudinary() {
        session.setStatus(MediaUploadSessionStatus.CONSUMED);
        when(mediaUploadSessionService.requireAuthorizedForPublish(
                sessionId, 10L, 1L, MediaUploadPurpose.PROFILE_VIDEO)).thenReturn(session);
        when(profileVideoRepository.findByMediaUploadSessionId(sessionId)).thenReturn(Optional.of(video));

        var result = profileVideoService.upload(1L, 10L, new UploadVideoRequest(sessionId, 7L, "sig"));

        assertThat(result.id()).isEqualTo(50L);
        verifyNoInteractions(mediaVerificationService, finalizationService);
    }

    @Test
    @DisplayName("hide: video thuộc quán khác (path variable sai) → 404")
    void hide_videoBelongsToDifferentRestaurant() {
        when(restaurantService.requireOwnedRestaurantForUpdate(2L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> profileVideoService.hide(2L, 50L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("hide: video REPLACED không được chuyển vòng sang HIDDEN")
    void hide_replaced_throwsConflict() {
        video.setStatus(VideoStatus.REPLACED);
        when(restaurantService.requireOwnedRestaurantForUpdate(1L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> profileVideoService.hide(1L, 50L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.REPLACED);
        verify(transparencyScoreService, never()).evictCache(anyLong());
    }

    @Test
    @DisplayName("delete: soft delete → status DELETED, không xoá vật lý, evict cache")
    void delete_softDeletesAndEvictsCache() {
        when(restaurantService.requireOwnedRestaurantForUpdate(1L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));

        profileVideoService.delete(1L, 50L, 10L);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.DELETED);
        verify(profileVideoRepository, never()).delete(any());
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("listActive: chỉ trả về video ACTIVE")
    void listActive_onlyActiveVideos() {
        when(restaurantService.requireViewableRestaurant(1L, null)).thenReturn(restaurant);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE))
                .thenReturn(java.util.List.of(video));

        var result = profileVideoService.listActive(1L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(VideoStatus.ACTIVE);
    }
}
