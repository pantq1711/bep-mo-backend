package com.bepmo.recentproof.controller;

import com.bepmo.recentproof.dto.RecentProofDtos.*;
import com.bepmo.recentproof.service.RecentProofService;
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
@RequestMapping("/api/v1/restaurants/{restaurantId}/recent-proofs")
@RequiredArgsConstructor
@Tag(name = "RecentProof", description = "Bằng chứng gần đây (hoá đơn, ảnh nguyên liệu, video nhận hàng...)")
public class RecentProofController {

    private final RecentProofService recentProofService;

    @PostMapping
    @Operation(summary = "Upload recent proof (owner only)")
    public ResponseEntity<RecentProofResponse> create(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateRecentProofRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recentProofService.create(restaurantId, currentUserId, request));
    }

    @DeleteMapping("/{proofId}")
    @Operation(summary = "Delete recent proof (owner only, soft delete)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long currentUserId,
            @PathVariable Long restaurantId,
            @PathVariable Long proofId
    ) {
        recentProofService.delete(restaurantId, proofId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get 3 most recent active proofs (public)")
    public ResponseEntity<List<RecentProofResponse>> listRecent(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(recentProofService.listRecentActive(restaurantId));
    }
}
