package com.bepmo.recentproof.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recent_proofs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "proof_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ProofType proofType;

    @Column(name = "media_kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MediaKind mediaKind;

    @Column(name = "media_url", nullable = false, columnDefinition = "TEXT")
    private String mediaUrl;

    @Column(name = "cloudinary_public_id", nullable = false, length = 255)
    private String cloudinaryPublicId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RecentProofStatus status;

    // uploadedAt = thời điểm tạo record, dùng để tính Freshness score.
    // Dùng @CreationTimestamp thay vì set thủ công trong service, tránh lệch giờ server/DB
    // và tuân đúng convention "timestamp không set tay trong service".
    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
