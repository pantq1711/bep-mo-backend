package com.bepmo.admin;
import com.bepmo.restaurant.repository.RestaurantRepository;
import com.bepmo.admin.service.AdminService;
import com.bepmo.common.exception.AppException;
import com.bepmo.ingredientsource.entity.IngredientSource;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.repository.IngredientSourceRepository;
import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import com.bepmo.user.entity.User;
import com.bepmo.user.entity.UserRole;
import com.bepmo.user.entity.UserStatus;
import com.bepmo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock RestaurantService restaurantService;
    @Mock ProfileVideoRepository profileVideoRepository;
    @Mock IngredientSourceRepository ingredientSourceRepository;
    @Mock RecentProofRepository recentProofRepository;
    @Mock UserRepository userRepository;
    @Mock TransparencyScoreService transparencyScoreService;

    @InjectMocks AdminService adminService;

    // ── Restaurant ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listRestaurants: trả cả ACTIVE và HIDDEN để admin moderation")
    void listRestaurants_returnsAllStatuses() {
        Restaurant active = Restaurant.builder()
                .id(1L).name("Quán A").address("Địa chỉ A").category("Phở")
                .status(RestaurantStatus.ACTIVE).createdAt(OffsetDateTime.now().minusDays(1)).build();
        Restaurant hidden = Restaurant.builder()
                .id(2L).name("Quán B").address("Địa chỉ B").category("Bún")
                .status(RestaurantStatus.HIDDEN).createdAt(OffsetDateTime.now()).build();
        when(restaurantRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(hidden, active)));

        var result = adminService.listRestaurants(0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(r -> r.status())
                .containsExactly(RestaurantStatus.HIDDEN, RestaurantStatus.ACTIVE);
        verify(restaurantRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("hideRestaurant: đặt status HIDDEN")
    void hideRestaurant_setsHidden() {
        Restaurant r = Restaurant.builder().id(1L).status(RestaurantStatus.ACTIVE).build();
        when(restaurantService.lockRestaurantForUpdate(1L)).thenReturn(r);

        adminService.hideRestaurant(1L);

        assertThat(r.getStatus()).isEqualTo(RestaurantStatus.HIDDEN);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("unhideRestaurant: đặt status ACTIVE + evict cache")
    void unhideRestaurant_setsActiveAndEvicts() {
        Restaurant r = Restaurant.builder().id(1L).status(RestaurantStatus.HIDDEN).build();
        when(restaurantService.lockRestaurantForUpdate(1L)).thenReturn(r);

        adminService.unhideRestaurant(1L);

        assertThat(r.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("hideRestaurant: không tồn tại → 404")
    void hideRestaurant_notFound() {
        when(restaurantService.lockRestaurantForUpdate(999L))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        assertThatThrownBy(() -> adminService.hideRestaurant(999L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── ProfileVideo ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("unhideVideo: không có video ACTIVE khác cùng type → cho phép, evict cache")
    void unhideVideo_noConflict_succeeds() {
        ProfileVideo video = ProfileVideo.builder()
                .id(50L).restaurantId(1L).type(VideoType.KITCHEN).status(VideoStatus.HIDDEN).build();
        when(profileVideoRepository.findRestaurantIdById(50L)).thenReturn(Optional.of(1L));
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));
        when(profileVideoRepository.existsByRestaurantIdAndTypeAndStatus(1L, VideoType.KITCHEN, VideoStatus.ACTIVE))
                .thenReturn(false);

        adminService.unhideVideo(50L);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.ACTIVE);
        verify(restaurantService).lockRestaurantForUpdate(1L);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("unhideVideo: đã có video ACTIVE khác cùng type → 409 CONFLICT, không đổi status")
    void unhideVideo_conflictWithExistingActive_throws() {
        ProfileVideo video = ProfileVideo.builder()
                .id(50L).restaurantId(1L).type(VideoType.KITCHEN).status(VideoStatus.HIDDEN).build();
        when(profileVideoRepository.findRestaurantIdById(50L)).thenReturn(Optional.of(1L));
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));
        when(profileVideoRepository.existsByRestaurantIdAndTypeAndStatus(1L, VideoType.KITCHEN, VideoStatus.ACTIVE))
                .thenReturn(true); // owner đã upload video mới thay thế trong lúc bị hide

        assertThatThrownBy(() -> adminService.unhideVideo(50L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.HIDDEN); // giữ nguyên, không unhide
        verify(transparencyScoreService, never()).evictCache(anyLong());
    }

    @Test
    @DisplayName("unhideVideo: không được phục hồi video REPLACED/DELETED thành ACTIVE")
    void unhideVideo_nonHidden_throwsConflict() {
        ProfileVideo video = ProfileVideo.builder()
                .id(51L).restaurantId(1L).type(VideoType.KITCHEN).status(VideoStatus.REPLACED).build();
        when(profileVideoRepository.findRestaurantIdById(51L)).thenReturn(Optional.of(1L));
        when(profileVideoRepository.findById(51L)).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> adminService.unhideVideo(51L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(profileVideoRepository, never())
                .existsByRestaurantIdAndTypeAndStatus(anyLong(), any(), any());
        verify(transparencyScoreService, never()).evictCache(anyLong());
    }

    @Test
    @DisplayName("hideVideo: đặt HIDDEN + evict cache")
    void hideVideo_setsHiddenAndEvicts() {
        ProfileVideo video = ProfileVideo.builder()
                .id(50L).restaurantId(1L).type(VideoType.PREP).status(VideoStatus.ACTIVE).build();
        when(profileVideoRepository.findRestaurantIdById(50L)).thenReturn(Optional.of(1L));
        when(profileVideoRepository.findById(50L)).thenReturn(Optional.of(video));

        adminService.hideVideo(50L);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.HIDDEN);
        verify(restaurantService).lockRestaurantForUpdate(1L);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("hideVideo: REPLACED không được đổi vòng sang HIDDEN")
    void hideVideo_replaced_throwsConflict() {
        ProfileVideo video = ProfileVideo.builder()
                .id(52L).restaurantId(1L).type(VideoType.PREP).status(VideoStatus.REPLACED).build();
        when(profileVideoRepository.findRestaurantIdById(52L)).thenReturn(Optional.of(1L));
        when(profileVideoRepository.findById(52L)).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> adminService.hideVideo(52L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.REPLACED);
        verify(transparencyScoreService, never()).evictCache(anyLong());
    }

    // ── IngredientSource ──────────────────────────────────────────────────────

    @Test
    @DisplayName("hideIngredientSource: đặt HIDDEN + evict cache")
    void hideIngredientSource_setsHiddenAndEvicts() {
        IngredientSource source = IngredientSource.builder()
                .id(200L).restaurantId(1L).status(IngredientSourceStatus.ACTIVE).build();
        when(ingredientSourceRepository.findRestaurantIdById(200L)).thenReturn(Optional.of(1L));
        when(ingredientSourceRepository.findById(200L)).thenReturn(Optional.of(source));

        adminService.hideIngredientSource(200L);

        assertThat(source.getStatus()).isEqualTo(IngredientSourceStatus.HIDDEN);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("unhideIngredientSource: source DELETED không được resurrect")
    void unhideIngredientSource_deleted_throwsConflict() {
        IngredientSource source = IngredientSource.builder()
                .id(201L).restaurantId(1L).status(IngredientSourceStatus.DELETED).build();
        when(ingredientSourceRepository.findRestaurantIdById(201L)).thenReturn(Optional.of(1L));
        when(ingredientSourceRepository.findById(201L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> adminService.unhideIngredientSource(201L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ── RecentProof ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("unhideRecentProof: đặt lại ACTIVE + evict cache")
    void unhideRecentProof_setsActiveAndEvicts() {
        RecentProof proof = RecentProof.builder()
                .id(300L).restaurantId(1L).status(RecentProofStatus.HIDDEN).build();
        when(recentProofRepository.findRestaurantIdById(300L)).thenReturn(Optional.of(1L));
        when(recentProofRepository.findById(300L)).thenReturn(Optional.of(proof));

        adminService.unhideRecentProof(300L);

        assertThat(proof.getStatus()).isEqualTo(RecentProofStatus.ACTIVE);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("unhideRecentProof: proof DELETED không được resurrect")
    void unhideRecentProof_deleted_throwsConflict() {
        RecentProof proof = RecentProof.builder()
                .id(301L).restaurantId(1L).status(RecentProofStatus.DELETED).build();
        when(recentProofRepository.findRestaurantIdById(301L)).thenReturn(Optional.of(1L));
        when(recentProofRepository.findById(301L)).thenReturn(Optional.of(proof));

        assertThatThrownBy(() -> adminService.unhideRecentProof(301L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ── User ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disableUser: đặt status DISABLED")
    void disableUser_setsDisabled() {
        User user = User.builder().id(5L).role(UserRole.RESTAURANT_OWNER).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        adminService.disableUser(5L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    @DisplayName("enableUser: user không tồn tại → 404")
    void enableUser_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.enableUser(999L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
