package com.bepmo.media.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaResourceType;
import com.bepmo.media.entity.MediaUploadSession;
import com.bepmo.media.gateway.CloudinaryMediaGateway;
import com.bepmo.media.gateway.MediaValidationException;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Performs all Cloudinary verification before the DB finalization transaction begins.
 */
@Service
public class MediaVerificationService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final double MAX_VIDEO_DURATION_SECONDS = 60.0;
    private static final int METADATA_POLL_ATTEMPTS = 6;
    private static final long METADATA_POLL_DELAY_MILLIS = 1_000L;
    private static final Set<String> IMAGE_FORMATS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_FORMATS = Set.of("mp4", "webm", "mov");

    private final CloudinaryMediaGateway cloudinaryGateway;
    private final MediaUploadSessionStateService stateService;
    private final int metadataPollAttempts;
    private final long metadataPollDelayMillis;
    private final Sleeper sleeper;

    @Autowired
    public MediaVerificationService(
            CloudinaryMediaGateway cloudinaryGateway,
            MediaUploadSessionStateService stateService
    ) {
        this(
                cloudinaryGateway,
                stateService,
                METADATA_POLL_ATTEMPTS,
                METADATA_POLL_DELAY_MILLIS,
                Thread::sleep
        );
    }

    MediaVerificationService(
            CloudinaryMediaGateway cloudinaryGateway,
            MediaUploadSessionStateService stateService,
            int metadataPollAttempts,
            long metadataPollDelayMillis,
            Sleeper sleeper
    ) {
        this.cloudinaryGateway = cloudinaryGateway;
        this.stateService = stateService;
        this.metadataPollAttempts = Math.max(1, metadataPollAttempts);
        this.metadataPollDelayMillis = Math.max(0L, metadataPollDelayMillis);
        this.sleeper = sleeper;
    }

    public TrustedMediaMetadata verify(
            MediaUploadSession session,
            long responseVersion,
            String responseSignature
    ) {
        try {
            cloudinaryGateway.verifyUploadResponseSignature(
                    session.getExpectedPublicId(),
                    responseVersion,
                    responseSignature
            );

            return fetchAndValidateMetadata(session, responseVersion);
        } catch (MediaValidationException ex) {
            stateService.rejectIfOpen(session.getId(), ex.getMessage());
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }

    private TrustedMediaMetadata fetchAndValidateMetadata(
            MediaUploadSession session,
            long responseVersion
    ) {
        for (int attempt = 1; attempt <= metadataPollAttempts; attempt++) {
            try {
                TrustedMediaMetadata metadata = cloudinaryGateway.fetchTrustedMetadata(
                        session.getExpectedPublicId(),
                        session.getResourceType()
                );
                validateMetadata(session, responseVersion, metadata);
                return metadata;
            } catch (VideoMetadataNotReadyException ex) {
                if (attempt == metadataPollAttempts) {
                    throw new AppException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Cloudinary video metadata is still processing. Retry finalization."
                    );
                }
                pauseBeforeRetry();
            } catch (AppException ex) {
                boolean retryableFetchFailure = session.getResourceType() == MediaResourceType.VIDEO
                        && ex.getStatus() == HttpStatus.BAD_GATEWAY;
                if (!retryableFetchFailure || attempt == metadataPollAttempts) {
                    throw ex;
                }
                pauseBeforeRetry();
            }
        }
        throw new IllegalStateException("Metadata polling ended without a result");
    }

    private void pauseBeforeRetry() {
        try {
            sleeper.sleep(metadataPollDelayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary metadata verification was interrupted. Retry finalization."
            );
        }
    }

    private void validateMetadata(
            MediaUploadSession session,
            long responseVersion,
            TrustedMediaMetadata metadata
    ) {
        if (!session.getExpectedPublicId().equals(metadata.publicId())) {
            reject("Cloudinary public_id does not match the upload session");
        }
        if (metadata.version() <= 0 || metadata.version() != responseVersion) {
            reject("Cloudinary asset version does not match the signed upload response");
        }
        if (metadata.resourceType() != session.getResourceType()) {
            reject("Cloudinary resource_type does not match the upload session");
        }
        if (!"upload".equalsIgnoreCase(metadata.deliveryType())) {
            reject("Cloudinary delivery type is not allowed");
        }
        if (!StringUtils.hasText(metadata.secureUrl()) || !metadata.secureUrl().startsWith("https://")) {
            reject("Cloudinary secure_url is missing or invalid");
        }
        if (metadata.bytes() < 0) {
            reject("Cloudinary file size metadata is invalid");
        }

        String format = metadata.format() == null
                ? ""
                : metadata.format().toLowerCase(Locale.ROOT);

        if (session.getResourceType() == MediaResourceType.IMAGE) {
            if (metadata.bytes() == 0) {
                reject("Cloudinary file size metadata is invalid");
            }
            if (metadata.bytes() > MAX_IMAGE_BYTES) {
                reject("Image exceeds the 10 MiB limit");
            }
            if (!IMAGE_FORMATS.contains(format)) {
                reject("Image format is not allowed");
            }
            if (metadata.width() == null || metadata.width() <= 0
                    || metadata.height() == null || metadata.height() <= 0) {
                reject("Cloudinary image dimensions are invalid");
            }
            return;
        }

        if (metadata.bytes() == 0) {
            metadataNotReady();
        }
        if (metadata.bytes() > MAX_VIDEO_BYTES) {
            reject("Video exceeds the 100 MiB limit");
        }
        if (!VIDEO_FORMATS.contains(format)) {
            reject("Video format is not allowed");
        }
        if (metadata.width() == null || metadata.width() == 0
                || metadata.height() == null || metadata.height() == 0) {
            metadataNotReady();
        }
        if (metadata.width() < 0 || metadata.height() < 0) {
            reject("Cloudinary video dimensions are invalid");
        }
        if (metadata.durationSeconds() == null || metadata.durationSeconds() == 0) {
            metadataNotReady();
        }
        if (!Double.isFinite(metadata.durationSeconds())
                || metadata.durationSeconds() < 0
                || metadata.durationSeconds() > MAX_VIDEO_DURATION_SECONDS) {
            reject("Video duration must be greater than 0 and at most 60 seconds");
        }
    }

    private void reject(String message) {
        throw new MediaValidationException(message);
    }

    private void metadataNotReady() {
        throw new VideoMetadataNotReadyException();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final class VideoMetadataNotReadyException extends RuntimeException {
    }
}
