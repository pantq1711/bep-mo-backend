package com.bepmo.dish.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class DishDtos {

    public record CreateDishRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        String category
    ) {}

    public record UpdateDishRequest(
        @Size(max = 150) String name,
        String description,
        @DecimalMin("0.0") BigDecimal price,
        String category,
        Boolean isAvailable
    ) {}

    public record DishResponse(
        Long id,
        Long restaurantId,
        String name,
        String description,
        BigDecimal price,
        String category,
        String imageUrl,
        Boolean isAvailable,
        OffsetDateTime createdAt
    ) {}
}
