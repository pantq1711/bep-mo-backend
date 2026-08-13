package com.bepmo.media.service;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaResourceType;
import com.bepmo.media.entity.MediaUploadSession;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.CloudinaryMediaGateway;
import com.bepmo.media.gateway.MediaValidationException;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaVerificationServiceTest {

    private static final long RESPONSE_VERSION = 1315060510L;
    private static final String RESPONSE_SIGNATURE = "signed-response";

    @Mock CloudinaryMediaGateway cloudinaryGateway;
    @Mock MediaUploadSessionStateService stateService;

    private MediaVerificationService service;

    @BeforeEach
    void setUp() {
        service = new MediaVerificationService(
                cloudinaryGateway,
                stateService,
                3,
                0L,
                ignored -> { }
        );
    }

    @Test
    void verify_retriesWhenVideoDurationIsTemporarilyMissing() {
        MediaUploadSession session = videoSession();
        TrustedMediaMetadata pending = videoMetadata(null);
        TrustedMediaMetadata ready = videoMetadata(7.0);
        when(cloudinaryGateway.fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        )).thenReturn(pending, ready);

        TrustedMediaMetadata result = service.verify(
                session, RESPONSE_VERSION, RESPONSE_SIGNATURE
        );

        assertSame(ready, result);
        verify(cloudinaryGateway, times(2)).fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        );
        verifyNoInteractions(stateService);
    }

    @Test
    void verify_keepsSessionOpenWhenVideoMetadataIsStillProcessing() {
        MediaUploadSession session = videoSession();
        when(cloudinaryGateway.fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        )).thenReturn(videoMetadata(0.0));

        AppException error = assertThrows(
                AppException.class,
                () -> service.verify(session, RESPONSE_VERSION, RESPONSE_SIGNATURE)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals(
                "Cloudinary video metadata is still processing. Retry finalization.",
                error.getMessage()
        );
        verify(cloudinaryGateway, times(3)).fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        );
        verifyNoInteractions(stateService);
    }

    @Test
    void verify_rejectsConfirmedVideoOverDurationLimit() {
        MediaUploadSession session = videoSession();
        when(cloudinaryGateway.fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        )).thenReturn(videoMetadata(60.01));

        AppException error = assertThrows(
                AppException.class,
                () -> service.verify(session, RESPONSE_VERSION, RESPONSE_SIGNATURE)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        assertEquals(
                "Video duration must be greater than 0 and at most 60 seconds",
                error.getMessage()
        );
        verify(stateService).rejectIfOpen(session.getId(), error.getMessage());
        verify(cloudinaryGateway).fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        );
    }

    @Test
    void verify_rejectsInvalidUploadResponseSignatureWithoutFetchingMetadata() {
        MediaUploadSession session = videoSession();
        doThrow(new MediaValidationException("Cloudinary upload response signature is invalid"))
                .when(cloudinaryGateway).verifyUploadResponseSignature(
                session.getExpectedPublicId(), RESPONSE_VERSION, RESPONSE_SIGNATURE
        );

        AppException error = assertThrows(
                AppException.class,
                () -> service.verify(session, RESPONSE_VERSION, RESPONSE_SIGNATURE)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(stateService).rejectIfOpen(session.getId(), error.getMessage());
        verify(cloudinaryGateway, never()).fetchTrustedMetadata(
                session.getExpectedPublicId(), MediaResourceType.VIDEO
        );
    }

    private MediaUploadSession videoSession() {
        return MediaUploadSession.builder()
                .id(UUID.randomUUID())
                .ownerId(10L)
                .restaurantId(20L)
                .resourceType(MediaResourceType.VIDEO)
                .expectedPublicId("bep-mo/restaurants/20/profile-videos/test")
                .status(MediaUploadSessionStatus.ISSUED)
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .build();
    }

    private TrustedMediaMetadata videoMetadata(Double durationSeconds) {
        return new TrustedMediaMetadata(
                "bep-mo/restaurants/20/profile-videos/test",
                RESPONSE_VERSION,
                MediaResourceType.VIDEO,
                "upload",
                "mp4",
                63L * 1024 * 1024,
                1920,
                1080,
                durationSeconds,
                "https://res.cloudinary.com/demo/video/upload/test.mp4"
        );
    }
}
