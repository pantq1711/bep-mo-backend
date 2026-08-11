package com.bepmo.media.service;

import com.bepmo.media.entity.MediaUploadSessionStatus;
import com.bepmo.media.repository.MediaUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Persists terminal pre-finalization states in their own short DB transaction. */
@Service
@RequiredArgsConstructor
public class MediaUploadSessionStateService {

    private final MediaUploadSessionRepository sessionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectIfOpen(UUID sessionId, String reason) {
        sessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> {
            if (session.getStatus() == MediaUploadSessionStatus.ISSUED
                    || session.getStatus() == MediaUploadSessionStatus.VALIDATED) {
                session.setStatus(MediaUploadSessionStatus.REJECTED);
                session.setRejectionReason(truncate(reason));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireIfOpen(UUID sessionId) {
        sessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> {
            if (session.getStatus() == MediaUploadSessionStatus.ISSUED
                    || session.getStatus() == MediaUploadSessionStatus.VALIDATED) {
                session.setStatus(MediaUploadSessionStatus.EXPIRED);
            }
        });
    }

    private String truncate(String reason) {
        if (reason == null) return "Media verification rejected";
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
