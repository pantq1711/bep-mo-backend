package com.bepmo.restaurant.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.restaurant.dto.RestaurantDtos.*;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        return toProfile(restaurant);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RestaurantSummary> list(int page, int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, cappedSize);
        return PagedResponse.from(
                restaurantRepository.findByStatus(RestaurantStatus.ACTIVE, pageable).map(this::toSummary)
        );
    }

    // ── Ownership guard — dùng lại bởi Dish/ProfileVideo/IngredientSource/RecentProof service ──

    @Transactional(readOnly = true)
    public Restaurant requireOwnedRestaurant(Long restaurantId, Long currentUserId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Restaurant not found"));

        if (!restaurant.getOwnerId().equals(currentUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not own this restaurant");
        }

        return restaurant;
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
