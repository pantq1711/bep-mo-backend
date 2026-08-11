package com.bepmo.transparencyscore;

import com.bepmo.common.exception.AppException;
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
import com.bepmo.transparencyscore.dto.TransparencyScoreDtos.TransparencyScoreResponse;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransparencyScoreServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RestaurantService restaurantService;
    @Mock IngredientSourceRepository ingredientSourceRepository;
    @Mock ProfileVideoRepository profileVideoRepository;
    @Mock RecentProofRepository recentProofRepository;

    @InjectMocks TransparencyScoreService transparencyScoreService;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = Restaurant.builder()
                .id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
        lenient().when(restaurantService.requireViewableRestaurant(1L, null)).thenReturn(restaurant);
        lenient().when(restaurantService.requireViewableRestaurantForUpdate(1L, null)).thenReturn(restaurant);
    }

    @Test
    @DisplayName("getScore: quán không tồn tại → 404, không đụng Redis")
    void getScore_restaurantNotFound() {
        when(restaurantService.requireViewableRestaurant(999L, null))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        assertThatThrownBy(() -> transparencyScoreService.getScore(999L, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("getScore: cache hit → trả thẳng giá trị cached, KHÔNG tính lại")
    void getScore_cacheHit_returnsWithoutRecalculating() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn("55");

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(55);
        assertThat(result.maxScore()).isEqualTo(100);
        verifyNoInteractions(ingredientSourceRepository, profileVideoRepository, recentProofRepository);
        verify(restaurantService, never()).requireViewableRestaurantForUpdate(1L, null);
    }

    @Test
    @DisplayName("getScore: cache miss ban đầu nhưng có cache sau khi lấy DB lock → dùng cache, không tính lại")
    void getScore_cacheFilledWhileWaitingForLock_usesSecondCacheCheck() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null, "65");

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(65);
        verify(restaurantService).requireViewableRestaurantForUpdate(1L, null);
        verifyNoInteractions(ingredientSourceRepository, profileVideoRepository, recentProofRepository);
    }

    @Test
    @DisplayName("getScore: cache miss, quán chưa có gì → score = 0")
    void getScore_cacheMiss_emptyRestaurant_scoreZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(false);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE))
                .thenReturn(List.of());
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.empty());

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(0);
    }

    @Test
    @DisplayName("getScore: cache miss, đủ 4 video + ingredient source + proof mới ≤7 ngày → 100 điểm")
    void getScore_cacheMiss_fullCompleteness_freshProof_maxScore() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(true); // +15

        List<ProfileVideo> activeVideos = List.of(
                video(VideoType.INGREDIENT_RECEIVING), // +20
                video(VideoType.KITCHEN),               // +20
                video(VideoType.HYGIENE),               // +15
                video(VideoType.PREP)                   // +10
        );
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE)).thenReturn(activeVideos);

        RecentProof freshProof = RecentProof.builder()
                .uploadedAt(OffsetDateTime.now().minusDays(2)).build(); // <=7 ngày -> +20
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.of(freshProof));

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(100); // 15+20+20+15+10 + 20 = 100
        verify(recentProofRepository, times(1))
                .findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE);
    }

    @Test
    @DisplayName("getScore: source + KITCHEN + HYGIENE + proof 10 ngày → 60 điểm")
    void getScore_partialDemoProfile_scoreSixty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(true); // +15
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE)).thenReturn(List.of(
                video(VideoType.KITCHEN), // +20
                video(VideoType.HYGIENE) // +15
        ));
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.of(RecentProof.builder()
                        .uploadedAt(OffsetDateTime.now().minusDays(10)).build())); // +10

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(60);
    }

    @Test
    @DisplayName("getScore: proof 10 ngày trước → Freshness +10 (khoảng 8-14 ngày)")
    void getScore_freshness_between8And14Days() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(false);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE)).thenReturn(List.of());

        RecentProof proof10DaysAgo = RecentProof.builder()
                .uploadedAt(OffsetDateTime.now().minusDays(10)).build();
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.of(proof10DaysAgo));

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(10);
    }

    @Test
    @DisplayName("getScore: proof >14 ngày → Freshness +0")
    void getScore_freshness_after14Days() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(false);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE)).thenReturn(List.of());

        RecentProof oldProof = RecentProof.builder()
                .uploadedAt(OffsetDateTime.now().minusDays(20)).build();
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.of(oldProof));

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(0);
    }

    @Test
    @DisplayName("getScore: Redis unavailable khi đọc cache → tính từ DB và vẫn trả score")
    void getScore_redisReadFailure_fallsBackToDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1"))
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(false);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE))
                .thenReturn(List.of());
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.empty());

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(0);
        verify(restaurantService).requireViewableRestaurantForUpdate(1L, null);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("getScore: Redis lỗi khi ghi cache → vẫn trả score đã tính từ DB")
    void getScore_redisWriteFailure_returnsCalculatedScore() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(false);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE))
                .thenReturn(List.of());
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.empty());
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOperations).set(eq("score:restaurant:1"), eq("0"), any(Duration.class));

        TransparencyScoreResponse result = transparencyScoreService.getScore(1L, null);

        assertThat(result.score()).isEqualTo(0);
    }

    @Test
    @DisplayName("getScore: cache miss → ghi lại cache với TTL dương (có jitter)")
    void getScore_cacheMiss_setsCacheWithPositiveTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("score:restaurant:1")).thenReturn(null);
        when(ingredientSourceRepository.existsByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(false);
        when(profileVideoRepository.findByRestaurantIdAndStatus(1L, VideoStatus.ACTIVE)).thenReturn(List.of());
        when(recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(Optional.empty());

        transparencyScoreService.getScore(1L, null);

        verify(valueOperations).set(eq("score:restaurant:1"), eq("0"), argThat((Duration d) -> d.getSeconds() > 0));
    }

    @Test
    @DisplayName("evictCache: xoá đúng key theo restaurantId")
    void evictCache_deletesCorrectKey() {
        transparencyScoreService.evictCache(1L);

        verify(redisTemplate).delete("score:restaurant:1");
    }

    @Test
    @DisplayName("evictCache: trong transaction → không gọi Redis trước commit, chỉ delete afterCommit")
    void evictCache_insideTransaction_deletesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            transparencyScoreService.evictCache(1L);

            verify(redisTemplate, never()).delete("score:restaurant:1");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(redisTemplate, times(1)).delete("score:restaurant:1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("evictCache: Redis unavailable → không propagate lỗi cache sang business flow")
    void evictCache_redisFailure_doesNotThrow() {
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redisTemplate).delete("score:restaurant:1");

        assertThatCode(() -> transparencyScoreService.evictCache(1L))
                .doesNotThrowAnyException();
    }

    private ProfileVideo video(VideoType type) {
        return ProfileVideo.builder().restaurantId(1L).type(type).status(VideoStatus.ACTIVE).build();
    }
}
