package com.bepmo.profilevideo.repository;

import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfileVideoRepository extends JpaRepository<ProfileVideo, Long> {

    boolean existsByRestaurantIdAndTypeAndStatus(Long restaurantId, VideoType type, VideoStatus status);

    Optional<ProfileVideo> findByRestaurantIdAndTypeAndStatus(Long restaurantId, VideoType type, VideoStatus status);

    List<ProfileVideo> findByRestaurantIdAndStatus(Long restaurantId, VideoStatus status);

    // Used when replacing a video: mark old ACTIVE as REPLACED
    // Bind cả oldStatus/newStatus qua enum param — không dùng string literal trong JPQL,
    // tránh lỗi nếu enum value đổi tên sau này (compiler sẽ báo lỗi thay vì fail lúc runtime)
    @Modifying
    @Query("""
        UPDATE ProfileVideo pv
        SET pv.status = :newStatus
        WHERE pv.restaurantId = :restaurantId
          AND pv.type = :type
          AND pv.status = :oldStatus
        """)
    int replaceActive(Long restaurantId, VideoType type, VideoStatus newStatus, VideoStatus oldStatus);
}
