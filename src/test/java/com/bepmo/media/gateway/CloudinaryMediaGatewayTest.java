package com.bepmo.media.gateway;

import com.bepmo.media.entity.MediaResourceType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudinaryMediaGatewayTest {

    @Test
    void verifyUploadResponseSignature_acceptsValidResponseSignature() {
        CloudinaryMediaGateway gateway = gatewayWithSecret("abcd");

        assertDoesNotThrow(() -> gateway.verifyUploadResponseSignature(
                "sample",
                1315060510L,
                "912d90b6fe28aa6820cf928bc440a65a0f36e002"
        ));
    }

    @Test
    void verifyUploadResponseSignature_rejectsTampering() {
        CloudinaryMediaGateway gateway = gatewayWithSecret("abcd");

        assertThrows(MediaValidationException.class, () -> gateway.verifyUploadResponseSignature(
                "sample",
                1315060510L,
                "912d90b6fe28aa6820cf928bc440a65a0f36e003"
        ));
    }

    @Test
    void metadataResourceOptions_requestsVideoMediaMetadata() {
        Map<String, Object> options = CloudinaryMediaGateway.metadataResourceOptions(
                MediaResourceType.VIDEO
        );

        assertEquals("video", options.get("resource_type"));
        assertEquals("upload", options.get("type"));
        assertEquals(Boolean.TRUE, options.get("media_metadata"));
    }

    @Test
    void parseTrustedMetadata_readsDurationFromRealVideoUploadResponse() {
        Map<String, Object> response = realVideoResponse();

        TrustedMediaMetadata metadata = CloudinaryMediaGateway.parseTrustedMetadata(response);

        assertEquals(
                "bep-mo/restaurants/1/profile-videos/d1b7685d-55f3-447f-a692-7667ed7abc68",
                metadata.publicId()
        );
        assertEquals(1786520351L, metadata.version());
        assertEquals(MediaResourceType.VIDEO, metadata.resourceType());
        assertEquals(68_296_165L, metadata.bytes());
        assertEquals(3840, metadata.width());
        assertEquals(2160, metadata.height());
        assertEquals(6.7067, metadata.durationSeconds(), 0.000001);
    }

    @Test
    void parseTrustedMetadata_fallsBackToNestedMediaMetadataDuration() {
        Map<String, Object> response = realVideoResponse();
        response.remove("duration");
        response.put("media_metadata", Map.of(
                "video", Map.of("duration", "6.7067")
        ));

        TrustedMediaMetadata metadata = CloudinaryMediaGateway.parseTrustedMetadata(response);

        assertEquals(6.7067, metadata.durationSeconds(), 0.000001);
    }

    private Map<String, Object> realVideoResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("public_id", "bep-mo/restaurants/1/profile-videos/d1b7685d-55f3-447f-a692-7667ed7abc68");
        response.put("version", 1786520351L);
        response.put("width", 3840);
        response.put("height", 2160);
        response.put("format", "mp4");
        response.put("resource_type", "video");
        response.put("bytes", 68_296_165L);
        response.put("type", "upload");
        response.put("secure_url", "https://res.cloudinary.com/demo/video/upload/v1786520351/test.mp4");
        response.put("duration", 6.7067);
        return response;
    }

    private CloudinaryMediaGateway gatewayWithSecret(String secret) {
        return new CloudinaryMediaGateway("demo", "test-key", secret, "");
    }
}
