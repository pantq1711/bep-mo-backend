package com.bepmo.ingredientsource.controller;

import com.bepmo.ingredientsource.dto.IngredientSourceDtos.*;
import com.bepmo.ingredientsource.service.IngredientSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/ingredient-sources")
@RequiredArgsConstructor
@Tag(name = "IngredientSource", description = "Nguồn nguyên liệu tự khai của quán")
public class IngredientSourceController {

    private final IngredientSourceService ingredientSourceService;

    @PostMapping
    @Operation(summary = "Create ingredient source (owner only)")
    public ResponseEntity<IngredientSourceResponse> create(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateIngredientSourceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ingredientSourceService.create(restaurantId, currentUserId, request));
    }

    @PutMapping("/{sourceId}")
    @Operation(summary = "Update ingredient source (owner only)")
    public ResponseEntity<IngredientSourceResponse> update(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long sourceId,
            @Valid @RequestBody UpdateIngredientSourceRequest request
    ) {
        return ResponseEntity.ok(ingredientSourceService.update(restaurantId, sourceId, currentUserId, request));
    }

    @DeleteMapping("/{sourceId}")
    @Operation(summary = "Delete ingredient source (owner only, soft delete)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long sourceId
    ) {
        ingredientSourceService.delete(restaurantId, sourceId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List active ingredient sources of a restaurant (public)")
    public ResponseEntity<List<IngredientSourceResponse>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(ingredientSourceService.listActive(restaurantId));
    }
}
