package com.bepmo.recentproof;

import com.bepmo.common.exception.AppException;
import com.bepmo.recentproof.dto.RecentProofDtos.*;
import com.bepmo.recentproof.entity.MediaKind;
import com.bepmo.recentproof.entity.ProofType;
import com.bepmo.recentproof.entity.RecentProof;
import com.bepmo.recentproof.entity.RecentProofStatus;
import com.bepmo.recentproof.repository.RecentProofRepository;
import com.bepmo.recentproof.service.RecentProofService;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.transparencyscore.service.TransparencyScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecentProofServiceTest {

    @Mock RecentProofRepository recentProofRepository;
    @Mock RestaurantService restaurantService;
    @Mock TransparencyScoreService transparencyScoreService;

    @InjectMocks RecentProofService recentProofService;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().id(1L).ownerId(10L).status(RestaurantStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("create: proofType RECEIVING_VIDEO → mediaKind suy ra là VIDEO")
    void create_receivingVideo_derivesVideoMediaKind() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        ArgumentCaptor<RecentProof> captor = ArgumentCaptor.forClass(RecentProof.class);
        when(recentProofRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        recentProofService.create(1L, 10L, new CreateRecentProofRequest(
                ProofType.RECEIVING_VIDEO, "https://cdn/v.mp4", "pub-1", null));

        assertThat(captor.getValue().getMediaKind()).isEqualTo(MediaKind.VIDEO);
    }

    @Test
    @DisplayName("create: proofType khác (vd INVOICE) → mediaKind suy ra là IMAGE")
    void create_invoice_derivesImageMediaKind() {
        when(restaurantService.requireOwnedRestaurant(1L, 10L)).thenReturn(restaurant);
        ArgumentCaptor<RecentProof> captor = ArgumentCaptor.forClass(RecentProof.class);
        when(recentProofRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        recentProofService.create(1L, 10L, new CreateRecentProofRequest(
                ProofType.INVOICE, "https://cdn/i.jpg", "pub-2", "hoa don thang 7"));

        assertThat(captor.getValue().getMediaKind()).isEqualTo(MediaKind.IMAGE);
        verify(transparencyScoreService).evictCache(1L);
    }

    @Test
    @DisplayName("delete: proof thuộc quán khác (path variable sai) → 404")
    void delete_proofBelongsToDifferentRestaurant() {
        RecentProof proof = RecentProof.builder().id(300L).restaurantId(1L)
                .proofType(ProofType.INVOICE).mediaKind(MediaKind.IMAGE)
                .status(RecentProofStatus.ACTIVE).build();

        when(restaurantService.requireOwnedRestaurant(2L, 10L)).thenReturn(restaurant);
        when(recentProofRepository.findById(300L)).thenReturn(Optional.of(proof));

        assertThatThrownBy(() -> recentProofService.delete(2L, 300L, 10L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("listRecentActive: gọi đúng repository method top3 theo uploadedAt desc")
    void listRecentActive_callsTop3Query() {
        when(recentProofRepository.findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE))
                .thenReturn(java.util.List.of());

        recentProofService.listRecentActive(1L);

        verify(recentProofRepository).findTop3ByRestaurantIdAndStatusOrderByUploadedAtDesc(1L, RecentProofStatus.ACTIVE);
    }
}
