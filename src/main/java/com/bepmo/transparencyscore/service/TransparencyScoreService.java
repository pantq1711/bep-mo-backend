package com.bepmo.transparencyscore.service;

import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.repository.IngredientSourceRepository;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.dto.TransparencyScoreDtos.TransparencyScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * (7/14 ngày kể từ proof mới nhất). Với TTL hướng tới mốc thay đổi điểm, jitter chỉ
 * dịch expiry SỚM tối đa 5 phút để không bao giờ giữ điểm cũ quá boundary; TTL mặc định
 * (không còn boundary) vẫn dùng jitter ±5 phút.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransparencyScoreService {

    private static final String CACHE_KEY_PREFIX = "score:restaurant:";
    private static final int MAX_SCORE = 100;

    // TTL mặc định khi proof mới nhất đã qua mốc freshness cuối (>14 ngày) hoặc chưa có proof —
    // không còn mốc thời gian nào sắp tới làm đổi Freshness score, chỉ cần TTL vừa phải để dữ liệu
    // không bị cache vĩnh viễn (phòng trường hợp evictCache bị bỏ sót ở chỗ nào đó).
    private static final long DEFAULT_TTL_SECONDS = Duration.ofHours(6).toSeconds();
    private static final long JITTER_SECONDS = Duration.ofMinutes(5).toSeconds();

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
    private final RestaurantService restaurantService;
    private final IngredientSourceRepository ingredientSourceRepository;
    private final ProfileVideoRepository profileVideoRepository;
    private final RecentProofRepository recentProofRepository;

    @Transactional
    public TransparencyScoreResponse getScore(Long restaurantId, Long currentUserId) {
        // Visibility is checked before touching Redis so HIDDEN restaurants never leak a
        // cached score to anonymous/non-owner callers.
        restaurantService.requireViewableRestaurant(restaurantId, currentUserId);

        String key = cacheKey(restaurantId);
        boolean redisAvailable = true;
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException ex) {
            redisAvailable = false;
            log.warn("Transparency Score cache read failed for restaurant {}. Falling back to DB calculation: {}",
                    restaurantId, ex.getMessage());
        }
        if (cached != null) {
            return cachedResponse(restaurantId, cached);
        }

        // Cache miss: serialize this calculation with every score-affecting writer for the
        // same restaurant. Re-check Redis after acquiring the lock because another cache
        // miss may have populated it while this request was waiting. If Redis was already
        // unavailable on the first read, skip further cache calls for this request and use DB.
        restaurantService.requireViewableRestaurantForUpdate(restaurantId, currentUserId);
        if (redisAvailable) {
            try {
                cached = redisTemplate.opsForValue().get(key);
            } catch (DataAccessException ex) {
                redisAvailable = false;
                log.warn("Transparency Score cache re-check failed for restaurant {}. Continuing with DB calculation: {}",
                        restaurantId, ex.getMessage());
            }
            if (cached != null) {
                return cachedResponse(restaurantId, cached);
            }
        }

        ScoreCalculation calculation = calculate(restaurantId, OffsetDateTime.now());
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(
                        key,
                        String.valueOf(calculation.score()),
                        Duration.ofSeconds(calculation.ttlSeconds())
                );
            } catch (DataAccessException ex) {
                log.warn("Transparency Score cache write failed for restaurant {}. Returning DB-calculated score: {}",
                        restaurantId, ex.getMessage());
            }
        }

        return new TransparencyScoreResponse(restaurantId, calculation.score(), MAX_SCORE);
    }

    private TransparencyScoreResponse cachedResponse(Long restaurantId, String cached) {
        return new TransparencyScoreResponse(restaurantId, Integer.parseInt(cached), MAX_SCORE);
    }

    /**
     * Score-affecting writers call this while holding the per-restaurant DB row lock.
     *
     * When a DB transaction is active, defer Redis I/O until AFTER COMMIT. This keeps cache
     * availability outside the business transaction boundary: a Redis outage must never roll
     * back a successfully validated restaurant/video/proof mutation. Cache-miss score readers
     * also acquire the same restaurant row lock before calculating and writing a new value, so
     * the after-commit delete removes any value produced from the pre-commit state.
     *
     * Outside a DB transaction, evict immediately. Redis failures are logged and treated as a
     * cache degradation only; PostgreSQL remains the source of truth.
     */
    public void evictCache(Long restaurantId) {
        Runnable evict = () -> safeDeleteCache(restaurantId);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
            return;
        }

        evict.run();
    }

    private void safeDeleteCache(Long restaurantId) {
        try {
            redisTemplate.delete(cacheKey(restaurantId));
        } catch (DataAccessException ex) {
            log.warn("Transparency Score cache eviction failed for restaurant {}. DB mutation remains committed: {}",
                    restaurantId, ex.getMessage());
        }
    }

    // ── Calculation ───────────────────────────────────────────────────────────

    /**
     * Compute score and TTL from one logical snapshot: latest proof is queried once and the
     * same `now` instant is used for both Freshness and cache expiry. This avoids combining a
     * score calculated from proof A with a TTL calculated from proof B around concurrent writes
     * or the 7/14-day boundaries.
     */
    private ScoreCalculation calculate(Long restaurantId, OffsetDateTime now) {
        int completeness = calculateCompleteness(restaurantId);
        var latestProof = latestActiveProof(restaurantId);
        int freshness = calculateFreshness(latestProof, now);
        long ttl = computeTtlSeconds(latestProof, now);
        return new ScoreCalculation(completeness + freshness, ttl);
    }

    private int calculateCompleteness(Long restaurantId) {
        int score = 0;

        if (ingredientSourceRepository.existsByRestaurantIdAndStatus(restaurantId, IngredientSourceStatus.ACTIVE)) {
            score += INGREDIENT_SOURCE_WEIGHT;
        }

        // 1 query lấy hết video ACTIVE của quán, group theo type trong memory —
        // tránh bắn 4 query riêng (1 cho mỗi VideoType) mỗi lần cache miss.
        // Set cũng bảo đảm không double-count nếu dữ liệu DB từng bị corrupt/import sai.
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

    private int calculateFreshness(java.util.Optional<RecentProof> latestProof, OffsetDateTime now) {
        if (latestProof.isEmpty()) return 0;

        OffsetDateTime uploadedAt = effectiveUploadedAt(latestProof.get(), now);
        if (!now.isAfter(uploadedAt.plusDays(7))) return FRESHNESS_WITHIN_7_DAYS;
        if (!now.isAfter(uploadedAt.plusDays(14))) return FRESHNESS_WITHIN_14_DAYS;
        return 0;
    }

    // ── TTL ───────────────────────────────────────────────────────────────────

    private long computeTtlSeconds(java.util.Optional<RecentProof> latestProof, OffsetDateTime now) {
        if (latestProof.isEmpty()) {
            return defaultTtlWithJitter();
        }

        OffsetDateTime uploadedAt = effectiveUploadedAt(latestProof.get(), now);
        OffsetDateTime sevenDayBoundary = uploadedAt.plusDays(7);
        OffsetDateTime fourteenDayBoundary = uploadedAt.plusDays(14);

        if (!now.isAfter(sevenDayBoundary)) {
            return ttlUntilBoundary(now, sevenDayBoundary);
        }
        if (!now.isAfter(fourteenDayBoundary)) {
            return ttlUntilBoundary(now, fourteenDayBoundary);
        }
        return defaultTtlWithJitter();
    }

    /**
     * For a time-dependent score we only jitter EARLIER, never later than the business
     * boundary. Positive jitter would let +20 survive past day 7 (or +10 past day 14).
     */
    private long ttlUntilBoundary(OffsetDateTime now, OffsetDateTime boundary) {
        long baseTtl = Math.max(1, Duration.between(now, boundary).getSeconds());
        long maxEarlyJitter = Math.min(JITTER_SECONDS, Math.max(0, baseTtl - 1));
        long earlyJitter = maxEarlyJitter == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(0, maxEarlyJitter + 1);
        return Math.max(1, baseTtl - earlyJitter);
    }

    private long defaultTtlWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(-JITTER_SECONDS, JITTER_SECONDS + 1);
        return Math.max(60, DEFAULT_TTL_SECONDS + jitter);
    }

    // API-created proofs use @CreationTimestamp, so a future timestamp should not normally
    // occur. Clamp clock-skew/import anomalies to `now` rather than granting >7 days of +20.
    private OffsetDateTime effectiveUploadedAt(RecentProof proof, OffsetDateTime now) {
        return proof.getUploadedAt().isAfter(now) ? now : proof.getUploadedAt();
    }

    private record ScoreCalculation(int score, long ttlSeconds) {}

    private java.util.Optional<RecentProof> latestActiveProof(Long restaurantId) {
        return recentProofRepository.findTopByRestaurantIdAndStatusOrderByUploadedAtDesc(
                restaurantId, RecentProofStatus.ACTIVE);
    }

    private String cacheKey(Long restaurantId) {
        return CACHE_KEY_PREFIX + restaurantId;
    }
}
