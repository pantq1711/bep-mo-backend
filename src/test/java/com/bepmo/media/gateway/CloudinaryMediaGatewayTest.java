package com.bepmo.media.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    private CloudinaryMediaGateway gatewayWithSecret(String secret) {
        return new CloudinaryMediaGateway("demo", "test-key", secret, "");
    }
}
