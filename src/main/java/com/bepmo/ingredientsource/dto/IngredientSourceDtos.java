package com.bepmo.ingredientsource.dto;

import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import com.bepmo.ingredientsource.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class IngredientSourceDtos {

    public record CreateIngredientSourceRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull SourceType sourceType,
        String note
    ) {}

    public record UpdateIngredientSourceRequest(
        @Size(max = 150) String name,
        SourceType sourceType,
        String note
    ) {}

    public record IngredientSourceResponse(
        Long id,
        Long restaurantId,
        String name,
        SourceType sourceType,
        String note,
        IngredientSourceStatus status,
        OffsetDateTime createdAt
    ) {}
}
