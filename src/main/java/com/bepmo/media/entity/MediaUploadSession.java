package com.bepmo.media.entity;

import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.recentproof.entity.ProofType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_upload_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUploadSession {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaUploadPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_video_type", length = 50)
    private VideoType profileVideoType;

    @Enumerated(EnumType.STRING)
    @Column(name = "recent_proof_type", length = 50)
    private ProofType recentProofType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private MediaResourceType resourceType;

    @Column(name = "expected_public_id", nullable = false, length = 255)
    private String expectedPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaUploadSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
