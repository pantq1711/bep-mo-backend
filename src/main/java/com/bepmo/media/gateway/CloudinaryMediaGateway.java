package com.bepmo.media.gateway;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaResourceType;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single trust boundary for Cloudinary.
 *
 * Domain/application services never sign raw parameters or call the Admin API directly.
 * The API secret stays in this backend-only component and is never returned to the browser.
 */
@Component
public class CloudinaryMediaGateway {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String uploadPreset;
    private final Cloudinary cloudinary;

    public CloudinaryMediaGateway(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.upload-preset:}") String uploadPreset
    ) {
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.uploadPreset = uploadPreset == null ? "" : uploadPreset.trim();
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", this.cloudName,
                "api_key", this.apiKey,
                "api_secret", this.apiSecret,
                "secure", true
        ));
    }

    /**
     * Generates a short-lived signed browser-upload capability. This is local crypto only;
     * no Cloudinary network call occurs while the upload-session DB transaction is open.
     */
    public SignedUploadParameters signBrowserUpload(String publicId, MediaResourceType resourceType) {
        requireConfigured();

        long timestamp = Instant.now().getEpochSecond();
        Map<String, Object> paramsToSign = new LinkedHashMap<>();
        paramsToSign.put("timestamp", timestamp);
        paramsToSign.put("public_id", publicId);
        paramsToSign.put("overwrite", false);
        if (StringUtils.hasText(uploadPreset)) {
            paramsToSign.put("upload_preset", uploadPreset);
        }

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);
        String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/"
                + resourceType.cloudinaryValue() + "/upload";

        return new SignedUploadParameters(
                uploadUrl,
                cloudName,
                apiKey,
                timestamp,
                signature,
                publicId,
                resourceType.cloudinaryValue(),
                false,
                StringUtils.hasText(uploadPreset) ? uploadPreset : null
        );
    }

    /**
     * Cloudinary's upload response signature covers public_id + version. Recompute it with
     * the backend API secret; never trust a signature value or public_id asserted by the client.
     */
    public void verifyUploadResponseSignature(String expectedPublicId, long version, String responseSignature) {
        requireConfigured();
        if (!StringUtils.hasText(responseSignature) || version <= 0) {
            throw new MediaValidationException("Cloudinary upload response is missing a valid version/signature");
        }

        String expected = cloudinary.apiSignRequest(
                ObjectUtils.asMap("public_id", expectedPublicId, "version", version),
                apiSecret
        );
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                responseSignature.trim().getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new MediaValidationException("Cloudinary upload response signature is invalid");
        }
    }

    /**
     * Server-side source of truth for asset metadata. This is external I/O and must be called
     * before opening the short finalization transaction.
     */
    public TrustedMediaMetadata fetchTrustedMetadata(String publicId, MediaResourceType resourceType) {
        requireConfigured();
        try {
            Map<?, ?> result = cloudinary.api().resource(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type", resourceType.cloudinaryValue(),
                            "type", "upload"
                    )
            );

            return new TrustedMediaMetadata(
                    stringValue(result.get("public_id")),
                    longValue(result.get("version")),
                    parseResourceType(result.get("resource_type")),
                    stringValue(result.get("type")),
                    stringValue(result.get("format")),
                    longValue(result.get("bytes")),
                    integerValue(result.get("width")),
                    integerValue(result.get("height")),
                    doubleValue(result.get("duration")),
                    stringValue(result.get("secure_url"))
            );
        } catch (MediaValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            // Keep the session ISSUED. The browser can retry finalization without re-uploading.
            throw new AppException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary metadata verification is temporarily unavailable. Retry finalization."
            );
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(cloudName)
                || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(apiSecret)) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary backend credentials are not configured"
            );
        }
    }

    private MediaResourceType parseResourceType(Object value) {
        String raw = stringValue(value);
        if ("image".equalsIgnoreCase(raw)) return MediaResourceType.IMAGE;
        if ("video".equalsIgnoreCase(raw)) return MediaResourceType.VIDEO;
        throw new MediaValidationException("Cloudinary resource_type is invalid");
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new MediaValidationException("Cloudinary numeric metadata is invalid");
        }
    }

    private Integer integerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new MediaValidationException("Cloudinary dimension metadata is invalid");
        }
    }

    private Double doubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new MediaValidationException("Cloudinary duration metadata is invalid");
        }
    }
}
