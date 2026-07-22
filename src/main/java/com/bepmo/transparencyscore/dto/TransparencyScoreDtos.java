package com.bepmo.transparencyscore.dto;

public class TransparencyScoreDtos {

    public record TransparencyScoreResponse(
        Long restaurantId,
        int score,
        int maxScore
    ) {}
}
