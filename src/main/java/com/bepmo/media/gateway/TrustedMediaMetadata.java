package com.bepmo.media.gateway;

import com.bepmo.media.entity.MediaResourceType;

public record TrustedMediaMetadata(
        String publicId,
        long version,
        MediaResourceType resourceType,
        String deliveryType,
        String format,
        long bytes,
        Integer width,
        Integer height,
        Double durationSeconds,
        String secureUrl
) {}
