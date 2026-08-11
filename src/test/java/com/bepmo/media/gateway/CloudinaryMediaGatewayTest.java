package com.bepmo.media.gateway;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudinaryMediaGatewayTest {

    @Test
    void signParameters_matchesCloudinaryDocumentedUploadSignatureExample() {
        Map<String, Object> params = new LinkedHashMap<>();
        // Deliberately insert in reverse order: signing must sort keys alphabetically.
        params.put("timestamp", 1315060510L);
        params.put("public_id", "sample_image");

        assertEquals(
                "b4ad47fb4e25c7bf5f92a20089f9db59bc302313",
                CloudinaryMediaGateway.signParameters(params, "abcd")
        );
    }
}
