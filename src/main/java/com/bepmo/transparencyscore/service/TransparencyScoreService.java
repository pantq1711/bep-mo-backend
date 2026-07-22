package com.bepmo.transparencyscore.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.repository.IngredientSourceRepository;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.restaurant.repository.RestaurantRepository;
import com.bepmo.transparencyscore.dto.TransparencyScoreDtos.TransparencyScoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Transparency Score — tính on-demand, KHÔNG lưu bảng riêng trong DB (đúng thiết kế mục 5).
 *
 * Completeness (80đ): ingredient source ACTIVE +15, video INGREDIENT_RECEIVING +20,
 * KITCHEN +20, HYGIENE +15, PREP +10.
 * Freshness (20đ): proof ACTIVE mới nhất ≤7 ngày +20, 8–14 ngày +10, >14 ngày hoặc
 * không có +0.
 *
 * Cache-aside qua Redis, key "score:restaurant:{id}", TTL động theo mốc freshness
 * (7/14 ngày kể từ proof mới nhất) + jitter ±5 phút chống cache stampede khi nhiều
 * quán cùng hết hạn cache tại cùng thời điểm.
 */
@Service
@RequiredArgsConstructor
public class TransparencyScoreService {

    private static final String CACHE_KEY_PREFIX = "score:restaurant:";
    private static final int MAX_SCORE = 100;

    // TTL mặc định khi proof mới nhất đã qua mốc freshness cuối (>14 ngày) hoặc chưa có proof —
    // không còn mốc thời gian nào sắp tới làm đổi Freshness score, chỉ cần TTL vừa phải để dữ liệu
    // không bị cache vĩnh viễn (phòng trường hợp evictCache bị bỏ sót ở chỗ nào đó).
    private static final long DEFAULT_TTL_SECONDS = Duration.ofHours(6).toSeconds();
    private static final long JITTER_SECONDS = Duration.ofMinutes(5).toSeconds();
    private static final long SEVEN_DAYS_SECONDS = Duration.ofDays(7).toSeconds();
    private static final long FOURTEEN_DAYS_SECONDS = Duration.ofDays(14).toSeconds();

    private static final Map<VideoType, Integer> VIDEO_WEIGHTS = Map.of(
            VideoType.INGREDIENT_RECEIVING, 20,
            VideoType.KITCHEN, 20,
            VideoType.HYGIENE, 15,
            VideoType.PREP, 10
    );
    private static final int INGREDIENT_SOURCE_WEIGHT = 15;
    private static final int FRESHNESS_WITHIN_7_DAYS = 20;
    private static final int FRESHNESS_WITHIN_14_DAYS = 10;

    private final StringRedisTemplate redisTemplate;
    private final RestaurantRepository restaurantRepository;
    private final IngredientSourceRepository ingredientSourceRepository;
    private final ProfileVideoRepository profileVideoRepository;
    private final RecentProofRepository recentProofRepository;

    @Transactional(readOnly = true)
    public TransparencyScoreResponse getScore(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Restaurant not found");
        }

        String key = cacheKey(restaurantId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return new TransparencyScoreResponse(restaurantId, Integer.parseInt(cached), MAX_SCORE);
        }

        int score = calculate(restaurantId);
        long ttl = computeTtlSeconds(restaurantId);
        redisTemplate.opsForValue().set(key, String.valueOf(score), Duration.ofSeconds(ttl));

        return new TransparencyScoreResponse(restaurantId, score, MAX_SCORE);
    }

    // Gọi sau khi commit mọi thao tác ghi vào profile_videos / ingredient_sources / recent_proofs
    public void evictCache(Long restaurantId) {
        redisTemplate.delete(cacheKey(restaurantId));
    }

    // ── Calculation ───────────────────────────────────────────────────────────

    private int calculate(Long restaurantId) {
        return calculateCompleteness(restaurantId) + calculateFreshness(restaurantId);
    }

    private int calculateCompleteness(Long restaurantId) {
        int score = 0;

        if (ingredientSourceRepository.existsByRestaurantIdAndStatus(restaurantId, IngredientSourceStatus.ACTIVE)) {
            score += INGREDIENT_SOURCE_WEIGHT;
        }

        // 1 query lấy hết video ACTIVE của quán, group theo type trong memory —
        // tránh bắn 4 query riêng (1 cho mỗi VideoType) mỗi lần cache miss.
        var activeTypes = profileVideoRepository.findByRestaurantIdAndStatus(restaurantId, VideoStatus.ACTIVE)
                .stream()
                .map(video -> video.getType())
                .collect(java.util.stream.Collectors.toSet());

        for (Map.Entry<VideoType, Integer> entry : VIDEO_WEIGHTS.entrySet()) {
            if (activeTypes.contains(entry.getKey())) {
                score += entry.getValue();
            }
        }

        return score;
    }

    private int calculateFreshness(Long restaurantId) {
        return latestActiveProof(restaurantId)
                .map(proof -> {
                    long secondsSince = Duration.between(proof.getUploadedAt(), OffsetDateTime.now()).getSeconds();
                    if (secondsSince <= SEVEN_DAYS_SECONDS) return FRESHNESS_WITHIN_7_DAYS;
                    if (secondsSince <= FOURTEEN_DAYS_SECONDS) return FRESHNESS_WITHIN_14_DAYS;
                    return 0;
                })
                .orElse(0);
    }

    // ── TTL ───────────────────────────────────────────────────────────────────

    private long computeTtlSeconds(Long restaurantId) {
        long baseTtl = latestActiveProof(restaurantId)
                .map(proof -> {
                    long secondsSince = Duration.between(proof.getUploadedAt(), OffsetDateTime.now()).getSeconds();
                    if (secondsSince < SEVEN_DAYS_SECONDS) {
                        return SEVEN_DAYS_SECONDS - secondsSince;
                    }
                    if (secondsSince < FOURTEEN_DAYS_SECONDS) {
                        return FOURTEEN_DAYS_SECONDS - secondsSince;
                    }
                    return DEFAULT_TTL_SECONDS;
                })
                .orElse(DEFAULT_TTL_SECONDS);

        long jitter = ThreadLocalRandom.current().nextLong(-JITTER_SECONDS, JITTER_SECONDS + 1);
        // Clamp tối thiểu 60s — tránh TTL âm hoặc gần 0 khi baseTtl nhỏ mà jitter âm mạnh,
        // gây cache bị evict gần như ngay lập tức (mất tác dụng cache-aside).
        return Math.max(60, baseTtl + jitter);
    }

    private java.util.Optional<RecentProof> latestActiveProof(Long restaurantId) {
        return recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(
                restaurantId, RecentProofStatus.ACTIVE);
    }

    private String cacheKey(Long restaurantId) {
        return CACHE_KEY_PREFIX + restaurantId;
    }
}
