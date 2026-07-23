package com.bepmo.recentproof.dto;

import com.bepmo.recentproof.entity.MediaKind;
import com.bepmo.recentproof.entity.ProofType;
import com.bepmo.recentproof.entity.RecentProofStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class RecentProofDtos {

    // mediaKind KHÔNG có trong request — suy ra từ proofType ở Service layer theo
    // rule compatibility (RECEIVING_VIDEO -> VIDEO, còn lại -> IMAGE), tránh client
    // gửi mediaKind sai lệch với proofType.
    public record CreateRecentProofRequest(
        @NotNull ProofType proofType,
        @NotBlank String mediaUrl,
        @NotBlank String cloudinaryPublicId,
        @Size(max = 500) String note
    ) {}

    public record RecentProofResponse(
        Long id,
        Long restaurantId,
        ProofType proofType,
        MediaKind mediaKind,
        String mediaUrl,
        String note,
        RecentProofStatus status,
        OffsetDateTime uploadedAt
    ) {}
}
