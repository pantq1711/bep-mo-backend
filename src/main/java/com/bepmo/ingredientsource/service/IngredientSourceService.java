package com.bepmo.ingredientsource.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.ingredientsource.dto.IngredientSourceDtos.*;
import com.bepmo.ingredientsource.entity.IngredientSource;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.repository.IngredientSourceRepository;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngredientSourceService {

    private final IngredientSourceRepository ingredientSourceRepository;
    private final RestaurantService restaurantService;
    private final TransparencyScoreService transparencyScoreService;

    @Transactional
    public IngredientSourceResponse create(Long restaurantId, Long ownerId, CreateIngredientSourceRequest request) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);

        IngredientSource source = IngredientSource.builder()
                .restaurantId(restaurantId)
                .name(request.name())
                .sourceType(request.sourceType())
                .note(request.note())
                .status(IngredientSourceStatus.ACTIVE)
                .build();

        ingredientSourceRepository.save(source);

        // Ít nhất 1 ingredient source ACTIVE = +15 Completeness — evict cache
        transparencyScoreService.evictCache(restaurantId);

        return toResponse(source);
    }

    @Transactional
    public IngredientSourceResponse update(Long restaurantId, Long sourceId, Long ownerId,
                                            UpdateIngredientSourceRequest request) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);
        IngredientSource source = requireSourceInRestaurant(sourceId, restaurantId);

        if (request.name() != null) source.setName(request.name());
        if (request.sourceType() != null) source.setSourceType(request.sourceType());
        if (request.note() != null) source.setNote(request.note());

        return toResponse(source);
    }

    @Transactional
    public void delete(Long restaurantId, Long sourceId, Long ownerId) {
        restaurantService.requireOwnedRestaurant(restaurantId, ownerId);
        IngredientSource source = requireSourceInRestaurant(sourceId, restaurantId);
        source.setStatus(IngredientSourceStatus.DELETED);
        transparencyScoreService.evictCache(restaurantId);
    }

    @Transactional(readOnly = true)
    public List<IngredientSourceResponse> listActive(Long restaurantId) {
        return ingredientSourceRepository.findByRestaurantIdAndStatus(restaurantId, IngredientSourceStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IngredientSource requireSourceInRestaurant(Long sourceId, Long restaurantId) {
        IngredientSource source = ingredientSourceRepository.findById(sourceId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Ingredient source not found"));

        if (!source.getRestaurantId().equals(restaurantId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Ingredient source not found in this restaurant");
        }

        return source;
    }

    private IngredientSourceResponse toResponse(IngredientSource s) {
        return new IngredientSourceResponse(
                s.getId(), s.getRestaurantId(), s.getName(), s.getSourceType(),
                s.getNote(), s.getStatus(), s.getCreatedAt()
        );
    }
}
