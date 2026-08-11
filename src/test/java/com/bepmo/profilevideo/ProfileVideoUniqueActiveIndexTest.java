package com.bepmo.profilevideo;

import com.bepmo.profilevideo.entity.ProfileVideo;
import com.bepmo.profilevideo.entity.VideoStatus;
import com.bepmo.profilevideo.entity.VideoType;
import com.bepmo.profilevideo.repository.ProfileVideoRepository;
import com.bepmo.restaurant.entity.Restaurant;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.restaurant.repository.RestaurantRepository;
import com.bepmo.user.entity.User;
import com.bepmo.user.entity.UserRole;
import com.bepmo.user.entity.UserStatus;
import com.bepmo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Integration test dùng Postgres thật (Testcontainers) thay vì H2/mock — vì mục tiêu là
 * verify ràng buộc uq_profile_videos_one_active_per_type (partial unique index, WHERE
 * status='ACTIVE') được định nghĩa trong V1__init_schema.sql. Hibernate KHÔNG biết gì về
 * ràng buộc này (nó không phải @UniqueConstraint trên entity), nên unit test với mock
 * repository sẽ luôn pass dù constraint DB có đúng hay không — chỉ test tích hợp với DB
 * thật mới bắt được lỗi thật.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProfileVideoUniqueActiveIndexTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bepmo_test")
            .withUsername("bepmo")
            .withPassword("bepmo123");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private ProfileVideoRepository profileVideoRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long restaurantId;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder()
                .email("owner-" + System.nanoTime() + "@bepmo.test")
                .passwordHash("hash")
                .role(UserRole.RESTAURANT_OWNER)
                .status(UserStatus.ACTIVE)
                .build());

        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .ownerId(owner.getId())
                .name("Quan Test")
                .address("123 Test Street")
                .status(RestaurantStatus.ACTIVE)
                .build());

        restaurantId = restaurant.getId();
    }

    @Test
    @DisplayName("2 video ACTIVE cùng type cho cùng 1 quán -> DB reject bằng DataIntegrityViolationException")
    void cannotHaveTwoActiveVideosOfSameType() {
        profileVideoRepository.saveAndFlush(newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE));

        assertThatThrownBy(() ->
                profileVideoRepository.saveAndFlush(newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("2 video ACTIVE khác type cho cùng 1 quán -> OK, partial index chỉ áp dụng theo (restaurant_id, type)")
    void differentTypesCanBothBeActive() {
        profileVideoRepository.saveAndFlush(newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE));
        profileVideoRepository.saveAndFlush(newVideo(VideoType.HYGIENE, VideoStatus.ACTIVE));

        assertThat(profileVideoRepository.findByRestaurantIdAndStatus(restaurantId, VideoStatus.ACTIVE))
                .hasSize(2);
    }

    @Test
    @DisplayName("1 video ACTIVE + 1 video REPLACED cùng type -> OK, index chỉ áp dụng khi status='ACTIVE'")
    void replacedVideoDoesNotConflictWithActiveOfSameType() {
        profileVideoRepository.saveAndFlush(newVideo(VideoType.PREP, VideoStatus.REPLACED));

        assertThatCode(() -> profileVideoRepository.saveAndFlush(newVideo(VideoType.PREP, VideoStatus.ACTIVE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("replaceActive() demote video ACTIVE cũ -> insert video mới cùng type không còn bị conflict")
    void replaceActiveAllowsInsertingNewVideoOfSameType() {
        ProfileVideo oldVideo = profileVideoRepository.saveAndFlush(newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE));

        int updated = profileVideoRepository.replaceActive(
                restaurantId, VideoType.KITCHEN, VideoStatus.REPLACED, VideoStatus.ACTIVE);
        profileVideoRepository.flush();

        assertThat(updated).isEqualTo(1);

        // Không throw — vì video cũ đã bị demote khỏi ACTIVE
        ProfileVideo newVideo = profileVideoRepository.saveAndFlush(newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE));

        profileVideoRepository.findById(oldVideo.getId())
                .ifPresent(v -> assertThat(v.getStatus()).isEqualTo(VideoStatus.REPLACED));
        assertThat(newVideo.getStatus()).isEqualTo(VideoStatus.ACTIVE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("2 replace đồng thời + lock restaurant → cả hai transaction thành công, cuối cùng chỉ 1 ACTIVE")
    void concurrentReplaceWithRestaurantLock_serializesLastWriterWins() throws Exception {
        profileVideoRepository.saveAndFlush(newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Long> replaceTask = () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent replace");
                }

                return new TransactionTemplate(transactionManager).execute(status -> {
                    restaurantRepository.findByIdForUpdate(restaurantId).orElseThrow();
                    profileVideoRepository.replaceActive(
                            restaurantId, VideoType.KITCHEN, VideoStatus.REPLACED, VideoStatus.ACTIVE);
                    return profileVideoRepository.saveAndFlush(
                            newVideo(VideoType.KITCHEN, VideoStatus.ACTIVE)).getId();
                });
            };

            Future<Long> first = executor.submit(replaceTask);
            Future<Long> second = executor.submit(replaceTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull();
            assertThat(second.get(10, TimeUnit.SECONDS)).isNotNull();

            assertThat(profileVideoRepository.findByRestaurantIdAndStatus(restaurantId, VideoStatus.ACTIVE))
                    .hasSize(1);
            assertThat(profileVideoRepository.findByRestaurantIdAndStatus(restaurantId, VideoStatus.REPLACED))
                    .hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProfileVideo newVideo(VideoType type, VideoStatus status) {
        return ProfileVideo.builder()
                .restaurantId(restaurantId)
                .type(type)
                .cloudinaryUrl("https://cloudinary.test/video.mp4")
                .cloudinaryPublicId("public-id-" + System.nanoTime())
                .durationSeconds(30)
                .fileSizeBytes(1_000_000L)
                .status(status)
                .build();
    }
}
