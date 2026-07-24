package com.bepmo.profilevideo;

import com.bepmo.common.exception.AppException;
import com.bepmo.profilevideo.dto.ProfileVideoDtos.*;
import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileVideoServiceTest {

    @Mock ProfileVideoRepository profileVideoRepository;
    @Mock RestaurantService restaurantService;
    @Mock TransparencyScoreService transparencyScoreService;

    @InjectMocks ProfileVideoService profileVideoService;

    private Restaurant restaurant;
    private ProfileVideo video;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
        video = ProfileVideo.builder()
                .id(50L).restaurantId(1L).type(VideoType.KITCHEN)
                .cloudinaryUrl("https://cdn/x.mp4").cloudinaryPublicId("pub-1")
                .durationSeconds(20).fileSizeBytes(1000L)
                .status(VideoStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("upload: gọi replaceActive() TRƯỚC khi save video mới cùng type")
    void upload_callsReplaceActiveBeforeSave() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        profileVideoService.upload(1L, 10L, new UploadVideoRequest(
                VideoType.KITCHEN, "https://cdn/new.mp4", "pub-2", null, 15, 2000L));

        InOrder order = inOrder(profileVideoRepository);
        order.verify(profileVideoRepository).replaceActive(1L, VideoType.KITCHEN, VideoStatus.REPLACED, VideoStatus.ACTIVE);
        order.verify(profileVideoRepository).save(any(ProfileVideo.class));
    }

    @Test
    @DisplayName("upload: thành công → evict transparency score cache")
    void upload_evictsScoreCache() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        profileVideoService.upload(1L, 10L, new UploadVideoRequest(
                VideoType.HYGIENE, "https://cdn/new.mp4", "pub-2", null, 15, 2000L));

        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("hide: video thuộc quán khác (path variable sai) → 404")
    void hide_videoBelongsToDifferentRestaurant() {
        when(restaurantService.requireOwnedRestaurant(2L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video)); // video.restaurantId = 1

        assertThatThrownBy(() -> profileVideoService.hide(2L, 50L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("delete: soft delete → status DELETED, không xoá vật lý, evict cache")
    void delete_softDeletesAndEvictsCache() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));

        profileVideoService.delete(1L, 50L, 10L);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.DELETED);
        verify(profileVideoRepository, never()).delete(any());
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("listActive: chỉ trả về video ACTIVE")
    void listActive_onlyActiveVideos() {
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE))
                .thenReturn(java.util.List.of(video));

        var result = profileVideoService.listActive(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(VideoStatus.ACTIVE);
    }
}
