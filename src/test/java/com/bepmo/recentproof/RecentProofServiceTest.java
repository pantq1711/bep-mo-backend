package com.bepmo.recentproof;

import com.bepmo.common.exception.AppException;
import com.bepmo.media.entity.MediaResourceType;
import com.bepmo.media.entity.MediaUploadPurpose;
import com.bepmo.media.entity.MediaUploadSession;
import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.gateway.TrustedMediaMetadata;
import com.bepmo.media.service.MediaUploadSessionService;
import com.bepmo.media.service.MediaVerificationService;
import com.bepmo.recentproof.dto.RecentProofDtos.CreateRecentProofRequest;
import com.bepmo.recentproof.entity.MediaKind;
import com.bepmo.recentproof.entity.ProofType;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.recentproof.service.RecentProofFinalizationService;
import com.bepmo.recentproof.service.RecentProofService;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecentProofServiceTest {

    @Mock RecentProofRepository recentProofRepository;
    @Mock RestaurantService restaurantService;
    @Mock TransparencyScoreService transparencyScoreService;
    @Mock MediaUploadSessionService mediaUploadSessionService;
    @Mock MediaVerificationService mediaVerificationService;
    @Mock RecentProofFinalizationService finalizationService;

    @InjectMocks RecentProofService recentProofService;

    private Restaurant restaurant;
    private MediaUploadSession session;
    private RecentProof proof;
    private TrustedMediaMetadata metadata;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        restaurant = Restaurant.builder().id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
        session = MediaUploadSession.builder()
                .id(sessionId).ownerId(10L).restaurantId(1L)
                .purpose(MediaUploadPurpose.RECENT_PROOF)
                .recentProofType(ProofType.INVOICE)
                .resourceType(MediaResourceType.IMAGE)
                .expectedPublicId("bep-mo/proof")
                .status(MediaUploadSessionStatus.ISSUED)
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();
        proof = RecentProof.builder()
                .id(300L).restaurantId(1L).proofType(ProofType.INVOICE)
                .mediaKind(MediaKind.IMAGE).mediaUrl("https://cdn/p.jpg")
                .cloudinaryPublicId("bep-mo/proof").mediaUploadSessionId(sessionId)
                .status(RecentProofStatus.ACTIVE).build();
        metadata = new TrustedMediaMetadata(
                "bep-mo/proof", 9L, MediaResourceType.IMAGE, "upload", "jpg",
                3000L, 1000, 800, null, "https://cdn/p.jpg"
        );
    }

    @Test
    @DisplayName("create: Cloudinary verification xảy ra trước short DB finalization")
    void create_verifiesBeforeFinalization() {
        when(mediaUploadSessionService.requireAuthorizedForPublish(
                sessionId, 10L, 1L, MediaUploadPurpose.RECENT_PROOF)).thenReturn(session);
        when(recentProofRepository.findByMediaUploadSessionId(sessionId)).thenReturn(Optional.empty());
        when(mediaVerificationService.verify(session, 9L, "sig")).thenReturn(metadata);
        when(finalizationService.finalizeUpload(1L, 10L, sessionId, metadata, "invoice"))
                .thenReturn(proof);

        recentProofService.create(1L, 10L, new CreateRecentProofRequest(sessionId, 9L, "sig", "invoice"));

        InOrder order = inOrder(mediaVerificationService, finalizationService);
        order.verify(mediaVerificationService).verify(session, 9L, "sig");
        order.verify(finalizationService).finalizeUpload(1L, 10L, sessionId, metadata, "invoice");
    }

    @Test
    @DisplayName("create retry: CONSUMED session trả record cũ và bypass Cloudinary verification")
    void create_consumedRetryBypassesCloudinary() {
        session.setStatus(MediaUploadSessionStatus.CONSUMED);
        when(mediaUploadSessionService.requireAuthorizedForPublish(
                sessionId, 10L, 1L, MediaUploadPurpose.RECENT_PROOF)).thenReturn(session);
        when(recentProofRepository.findByMediaUploadSessionId(sessionId)).thenReturn(Optional.of(proof));

        var result = recentProofService.create(
                1L, 10L, new CreateRecentProofRequest(sessionId, 9L, "sig", "invoice"));

        assertThat(result.id()).isEqualTo(300L);
        verifyNoInteractions(mediaVerificationService, finalizationService);
    }

    @Test
    @DisplayName("delete: proof thuộc quán khác (path variable sai) → 404")
    void delete_proofBelongsToDifferentRestaurant() {
        when(restaurantService.requireOwnedRestaurantForUpdate(2L, 10L)).thenReturn(restaurant);
        when(recentProofRepository.findById(300L)).thenReturn(Optional.of(proof));

        assertThatThrownBy(() -> recentProofService.delete(2L, 300L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("listRecentActive: gọi đúng repository method top3 theo uploadedAt desc")
    void listRecentActive_callsTop3Query() {
        when(restaurantService.requireViewableRestaurant(1L, null)).thenReturn(restaurant);
        when(recentProofRepository.findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(java.util.List.of());

        recentProofService.listRecentActive(1L, null);

        verify(recentProofRepository).findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE);
    }
}
