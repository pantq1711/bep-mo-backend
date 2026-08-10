package com.bepmo.restaurant.repository;

import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // Public listing — chỉ ACTIVE, có search/filter + phân trang.
    // query=null  -> không lọc text
    // category=null -> không lọc category
    @Query("""
            SELECT r
            FROM Restaurant r
            WHERE r.status = :status
              AND (
                    :query IS NULL
                    OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(r.address) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(r.category, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
              AND (
                    :category IS NULL
                    OR LOWER(r.category) = LOWER(:category)
                  )
            """)
    Page<Restaurant> searchPublic(
            @Param("status") RestaurantStatus status,
            @Param("query") String query,
            @Param("category") String category,
            Pageable pageable
    );

    // Danh sách category public để frontend tạo filter động, không hard-code loại quán.
    @Query("""
            SELECT DISTINCT TRIM(r.category)
            FROM Restaurant r
            WHERE r.status = :status
              AND r.category IS NOT NULL
              AND TRIM(r.category) <> ''
            ORDER BY TRIM(r.category)
            """)
    List<String> findDistinctCategoriesByStatus(@Param("status") RestaurantStatus status);

    // Public detail — quán HIDDEN không được truy cập bằng cách đoán URL/id.
    Optional<Restaurant> findByIdAndStatus(Long id, RestaurantStatus status);

    // Owner lookup — kiểm tra owner đã có quán chưa
    Optional<Restaurant> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}
