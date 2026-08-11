package com.bepmo.ingredientsource;

import com.bepmo.common.exception.AppException;
import com.bepmo.ingredientsource.dto.IngredientSourceDtos.*;
import com.bepmo.ingredientsource.entity.IngredientSource;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.entity.SourceType;
import com.bepmo.ingredientsource.repository.IngredientSourceRepository;
import com.bepmo.ingredientsource.service.IngredientSourceService;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class IngredientSourceServiceTest {

    @Mock IngredientSourceRepository ingredientSourceRepository;
    @Mock RestaurantService restaurantService;
    @Mock TransparencyScoreService transparencyScoreService;

    @InjectMocks IngredientSourceService ingredientSourceService;

    private Restaurant restaurant;
    private IngredientSource source;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
        source = IngredientSource.builder()
                .id(200L).restaurantId(1L).name("Cho Dau Moi")
                .sourceType(SourceType.LOCAL_MARKET).status(IngredientSourceStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("create: thành công → status ACTIVE, evict score cache")
    void create_success() {
        when(restaurantService.requireOwnedRestaurantForUpdate(1L, 10L)).thenReturn(restaurant);
        when(ingredientSourceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        IngredientSourceResponse result = ingredientSourceService.create(1L, 10L,
                new CreateIngredientSourceRequest("Cho Dau Moi", SourceType.LOCAL_MARKET, null));

        assertThat(result.status()).isEqualTo(IngredientSourceStatus.ACTIVE);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("update: source thuộc quán khác (path variable sai) → 404")
    void update_sourceBelongsToDifferentRestaurant() {
        when(restaurantService.requireOwnedRestaurant(2L, 10L)).thenReturn(restaurant);
        when(ingredientSourceRepository.findById(200L)).thenReturn(Optional.of(source)); // source.restaurantId=1

        assertThatThrownBy(() -> ingredientSourceService.update(2L, 200L, 10L,
                new UpdateIngredientSourceRequest("X", null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("delete: soft delete → status DELETED, evict cache")
    void delete_softDeletesAndEvictsCache() {
        when(restaurantService.requireOwnedRestaurantForUpdate(1L, 10L)).thenReturn(restaurant);
        when(ingredientSourceRepository.findById(200L)).thenReturn(Optional.of(source));

        ingredientSourceService.delete(1L, 200L, 10L);

        assertThat(source.getStatus()).isEqualTo(IngredientSourceStatus.DELETED);
        verify(ingredientSourceRepository, never()).delete(any());
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("listActive: chỉ trả về source ACTIVE")
    void listActive_onlyActive() {
        when(restaurantService.requireViewableRestaurant(1L, null)).thenReturn(restaurant);
        when(ingredientSourceRepository.findByRestaurantIdAndStatus(1L, IngredientSourceStatus.ACTIVE))
                .thenReturn(java.util.List.of(source));

        var result = ingredientSourceService.listActive(1L, null);

        assertThat(result).hasSize(1);
    }
}
