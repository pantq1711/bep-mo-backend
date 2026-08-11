# Bếp Mở — Backend post-review hotfix v2

## Baseline

Patch này được tạo sau signed-media refactor và xử lý các lỗi được xác minh lại từ source thực tế.

Có 2 cách apply:

- `APPLY_FROM_SIGNED_MEDIA.patch`: dùng khi source của bạn **đã có signed-media refactor** trước đó.
- `APPLY_FROM_ORIGINAL_RAR.patch`: cumulative patch, dùng trực tiếp trên baseline `bep-mo-backend(5).rar` đã upload trong chat.

Không apply cả hai patch lên cùng một source.

## File thay đổi

1. `src/main/java/com/bepmo/transparencyscore/service/TransparencyScoreService.java`
2. `src/main/java/com/bepmo/dish/controller/DishController.java`
3. `src/main/java/com/bepmo/dish/dto/DishDtos.java`
4. `src/test/java/com/bepmo/transparencyscore/TransparencyScoreServiceTest.java`
5. `src/test/java/com/bepmo/dish/DishDtosValidationTest.java` (new)

## Fix 1 — Redis graceful degradation cho Transparency Score

- Redis cache read failure không còn làm endpoint score trả 500 ngay.
- Khi Redis unavailable, service tính score từ PostgreSQL và vẫn trả response.
- Redis cache write failure chỉ log warning, không làm mất score đã tính.
- Không catch lỗi DB/business chung; chỉ cache exception thuộc `DataAccessException`.

## Fix 2 — Redis eviction không còn nằm trên critical DB mutation transaction

Khi `evictCache()` được gọi trong transaction:

- không gọi Redis DELETE ngay trong transaction;
- đăng ký `afterCommit` callback;
- chỉ DELETE Redis sau khi DB commit thành công;
- Redis DELETE failure được log và không propagate ngược vào business flow.

Mục tiêu: Redis là cache, không được làm rollback ProfileVideo/RecentProof/IngredientSource mutation đã hợp lệ.

## Fix 3 — Dish availability request có validation

Thay raw:

```java
Map<String, Boolean>
```

bằng DTO:

```java
SetAvailabilityRequest(@NotNull Boolean isAvailable)
```

JSON contract vẫn là:

```json
{ "isAvailable": true }
```

Nhưng request `{}` hoặc `{ "isAvailable": null }` sẽ bị Bean Validation từ chối thay vì âm thầm hiểu thành `false`.

## Tests bổ sung/cập nhật

- Redis read failure -> fallback DB.
- Redis write failure -> vẫn trả calculated score.
- `evictCache()` trong transaction -> chỉ delete after commit.
- Redis delete failure -> không throw vào business flow.
- `SetAvailabilityRequest.isAvailable` null -> validation violation.

## QA đã thực hiện

- `git diff --check`: PASS cho file hotfix.
- `git apply --check APPLY_FROM_SIGNED_MEDIA.patch`: PASS trên baseline signed-media đã tái tạo.
- Apply patch rồi so sánh file output với work copy cuối: byte-for-byte PASS.
- `git apply --check APPLY_FROM_ORIGINAL_RAR.patch`: PASS trên baseline RAR gốc.

## QA chưa thể thực hiện trong environment này

Backend compile/test chưa chạy được vì Maven Wrapper cố tải Maven 3.9.16 từ Maven Central và environment hiện không resolve/download được URL đó.

Không claim `mvn test` pass.

## Cố ý chưa sửa trong hotfix này

- PUT endpoints đang mang partial-update semantics (PUT-as-PATCH).
- Disabled user có thể dùng access token hiện tại đến khi token hết hạn nếu chưa blacklist/revoke tức thời.

Hai mục này nên review/test riêng sau khi core demo flow ổn định.
