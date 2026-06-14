package com.bepmo.restaurant.repository;

import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // Public listing — chỉ ACTIVE, có phân trang
    // Spring Data tự generate: SELECT * FROM restaurants WHERE status = ? LIMIT ? OFFSET ?
    Page<Restaurant> findByStatus(RestaurantStatus status, Pageable pageable);

    // Owner lookup — kiểm tra owner đã có quán chưa
    Optional<Restaurant> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}
