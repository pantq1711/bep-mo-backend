package com.bepmo.recentproof.dto;

import com.bepmo.recentproof.entity.MediaKind;
import com.bepmo.recentproof.entity.ProofType;
import com.bepmo.recentproof.entity.RecentProofStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RecentProofDtos {

    /**
     * proofType/mediaKind/public_id/URL are not client claims anymore. proofType is bound to
     * the upload session and media metadata is verified server-side against Cloudinary.
     */
    public record CreateRecentProofRequest(
            @NotNull UUID uploadSessionId,
            @NotNull @Positive Long version,
            @NotBlank String responseSignature,
            @Size(max = 500) String note
    ) {}

    public record RecentProofResponse(
        Long id,
        Long restaurantId,
        ProofType proofType,
        MediaKind mediaKind,
        String mediaUrl,
        String note,
        RecentProofStatus status,
        OffsetDateTime uploadedAt
    ) {}
}
