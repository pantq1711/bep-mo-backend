package com.bepmo.media.controller;

import com.bepmo.media.dto.MediaUploadDtos.CreateUploadSessionRequest;
import com.bepmo.media.dto.MediaUploadDtos.UploadSessionResponse;
import com.bepmo.media.service.MediaUploadSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media/upload-sessions")
@RequiredArgsConstructor
@Tag(name = "MediaUpload", description = "Short-lived capability-based signed Cloudinary upload sessions")
public class MediaUploadController {

    private final MediaUploadSessionService mediaUploadSessionService;

    @PostMapping
    @Operation(summary = "Issue a signed direct-to-Cloudinary upload session (restaurant owner only)")
    public ResponseEntity<UploadSessionResponse> issue(
            @AuthenticationPrincipal Long currentUserId,
            @Valid @RequestBody CreateUploadSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaUploadSessionService.issue(currentUserId, request));
    }
}
