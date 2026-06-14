package com.bepmo.dish.repository;

import com.bepmo.dish.entity.Dish;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DishRepository extends JpaRepository<Dish, Long> {

    List<Dish> findByRestaurantIdAndIsAvailableTrue(Long restaurantId);

    List<Dish> findByRestaurantId(Long restaurantId);
}
