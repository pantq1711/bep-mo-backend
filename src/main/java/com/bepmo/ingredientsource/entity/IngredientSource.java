package com.bepmo.ingredientsource.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ingredient_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "source_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private IngredientSourceStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
