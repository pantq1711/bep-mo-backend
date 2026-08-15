package com.bepmo.admin.dto;

import com.bepmo.restaurant.entity.RestaurantStatus;

import java.time.OffsetDateTime;

public class AdminDtos {

    private AdminDtos() {}

    public record AdminRestaurantSummary(
            Long id,
            String name,
            String address,
            String category,
            RestaurantStatus status,
            OffsetDateTime createdAt
    ) {}
}
