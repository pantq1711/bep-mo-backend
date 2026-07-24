package com.bepmo.restaurant;

import com.bepmo.common.exception.AppException;
import com.bepmo.restaurant.dto.RestaurantDtos.*;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.repository.RestaurantRepository;
import com.bepmo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock RestaurantRepository restaurantRepository;

    @InjectMocks RestaurantService restaurantService;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .id(1L).ownerId(10L)
                .name("Quan Ngon").address("123 Le Loi")
                .status(RestaurantStatus.ACTIVE)
                .build();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: owner chưa có quán → tạo mới thành công")
    void create_success() {
        when(restaurantRepository.existsByOwnerId(10L)).thenReturn(false);
        when(restaurantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RestaurantProfile result = restaurantService.create(10L,
                new CreateRestaurantRequest("Quan Ngon", "mo ta", "123 Le Loi", "Vietnamese"));

        assertThat(result.name()).isEqualTo("Quan Ngon");
        verify(restaurantRepository).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("create: owner đã có quán rồi → 409 CONFLICT")
    void create_ownerAlreadyHasRestaurant() {
        when(restaurantRepository.existsByOwnerId(10L)).thenReturn(true);

        assertThatThrownBy(() -> restaurantService.create(10L,
                new CreateRestaurantRequest("Quan 2", null, "456 Tran Phu", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(restaurantRepository, never()).save(any());
    }

    // ── Update / ownership guard ─────────────────────────────────────────────

    @Test
    @DisplayName("update: đúng chủ quán → cập nhật field không null")
    void update_success() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        RestaurantProfile result = restaurantService.update(1L, 10L,
                new UpdateRestaurantRequest("Quan Ngon Moi", null, null, null));

        assertThat(result.name()).isEqualTo("Quan Ngon Moi");
        assertThat(restaurant.getAddress()).isEqualTo("123 Le Loi"); // field null trong request -> giữ nguyên
    }

    @Test
    @DisplayName("update: không phải chủ quán → 403 FORBIDDEN")
    void update_notOwner_forbidden() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.update(1L, 999L,
                new UpdateRestaurantRequest("Hack", null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("requireOwnedRestaurant: quán không tồn tại → 404 NOT_FOUND")
    void requireOwnedRestaurant_notFound() {
        when(restaurantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.requireOwnedRestaurant(999L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProfile: tồn tại → trả về profile, transparencyScore null (tách domain)")
    void getProfile_success() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        RestaurantProfile result = restaurantService.getProfile(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.transparencyScore()).isNull();
    }

    @Test
    @DisplayName("list: cap page size tối đa 50 dù client request lớn hơn")
    void list_capsPageSize() {
        when(restaurantRepository.findByStatus(eq(RestaurantStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(restaurant), PageRequest.of(0, 50), 1));

        restaurantService.list(0, 1000);

        verify(restaurantRepository).findByStatus(eq(RestaurantStatus.ACTIVE), argThat(p -> p.getPageSize() == 50));
    }
}
