package com.bepmo.media.dto;

import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.recentproof.entity.ProofType;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public class MediaUploadDtos {

    public record CreateUploadSessionRequest(
            @NotNull Long restaurantId,
            @NotNull MediaUploadPurpose purpose,
            VideoType profileVideoType,
            ProofType recentProofType
    ) {}

    public record UploadSessionResponse(
            UUID uploadSessionId,
            String uploadUrl,
            String cloudName,
            String apiKey,
            long timestamp,
            String signature,
            String publicId,
            String resourceType,
            boolean overwrite,
            String uploadPreset,
            OffsetDateTime expiresAt
    ) {}
}
