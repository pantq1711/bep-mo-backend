package com.bepmo.dish;

import com.bepmo.common.exception.AppException;
import com.bepmo.dish.dto.DishDtos.*;
import com.bepmo.dish.entity.Dish;
import com.bepmo.dish.repository.DishRepository;
import com.bepmo.dish.service.DishService;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishServiceTest {

    @Mock DishRepository dishRepository;
    @Mock RestaurantService restaurantService;

    @InjectMocks DishService dishService;

    private Restaurant restaurant;
    private Dish dish;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
        dish = Dish.builder()
                .id(100L).restaurantId(1L).name("Pho Bo")
                .price(new BigDecimal("50000")).isAvailable(true)
                .build();
    }

    @Test
    @DisplayName("create: đúng chủ quán → tạo dish với isAvailable mặc định true")
    void create_success() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        when(dishRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DishResponse result = dishService.create(1L, 10L,
                new CreateDishRequest("Pho Bo", "ngon", new BigDecimal("50000"), "Main"));

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.name()).isEqualTo("Pho Bo");
    }

    @Test
    @DisplayName("create: không phải chủ quán → không tạo, ném lỗi từ RestaurantService")
    void create_notOwner_propagatesException() {
        when(restaurantService.requireOwnedRestaurant(1L, 999L))
                .thenThrow(new AppException(HttpStatus.FORBIDDEN, "not owner"));

        assertThatThrownBy(() -> dishService.create(1L, 999L,
                new CreateDishRequest("X", null, BigDecimal.ONE, null)))
                .isInstanceOf(AppException.class);

        verify(dishRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: dish thuộc quán khác (path variable sai) → 404, defense-in-depth")
    void update_dishBelongsToDifferentRestaurant() {
        when(restaurantService.requireOwnedRestaurant(2L, 10L)).thenReturn(restaurant);
        when(dishRepository.findById(100L)).thenReturn(Optional.of(dish)); // dish.restaurantId = 1, không phải 2

        assertThatThrownBy(() -> dishService.update(2L, 100L, 10L,
                new UpdateDishRequest("Y", null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("setAvailability: đúng chủ quán → cập nhật isAvailable")
    void setAvailability_success() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        when(dishRepository.findById(100L)).thenReturn(Optional.of(dish));

        DishResponse result = dishService.setAvailability(1L, 100L, 10L, false);

        assertThat(result.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("delete: dish không tồn tại → 404")
    void delete_dishNotFound() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        when(dishRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dishService.delete(1L, 999L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("list: availableOnly=true → gọi đúng repository method lọc theo isAvailable")
    void list_availableOnly() {
        when(dishRepository.findByRestaurantIdAndIsAvailableTrue(1L)).thenReturn(java.util.List.of(dish));

        var result = dishService.list(1L, true);

        assertThat(result).hasSize(1);
        verify(dishRepository).findByRestaurantIdAndIsAvailableTrue(1L);
        verify(dishRepository, never()).findByRestaurantId(anyLong());
    }
}
