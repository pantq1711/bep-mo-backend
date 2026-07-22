package com.bepmo.transparencyscore.controller;

import com.bepmo.transparencyscore.dto.TransparencyScoreDtos.TransparencyScoreResponse;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/transparency-score")
@RequiredArgsConstructor
@Tag(name = "TransparencyScore", description = "Điểm minh bạch — tính on-demand, cache-aside Redis")
public class TransparencyScoreController {

    private final TransparencyScoreService transparencyScoreService;

    @GetMapping
    @Operation(summary = "Get transparency score of a restaurant (public)")
    public ResponseEntity<TransparencyScoreResponse> getScore(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(transparencyScoreService.getScore(restaurantId));
    }
}
