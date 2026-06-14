package com.bepmo.ingredientsource.repository;

import com.bepmo.ingredientsource.entity.IngredientSource;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngredientSourceRepository extends JpaRepository<IngredientSource, Long> {

    boolean existsByRestaurantIdAndStatus(Long restaurantId, IngredientSourceStatus status);

    List<IngredientSource> findByRestaurantIdAndStatus(Long restaurantId, IngredientSourceStatus status);

    List<IngredientSource> findByRestaurantId(Long restaurantId);
}
