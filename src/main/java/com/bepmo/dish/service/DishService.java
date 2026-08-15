package com.bepmo.dish.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.dish.dto.DishDtos.*;
import com.bepmo.dish.entity.Dish;
import com.bepmo.dish.repository.DishRepository;
import com.bepmo.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DishService {

    private final DishRepository dishRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public DishResponse create(Long restaurantId, Long ownerId, CreateDishRequest request) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);

        Dish dish = Dish.builder()
                .restaurantId(restaurantId)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .category(normalizeOptionalCategory(request.category()))
                .isAvailable(true)
                .build();

        dishRepository.save(dish);
        return toResponse(dish);
    }

    @Transactional
    public DishResponse update(Long restaurantId, Long dishId, Long ownerId, UpdateDishRequest request) {
        // Serialize dish mutations per restaurant because Dish has no optimistic @Version.
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);
        Dish dish = requireDishInRestaurant(dishId, restaurantId);

        if (request.name() != null) dish.setName(request.name());
        if (request.description() != null) dish.setDescription(request.description());
        if (request.price() != null) dish.setPrice(request.price());
        if (request.category() != null) dish.setCategory(normalizeOptionalCategory(request.category()));
        if (request.isAvailable() != null) dish.setIsAvailable(request.isAvailable());

        return toResponse(dish);
    }

    @Transactional
    public DishResponse setAvailability(Long restaurantId, Long dishId, Long ownerId, boolean available) {
        // Same lock order as update/delete: restaurant -> dish.
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);
        Dish dish = requireDishInRestaurant(dishId, restaurantId);
        dish.setIsAvailable(available);
        return toResponse(dish);
    }

    @Transactional
    public void delete(Long restaurantId, Long dishId, Long ownerId) {
        // Same lock order as update/availability to avoid update-vs-delete races.
        restaurantService.requireOwnedRestaurantForUpdate(restaurantId, ownerId);
        Dish dish = requireDishInRestaurant(dishId, restaurantId);
        dishRepository.delete(dish);
    }

    @Transactional(readOnly = true)
    public List<DishResponse> list(Long restaurantId, boolean availableOnly, Long currentUserId) {
        if (availableOnly) {
            restaurantService.requireViewableRestaurant(restaurantId, currentUserId);
        } else {
            if (currentUserId == null) {
                throw new AppException(HttpStatus.UNAUTHORIZED,
                        "Authentication required to list unavailable dishes");
            }
            restaurantService.requireOwnedRestaurant(restaurantId, currentUserId);
        }

        List<Dish> dishes = availableOnly
                ? dishRepository.findByRestaurantIdAndIsAvailableTrue(restaurantId)
                : dishRepository.findByRestaurantId(restaurantId);
        return dishes.stream().map(this::toResponse).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String normalizeOptionalCategory(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Dish requireDishInRestaurant(Long dishId, Long restaurantId) {
        Dish dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Dish not found"));

        // Defense-in-depth: path variable restaurantId phải khớp dish thật sự thuộc quán nào,
        // không tin path variable mù quáng dù đã check ownership ở trên
        if (!dish.getRestaurantId().equals(restaurantId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Dish not found in this restaurant");
        }

        return dish;
    }

    private DishResponse toResponse(Dish d) {
        return new DishResponse(
                d.getId(), d.getRestaurantId(), d.getName(), d.getDescription(),
                d.getPrice(), d.getCategory(), d.getImageUrl(), d.getIsAvailable(), d.getCreatedAt()
        );
    }
}
