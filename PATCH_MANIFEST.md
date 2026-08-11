# Bếp Mở — Backend signed-media refactor patch

## Baseline đã xác minh

- Archive: `bep-mo-backend(5).rar`
- Branch/HEAD trong archive: `main @ 64c72a8`
- Archive có working-tree WIP từ trước. Patch này được tạo bằng cách so sánh **trực tiếp nội dung archive đã upload** với work copy sau refactor, không diff từ HEAD sạch, để không ghi đè WIP cũ.
- Không commit, không push.

## Thay đổi chính

- Thêm `POST /api/v1/media/upload-sessions` cho signed direct-to-Cloudinary upload capability, bind `ownerId + restaurantId + purpose + type + resourceType + expectedPublicId + expiresAt`.
- Thêm state machine `ISSUED -> VALIDATED -> CONSUMED`, cùng `REJECTED` và `EXPIRED`. `VALIDATED` là trạng thái nội bộ trong short transaction.
- Gom trust boundary Cloudinary vào `CloudinaryMediaGateway`: ký request, verify response signature, gọi Admin API lấy metadata gốc.
- Cloudinary Admin API/verification chạy ngoài DB finalization transaction; transaction chỉ lock/recheck session, idempotency, create resource, consume session và evict score cache.
- `ProfileVideo` và `RecentProof` có `media_upload_session_id` unique (nullable để tương thích dữ liệu legacy). Retry sau `CONSUMED` trả record cũ và bypass Cloudinary lookup.
- Publish payload tối giản: video = `uploadSessionId + version + responseSignature`; proof = các field đó + `note`. Type/publicId/URL/bytes/duration/mediaKind không còn là client claims.
- Policy backend: ảnh <= 10 MiB (`jpg/jpeg/png/webp`); video <= 100 MiB, <= 60 giây (`mp4/webm/mov`).
- Bổ sung Flyway `V4__signed_media_upload_sessions.sql`, Cloudinary backend config trong `application.yml`, `.example`, Docker Compose và `.env.example`.
- Cloudinary Java SDK pin `cloudinary-http5:2.3.2`.

## Migration

- `src/main/resources/db/migration/V4__signed_media_upload_sessions.sql`

## API contract mới

```json
POST /api/v1/media/upload-sessions
{
  "restaurantId": 1,
  "purpose": "PROFILE_VIDEO",
  "profileVideoType": "KITCHEN"
}
```

Hoặc recent proof:

```json
{
  "restaurantId": 1,
  "purpose": "RECENT_PROOF",
  "recentProofType": "INVOICE"
}
```

Finalize video:

```json
{
  "uploadSessionId": "<uuid>",
  "version": 1234567890,
  "responseSignature": "<cloudinary-response-signature>"
}
```

Finalize recent proof thêm `note` tùy chọn.

## QA đã thực hiện

- Verified Git/status/log của archive trước khi sửa.
- Static QA: không còn raw `HttpClient`; crypto/signature chỉ nằm trong `CloudinaryMediaGateway`; Admin API chỉ được gọi từ gateway/verification path ngoài finalization transaction.
- Static QA: kiểm tra client metadata cũ không còn trong upload/create request DTO.
- `git diff --check`: không có whitespace error trong task files.
- `git apply --check`: được kiểm tra lại trên bản copy của chính archive baseline khi đóng gói.
- `bash ./mvnw -q test`: **CHƯA CHẠY ĐƯỢC** — Maven Wrapper không tải được `apache-maven-3.9.16-bin.zip` từ Maven Central trong environment này. Không có khẳng định test pass.

## Known limitations / chưa verify runtime

- Chưa chạy real Cloudinary upload/Admin API vì không có credentials runtime.
- Chưa chạy PostgreSQL/Flyway integration và Docker E2E trong environment này.
- Webhook, orphan cleanup/reconciliation vẫn là optional/future work; asset upload xong nhưng session bị abandon/reject/expire có thể thành orphan.
- Signed upload preset là tùy chọn. Nếu cấu hình preset, nên đặt format/file-size constraints tại Cloudinary để reject sớm; backend vẫn verify lại metadata.

## File thêm/sửa (35)

- `.env.example`
- `docker-compose.yml`
- `pom.xml`
- `src/main/java/com/bepmo/config/SecurityConfig.java`
- `src/main/java/com/bepmo/media/controller/MediaUploadController.java`
- `src/main/java/com/bepmo/media/dto/MediaUploadDtos.java`
- `src/main/java/com/bepmo/media/entity/MediaResourceType.java`
- `src/main/java/com/bepmo/media/entity/MediaUploadPurpose.java`
- `src/main/java/com/bepmo/media/entity/MediaUploadSession.java`
- `src/main/java/com/bepmo/media/entity/MediaUploadSessionStatus.java`
- `src/main/java/com/bepmo/media/gateway/CloudinaryMediaGateway.java`
- `src/main/java/com/bepmo/media/gateway/MediaValidationException.java`
- `src/main/java/com/bepmo/media/gateway/SignedUploadParameters.java`
- `src/main/java/com/bepmo/media/gateway/TrustedMediaMetadata.java`
- `src/main/java/com/bepmo/media/repository/MediaUploadSessionRepository.java`
- `src/main/java/com/bepmo/media/service/MediaUploadSessionService.java`
- `src/main/java/com/bepmo/media/service/MediaUploadSessionStateService.java`
- `src/main/java/com/bepmo/media/service/MediaVerificationService.java`
- `src/main/java/com/bepmo/profilevideo/controller/ProfileVideoController.java`
- `src/main/java/com/bepmo/profilevideo/dto/ProfileVideoDtos.java`
- `src/main/java/com/bepmo/profilevideo/entity/ProfileVideo.java`
- `src/main/java/com/bepmo/profilevideo/repository/ProfileVideoRepository.java`
- `src/main/java/com/bepmo/profilevideo/service/ProfileVideoFinalizationService.java`
- `src/main/java/com/bepmo/profilevideo/service/ProfileVideoService.java`
- `src/main/java/com/bepmo/recentproof/controller/RecentProofController.java`
- `src/main/java/com/bepmo/recentproof/dto/RecentProofDtos.java`
- `src/main/java/com/bepmo/recentproof/entity/RecentProof.java`
- `src/main/java/com/bepmo/recentproof/repository/RecentProofRepository.java`
- `src/main/java/com/bepmo/recentproof/service/RecentProofFinalizationService.java`
- `src/main/java/com/bepmo/recentproof/service/RecentProofService.java`
- `src/main/resources/application.yml`
- `src/main/resources/application.yml.example`
- `src/main/resources/db/migration/V4__signed_media_upload_sessions.sql`
- `src/test/java/com/bepmo/profilevideo/ProfileVideoServiceTest.java`
- `src/test/java/com/bepmo/recentproof/RecentProofServiceTest.java`

## Cách áp dụng

Từ root của đúng source archive `bep-mo-backend(5).rar` đã giải nén:

```bash
git apply --check APPLY.patch
git apply APPLY.patch
```

Hoặc copy đè các file theo đúng đường dẫn trong ZIP. Không dùng patch này trên một source khác mà chưa review conflict/WIP.
