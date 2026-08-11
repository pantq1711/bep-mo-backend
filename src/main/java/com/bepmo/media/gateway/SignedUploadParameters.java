package com.bepmo.media.gateway;

public record SignedUploadParameters(
        String uploadUrl,
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        String publicId,
        String resourceType,
        boolean overwrite,
        String uploadPreset
) {}
