package com.bepmo.profilevideo.dto;

import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class ProfileVideoDtos {

    public record UploadVideoRequest(
        @NotNull  VideoType type,
        @NotBlank String cloudinaryUrl,
        @NotBlank String cloudinaryPublicId,
        String thumbnailUrl,
        @NotNull @Min(1) @Max(60) Integer durationSeconds,
        @NotNull @Min(1) Long fileSizeBytes
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
