package com.bepmo.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class RestaurantDtos {

    // ── Request ───────────────────────────────────────────────────────────────

    public record CreateRestaurantRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotBlank @Size(max = 255) String address,
        String category
    ) {}

    public record UpdateRestaurantRequest(
        @Size(max = 150) String name,
        String description,
        @Size(max = 255) String address,
        String category
    ) {}

    // ── Response — summary for list endpoint ─────────────────────────────────

    public record RestaurantSummary(
        Long id,
        String name,
        String address,
        String category,
        String avatarUrl,
        Integer transparencyScore   // nullable — null khi chưa có data
    ) {}

    // ── Response — full profile for detail endpoint ───────────────────────────

    public record RestaurantProfile(
        Long id,
        String name,
        String description,
        String address,
        String category,
        String avatarUrl,
        Integer transparencyScore,
        OffsetDateTime createdAt
    ) {}

    // ── Paginated wrapper ─────────────────────────────────────────────────────
    // Không dùng Spring Page<T> trực tiếp trong response vì nó expose internal fields
    // Wrap lại để control shape của JSON trả về

    public record PagedResponse<T>(
        java.util.List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
    ) {
        public static <T> PagedResponse<T> from(org.springframework.data.domain.Page<T> page) {
            return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
            );
        }
    }
}
