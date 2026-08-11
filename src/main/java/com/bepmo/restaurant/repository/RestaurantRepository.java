package com.bepmo.restaurant.repository;

import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // Public listing — chỉ ACTIVE, có search/filter + phân trang.
    // query/category luôn là chuỗi không-null; chuỗi rỗng nghĩa là không lọc.
    // Tránh truyền NULL vào LOWER(:param) vì PostgreSQL có thể bind NULL thành bytea.
    @Query("""
            SELECT r
            FROM Restaurant r
            WHERE r.status = :status
              AND (
                    :query = ''
                    OR LOWER(r.name) LIKE CONCAT('%', :query, '%')
                    OR LOWER(r.address) LIKE CONCAT('%', :query, '%')
                    OR LOWER(COALESCE(r.category, '')) LIKE CONCAT('%', :query, '%')
                  )
              AND (
                    :category = ''
                    OR LOWER(r.category) = :category
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

    /**
     * Coarse-grained per-restaurant transaction lock used by score-affecting writers and
     * score cache-miss calculations. PostgreSQL holds the row lock until transaction end,
     * so concurrent mutations for the same restaurant are serialized while different
     * restaurants can still proceed independently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Restaurant r WHERE r.id = :id")
    Optional<Restaurant> findByIdForUpdate(@Param("id") Long id);
}
