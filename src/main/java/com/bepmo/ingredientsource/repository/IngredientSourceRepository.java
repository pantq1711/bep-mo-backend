package com.bepmo.ingredientsource.repository;

import com.bepmo.ingredientsource.entity.IngredientSource;
import com.bepmo.ingredientsource.entity.IngredientSourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngredientSourceRepository extends JpaRepository<IngredientSource, Long> {

    boolean existsByRestaurantIdAndStatus(Long restaurantId, IngredientSourceStatus status);

    List<IngredientSource> findByRestaurantIdAndStatus(Long restaurantId, IngredientSourceStatus status);

    List<IngredientSource> findByRestaurantId(Long restaurantId);

    @Query("SELECT s.restaurantId FROM IngredientSource s WHERE s.id = :id")
    Optional<Long> findRestaurantIdById(@Param("id") Long id);
}
