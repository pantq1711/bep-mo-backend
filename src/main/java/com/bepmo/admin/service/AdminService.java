package com.bepmo.admin.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.ingredientsource.entity.IngredientSource;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.repository.IngredientSourceRepository;
import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.repository.RestaurantRepository;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import com.bepmo.user.entity.User;
import com.bepmo.user.entity.UserStatus;
import com.bepmo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin module — nằm ngoài mọi domain package (không phải "common.service" chung chung,
 * mà là domain riêng "quản trị" với quyền hạn xuyên suốt các domain khác). Mọi endpoint
 * ở đây đã được chặn ở SecurityConfig (.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")),
 * nên Service này không cần tự check role, chỉ cần lo đúng nghiệp vụ.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final RestaurantRepository restaurantRepository;
    private final ProfileVideoRepository profileVideoRepository;
    private final IngredientSourceRepository ingredientSourceRepository;
    private final RecentProofRepository recentProofRepository;
    private final UserRepository userRepository;
    private final TransparencyScoreService transparencyScoreService;

    // ── Restaurant ────────────────────────────────────────────────────────────

    @Transactional
    public void hideRestaurant(Long restaurantId) {
        Restaurant r = findRestaurant(restaurantId);
        r.setStatus(RestaurantStatus.HIDDEN);
    }

    @Transactional
    public void unhideRestaurant(Long restaurantId) {
        Restaurant r = findRestaurant(restaurantId);
        r.setStatus(RestaurantStatus.ACTIVE);
    }

    // ── ProfileVideo ──────────────────────────────────────────────────────────

    @Transactional
    public void hideVideo(Long videoId) {
        ProfileVideo v = findVideo(videoId);
        v.setStatus(VideoStatus.HIDDEN);
        transparencyScoreService.evictCache(v.getRestaurantId());
    }

    @Transactional
    public void unhideVideo(Long videoId) {
        ProfileVideo v = findVideo(videoId);

        // Không thể unhide thẳng về ACTIVE nếu quán đã có video ACTIVE khác cùng type
        // trong lúc video này bị hide (chủ quán có thể đã upload video mới thay thế) —
        // sẽ vi phạm partial unique index uq_profile_videos_one_active_per_type.
        boolean conflict = profileVideoRepository.existsByRestaurantIdAndTypeAndStatus(
                v.getRestaurantId(), v.getType(), VideoStatus.ACTIVE);
        if (conflict) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Restaurant already has an active video of this type — cannot unhide");
        }

        v.setStatus(VideoStatus.ACTIVE);
        transparencyScoreService.evictCache(v.getRestaurantId());
    }

    // ── IngredientSource ──────────────────────────────────────────────────────

    @Transactional
    public void hideIngredientSource(Long sourceId) {
        IngredientSource s = findIngredientSource(sourceId);
        s.setStatus(IngredientSourceStatus.HIDDEN);
        transparencyScoreService.evictCache(s.getRestaurantId());
    }

    @Transactional
    public void unhideIngredientSource(Long sourceId) {
        IngredientSource s = findIngredientSource(sourceId);
        s.setStatus(IngredientSourceStatus.ACTIVE);
        transparencyScoreService.evictCache(s.getRestaurantId());
    }

    // ── RecentProof ───────────────────────────────────────────────────────────

    @Transactional
    public void hideRecentProof(Long proofId) {
        RecentProof p = findRecentProof(proofId);
        p.setStatus(RecentProofStatus.HIDDEN);
        transparencyScoreService.evictCache(p.getRestaurantId());
    }

    @Transactional
    public void unhideRecentProof(Long proofId) {
        RecentProof p = findRecentProof(proofId);
        p.setStatus(RecentProofStatus.ACTIVE);
        transparencyScoreService.evictCache(p.getRestaurantId());
    }

    // ── User ──────────────────────────────────────────────────────────────────

    @Transactional
    public void disableUser(Long userId) {
        User user = findUser(userId);
        user.setStatus(UserStatus.DISABLED);
        // Không revoke refresh token hiện có ở đây — access token cũ (TTL 15p) vẫn còn hiệu lực
        // tới khi hết hạn tự nhiên. Chấp nhận độ trễ tối đa 15p, ngoài scope MVP để làm real-time revoke.
    }

    @Transactional
    public void enableUser(Long userId) {
        User user = findUser(userId);
        user.setStatus(UserStatus.ACTIVE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Restaurant findRestaurant(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }

    private ProfileVideo findVideo(Long id) {
        return profileVideoRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Video not found"));
    }

    private IngredientSource findIngredientSource(Long id) {
        return ingredientSourceRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Ingredient source not found"));
    }

    private RecentProof findRecentProof(Long id) {
        return recentProofRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Recent proof not found"));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
