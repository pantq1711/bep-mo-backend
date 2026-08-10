package com.bepmo.restaurant.controller;

import com.bepmo.restaurant.dto.RestaurantDtos.*;
import com.bepmo.restaurant.service.RestaurantService;
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
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurant", description = "Restaurant profile — create, update, browse")
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @Operation(summary = "Create restaurant profile for current owner (1 owner = 1 restaurant)")
    public ResponseEntity<RestaurantProfile> create(
            @AuthenticationPrincipal Long currentUserId,
            @Valid @RequestBody CreateRestaurantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.create(currentUserId, request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current owner's own restaurant, if any (404 if not created yet)")
    public ResponseEntity<RestaurantProfile> getMine(@AuthenticationPrincipal Long currentUserId) {
        return ResponseEntity.ok(restaurantService.getMyRestaurant(currentUserId));
    }

    @PutMapping("/{restaurantId}")
    @Operation(summary = "Update restaurant profile (owner only)")
    public ResponseEntity<RestaurantProfile> update(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @Valid @RequestBody UpdateRestaurantRequest request
    ) {
        return ResponseEntity.ok(restaurantService.update(restaurantId, currentUserId, request));
    }

    @GetMapping("/{restaurantId}")
    @Operation(summary = "Get restaurant profile (public)")
    public ResponseEntity<RestaurantProfile> get(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(restaurantService.getProfile(restaurantId));
    }

    @GetMapping
    @Operation(summary = "List active restaurants (public, searchable/filterable, paginated)")
    public ResponseEntity<PagedResponse<RestaurantSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(restaurantService.list(page, size, query, category));
    }

    @GetMapping("/categories")
    @Operation(summary = "List active restaurant categories for public filters")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(restaurantService.listCategories());
    }
}
