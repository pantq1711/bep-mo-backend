package com.bepmo.dish.controller;

import com.bepmo.dish.dto.DishDtos.*;
import com.bepmo.dish.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/dishes")
@RequiredArgsConstructor
@Tag(name = "Dish", description = "Menu items of a restaurant")
public class DishController {

    private final DishService dishService;

    @PostMapping
    @Operation(summary = "Create dish (owner only)")
    public ResponseEntity<DishResponse> create(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateDishRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dishService.create(restaurantId, currentUserId, request));
    }

    @PutMapping("/{dishId}")
    @Operation(summary = "Update dish (owner only)")
    public ResponseEntity<DishResponse> update(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long dishId,
            @Valid @RequestBody UpdateDishRequest request
    ) {
        return ResponseEntity.ok(dishService.update(restaurantId, dishId, currentUserId, request));
    }

    @PatchMapping("/{dishId}/availability")
    @Operation(summary = "Toggle dish availability (owner only)")
    public ResponseEntity<DishResponse> setAvailability(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long dishId,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean available = Boolean.TRUE.equals(body.get("isAvailable"));
        return ResponseEntity.ok(dishService.setAvailability(restaurantId, dishId, currentUserId, available));
    }

    @DeleteMapping("/{dishId}")
    @Operation(summary = "Delete dish (owner only)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long dishId
    ) {
        dishService.delete(restaurantId, dishId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List dishes of a restaurant (public)")
    public ResponseEntity<List<DishResponse>> list(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "false") boolean availableOnly
    ) {
        return ResponseEntity.ok(dishService.list(restaurantId, availableOnly));
    }
}
