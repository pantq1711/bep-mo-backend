package com.bepmo.restaurant.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.restaurant.dto.RestaurantDtos.*;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    // Page size cap — chặn client request size=10000 gây quá tải DB
    private static final int MAX_PAGE_SIZE = 50;

    private final RestaurantRepository restaurantRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public RestaurantProfile create(Long ownerId, CreateRestaurantRequest request) {
        // Enforce 1 owner = 1 restaurant ở cả application layer (fail sớm, message rõ ràng)
        // lẫn DB layer (UNIQUE(owner_id) — race condition cuối cùng vẫn được chặn ở đó)
        if (restaurantRepository.existsByOwnerId(ownerId)) {
            throw new AppException(HttpStatus.CONFLICT, "Owner already has a restaurant");
        }

        Restaurant restaurant = Restaurant.builder()
                .ownerId(ownerId)
                .name(request.name())
                .description(request.description())
                .address(request.address())
                .category(request.category())
                .status(RestaurantStatus.ACTIVE)
                .build();

        restaurantRepository.save(restaurant);
        return toProfile(restaurant);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public RestaurantProfile update(Long restaurantId, Long ownerId, UpdateRestaurantRequest request) {
        Restaurant restaurant = requireOwnedRestaurant(restaurantId, ownerId);

        // Partial update — chỉ field client gửi (khác null) mới bị ghi đè
        if (request.name() != null) restaurant.setName(request.name());
        if (request.description() != null) restaurant.setDescription(request.description());
        if (request.address() != null) restaurant.setAddress(request.address());
        if (request.category() != null) restaurant.setCategory(request.category());

        // Không cần gọi save() tường minh — entity đang managed trong transaction hiện tại,
        // Hibernate tự flush thay đổi (dirty checking) khi transaction commit.
        return toProfile(restaurant);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RestaurantProfile getProfile(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdAndStatus(restaurantId, RestaurantStatus.ACTIVE)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        return toProfile(restaurant);
    }

    // Owner tra "quán của tôi" — cần cho frontend Tuần 8 quyết định điều hướng:
    // chưa có quán -> màn hình tạo hồ sơ lần đầu; đã có -> dashboard quản lý.
    // Không dùng requireOwnedRestaurant() vì ở đây chưa biết restaurantId, chỉ có ownerId.
    @Transactional(readOnly = true)
    public RestaurantProfile getMyRestaurant(Long ownerId) {
        Restaurant restaurant = restaurantRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "You don't have a restaurant yet"));
        return toProfile(restaurant);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RestaurantSummary> list(int page, int size, String query, String category) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedQuery = normalizeSearchText(query);
        String normalizedCategory = normalizeSearchText(category);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return PagedResponse.from(
                restaurantRepository.searchPublic(
                        RestaurantStatus.ACTIVE,
                        normalizedQuery,
                        normalizedCategory,
                        pageable
                ).map(this::toSummary)
        );
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        return restaurantRepository.findDistinctCategoriesByStatus(RestaurantStatus.ACTIVE);
    }

    // ── Ownership guard — dùng lại bởi Dish/ProfileVideo/IngredientSource/RecentProof service ──

    @Transactional(readOnly = true)
    public Restaurant requireOwnedRestaurant(Long restaurantId, Long currentUserId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        if (currentUserId == null || !restaurant.getOwnerId().equals(currentUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not own this restaurant");
        }

        return restaurant;
    }

    /**
     * Same ownership guard but obtains a PostgreSQL row lock on the parent restaurant.
     * Score-affecting mutations use this method so two writers for the same restaurant
     * cannot interleave between "demote old state" and "insert/activate new state".
     */
    @Transactional
    public Restaurant requireOwnedRestaurantForUpdate(Long restaurantId, Long currentUserId) {
        Restaurant restaurant = lockRestaurantForUpdate(restaurantId);

        if (currentUserId == null || !restaurant.getOwnerId().equals(currentUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not own this restaurant");
        }

        return restaurant;
    }

    /**
     * Public nested resources follow the same visibility semantics as the restaurant
     * profile: ACTIVE is public; HIDDEN is only visible to its authenticated owner.
     * Return 404 to anonymous/non-owner callers so moderation state does not leak.
     */
    @Transactional(readOnly = true)
    public Restaurant requireViewableRestaurant(Long restaurantId, Long currentUserId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        boolean isOwner = currentUserId != null && restaurant.getOwnerId().equals(currentUserId);
        if (restaurant.getStatus() != RestaurantStatus.ACTIVE && !isOwner) {
            throw new AppException(HttpStatus.NOT_FOUND, "Restaurant not found");
        }

        return restaurant;
    }

    /**
     * Visibility check + transaction lock. Used only on a transparency-score cache miss.
     * The lock is deliberately exclusive: it serializes the cache-miss calculation with
     * every score-affecting mutation for this restaurant and makes stale cache repopulation
     * after a writer commit impossible through application service paths.
     */
    @Transactional
    public Restaurant requireViewableRestaurantForUpdate(Long restaurantId, Long currentUserId) {
        Restaurant restaurant = lockRestaurantForUpdate(restaurantId);

        boolean isOwner = currentUserId != null && restaurant.getOwnerId().equals(currentUserId);
        if (restaurant.getStatus() != RestaurantStatus.ACTIVE && !isOwner) {
            throw new AppException(HttpStatus.NOT_FOUND, "Restaurant not found");
        }

        return restaurant;
    }

    @Transactional
    public Restaurant lockRestaurantForUpdate(Long restaurantId) {
        return restaurantRepository.findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }

    private String normalizeSearchText(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────
    // Cố tình KHÔNG gọi TransparencyScoreService ở đây để giữ domain restaurant độc lập
    // với domain score — tránh coupling chéo và tránh N Redis call khi list() nhiều quán cùng lúc.
    // Client lấy điểm minh bạch qua endpoint riêng GET /restaurants/{id}/transparency-score.

    private RestaurantSummary toSummary(Restaurant r) {
        return new RestaurantSummary(r.getId(), r.getName(), r.getAddress(), r.getCategory(), r.getAvatarUrl(), null);
    }

    private RestaurantProfile toProfile(Restaurant r) {
        return new RestaurantProfile(
                r.getId(), r.getName(), r.getDescription(), r.getAddress(),
                r.getCategory(), r.getAvatarUrl(), null, r.getCreatedAt()
        );
    }
}
