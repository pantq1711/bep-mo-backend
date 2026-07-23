package com.bepmo.admin.controller;

import com.bepmo.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Moderation — hide/unhide nội dung, disable/enable user")
public class AdminController {

    private final AdminService adminService;

    // ── Restaurant ────────────────────────────────────────────────────────────

    @PatchMapping("/restaurants/{restaurantId}/hide")
    @Operation(summary = "Hide restaurant")
    public ResponseEntity<Void> hideRestaurant(@PathVariable Long restaurantId) {
        adminService.hideRestaurant(restaurantId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/restaurants/{restaurantId}/unhide")
    @Operation(summary = "Unhide restaurant")
    public ResponseEntity<Void> unhideRestaurant(@PathVariable Long restaurantId) {
        adminService.unhideRestaurant(restaurantId);
        return ResponseEntity.noContent().build();
    }

    // ── ProfileVideo ──────────────────────────────────────────────────────────

    @PatchMapping("/videos/{videoId}/hide")
    @Operation(summary = "Hide profile video")
    public ResponseEntity<Void> hideVideo(@PathVariable Long videoId) {
        adminService.hideVideo(videoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/videos/{videoId}/unhide")
    @Operation(summary = "Unhide profile video")
    public ResponseEntity<Void> unhideVideo(@PathVariable Long videoId) {
        adminService.unhideVideo(videoId);
        return ResponseEntity.noContent().build();
    }

    // ── IngredientSource ──────────────────────────────────────────────────────

    @PatchMapping("/ingredient-sources/{sourceId}/hide")
    @Operation(summary = "Hide ingredient source")
    public ResponseEntity<Void> hideIngredientSource(@PathVariable Long sourceId) {
        adminService.hideIngredientSource(sourceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/ingredient-sources/{sourceId}/unhide")
    @Operation(summary = "Unhide ingredient source")
    public ResponseEntity<Void> unhideIngredientSource(@PathVariable Long sourceId) {
        adminService.unhideIngredientSource(sourceId);
        return ResponseEntity.noContent().build();
    }

    // ── RecentProof ───────────────────────────────────────────────────────────

    @PatchMapping("/recent-proofs/{proofId}/hide")
    @Operation(summary = "Hide recent proof")
    public ResponseEntity<Void> hideRecentProof(@PathVariable Long proofId) {
        adminService.hideRecentProof(proofId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/recent-proofs/{proofId}/unhide")
    @Operation(summary = "Unhide recent proof")
    public ResponseEntity<Void> unhideRecentProof(@PathVariable Long proofId) {
        adminService.unhideRecentProof(proofId);
        return ResponseEntity.noContent().build();
    }

    // ── User ──────────────────────────────────────────────────────────────────

    @PatchMapping("/users/{userId}/disable")
    @Operation(summary = "Disable user account")
    public ResponseEntity<Void> disableUser(@PathVariable Long userId) {
        adminService.disableUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{userId}/enable")
    @Operation(summary = "Enable user account")
    public ResponseEntity<Void> enableUser(@PathVariable Long userId) {
        adminService.enableUser(userId);
        return ResponseEntity.noContent().build();
    }
}
