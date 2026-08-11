package com.bepmo.profilevideo.dto;

import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProfileVideoDtos {

    /**
     * Finalization payload only. Video type/public_id/URL/size/duration are bound to the
     * upload session or fetched server-side from Cloudinary; the browser cannot assert them.
     */
    public record UploadVideoRequest(
            @NotNull UUID uploadSessionId,
            @NotNull @Positive Long version,
            @NotBlank String responseSignature
    ) {}

    public record ProfileVideoResponse(
        Long id,
        Long restaurantId,
        VideoType type,
        String cloudinaryUrl,
        String thumbnailUrl,
        Integer durationSeconds,
        VideoStatus status,
        OffsetDateTime createdAt
    ) {}
}
