package com.bepmo.profilevideo.controller;

import com.bepmo.profilevideo.dto.ProfileVideoDtos.*;
import com.bepmo.profilevideo.service.ProfileVideoService;
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
@RequestMapping("/api/v1/restaurants/{restaurantId}/videos")
@RequiredArgsConstructor
@Tag(name = "ProfileVideo", description = "Kitchen/hygiene/prep/ingredient-receiving videos")
public class ProfileVideoController {

    private final ProfileVideoService profileVideoService;

    @PostMapping
    @Operation(summary = "Upload video (owner only) — client đã upload lên Cloudinary trước, đây chỉ record metadata")
    public ResponseEntity<ProfileVideoResponse> upload(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @Valid @RequestBody UploadVideoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profileVideoService.upload(restaurantId, currentUserId, request));
    }

    @GetMapping
    @Operation(summary = "List active videos of a restaurant (public)")
    public ResponseEntity<List<ProfileVideoResponse>> list(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(profileVideoService.listActive(restaurantId));
    }

    @PatchMapping("/{videoId}/hide")
    @Operation(summary = "Hide video (owner only)")
    public ResponseEntity<Void> hide(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long videoId
    ) {
        profileVideoService.hide(restaurantId, videoId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{videoId}")
    @Operation(summary = "Delete video (owner only, soft delete)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long videoId
    ) {
        profileVideoService.delete(restaurantId, videoId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
