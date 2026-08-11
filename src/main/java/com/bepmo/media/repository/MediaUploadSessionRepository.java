package com.bepmo.media.repository;

import com.bepmo.media.entity.MediaUploadSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaUploadSessionRepository extends JpaRepository<MediaUploadSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MediaUploadSession s WHERE s.id = :id")
    Optional<MediaUploadSession> findByIdForUpdate(@Param("id") UUID id);
}
