# Bếp Mở — Backend

Trust Profile Platform cho quán ăn. **Không phải** app giao đồ ăn, **không phải** app
review/rating, **không phải** hệ thống kiểm định vệ sinh an toàn thực phẩm. Điểm minh
bạch chỉ phản ánh mức độ quán chủ động công khai thông tin — không xác nhận đạt chuẩn VSATTP.

Đồ án thực tập tốt nghiệp PTIT — SV Phan Hồng An (B21DCCN136, D21CNPM02), GVHD Cô Đỗ Thị Liên.

Frontend riêng tại [pantq1711/bep-mo-frontend](https://github.com/pantq1711/bep-mo-frontend) (không phải monorepo).

## Tech stack

Java 17 · Spring Boot 3.3 · Spring Security · Spring Data JPA · PostgreSQL 16 · Redis 7 ·
Flyway · Docker Compose · Swagger/OpenAPI · JWT (jjwt 0.12.6) · Maven · Cloudinary (chỉ lưu
URL, backend không tự upload) · Modular Monolith (package theo domain).

## Kiến trúc — modular monolith theo domain

```
com.bepmo/
├── auth/               # Register, login, refresh (rotation + reuse detection), logout
├── restaurant/         # Hồ sơ quán — 1 owner = 1 restaurant (UNIQUE owner_id)
├── dish/                # Thực đơn
├── profilevideo/       # Video khu vực vận hành — 4 type cố định, mỗi type 1 video ACTIVE
├── ingredientsource/    # Nguồn nguyên liệu tự khai
├── recentproof/         # Bằng chứng gần đây (hoá đơn, ảnh, video nhận hàng...)
├── transparencyscore/   # Điểm minh bạch — tính on-demand, cache-aside Redis
├── admin/               # Moderation: hide/unhide nội dung, disable/enable user
└── common/              # GlobalExceptionHandler, AppException, security filter...
```

Mỗi domain tự chứa `entity/ repository/ dto/ service/ controller/` riêng, không có tầng
dùng chung kiểu "shared service" xuyên domain — trừ những phụ thuộc rõ ràng cần thiết
(ví dụ mọi service ghi dữ liệu ảnh hưởng điểm phải gọi
`transparencyScoreService.evictCache()`).

**Quyết định thiết kế quan trọng:** `RestaurantService` cố tình KHÔNG gọi
`TransparencyScoreService.getScore()` khi trả về profile/summary — tách domain, tránh N lần
gọi Redis khi list nhiều quán cùng lúc. Điểm minh bạch luôn được lấy qua endpoint riêng
`GET /restaurants/{id}/transparency-score`.

## Business rules bắt buộc nhớ đúng

- **1 owner = 1 restaurant**, enforce bằng UNIQUE constraint trên `owner_id`, không chỉ check ở tầng service.
- **ProfileVideo**: mỗi `type` (INGREDIENT_RECEIVING / KITCHEN / HYGIENE / PREP) chỉ được có
  1 video ACTIVE tại một thời điểm — enforce bằng partial unique index, có Testcontainers
  test riêng cho việc này (không chỉ tin unit test mock).
- **RecentProof**: `mediaKind` suy tự động ở application layer từ `proofType`
  (`RECEIVING_VIDEO` → `VIDEO`, còn lại → `IMAGE`) — client không được gửi `mediaKind`.
- **Transparency Score** (0–100, tính on-demand, cache Redis, **không lưu DB**):
  - Completeness tối đa 80đ: ingredient source ACTIVE +15, video INGREDIENT_RECEIVING +20,
    KITCHEN +20, HYGIENE +15, PREP +10.
  - Freshness tối đa 20đ: bằng chứng gần nhất ≤7 ngày +20, 8–14 ngày +10, >14 ngày hoặc
    không có +0.
  - Cache có dynamic TTL theo mốc "độ mới" của proof, cộng jitter ±5 phút để tránh cache
    stampede khi nhiều request cùng miss cache một lúc.
- **Ngoài phạm vi MVP** (cố tình không làm): delivery, đặt món, thanh toán, review/rating,
  chat, notification, Kafka, WebSocket, microservices, AI chấm điểm, OCR hoá đơn, xác thực
  bên thứ ba, timeline lịch sử điểm.

## Setup chạy local

`application.yml` **không được push lên GitHub** (tránh lộ secret) — bạn phải tự tạo:

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
# rồi điền DB_PASSWORD / JWT_SECRET / ADMIN_PASSWORD thật, hoặc set qua biến môi trường
```

> `application.yml.example` được viết lại từ đúng property key thật trong code
> (`JwtProperties`, `AdminBootstrapRunner`) — nhưng 2 giá trị thời hạn token
> (`access-token-expiration-ms`, `refresh-token-expiration-ms`) là số phỏng đoán hợp lý,
> **chưa đối chiếu với `JwtUtil` thật** — tự verify lại trước khi tin.

### Chạy bằng Docker Compose (khuyến nghị — đúng môi trường CI/deploy)

```bash
docker compose up --build
```

Compose tự start Postgres 16 + Redis 7 + app, override datasource/redis host về tên service
trong network Docker (khác với `localhost` khi chạy ngoài Docker).

### Chạy trực tiếp bằng Maven (cần tự có Postgres + Redis local)

```bash
./mvnw spring-boot:run
```

## Test

```bash
mvn test
```

Có unit test (JUnit 5 + Mockito) cho toàn bộ service chính, và integration test dùng
Testcontainers để verify partial unique index của `ProfileVideo` — test này cần Docker
đang chạy để Testcontainers tự spin container Postgres thật.

## API docs & Postman

- Swagger UI: `http://localhost:8080/swagger-ui.html` (sau khi app đã chạy)
- Postman collection: [`postman/Bep-Mo.postman_collection.json`](postman/Bep-Mo.postman_collection.json)
  + environment [`postman/Bep-Mo-Local.postman_environment.json`](postman/Bep-Mo-Local.postman_environment.json)
  — import cả 2 vào Postman, chạy `Auth > Register` rồi `Auth > Login` trước, các request
  còn lại tự dùng token/id đã lưu qua biến collection, không cần sửa tay.

## Trạng thái đã verify thật (tính đến khi viết README này)

- ✅ Build (`mvn compile`) và toàn bộ endpoint đọc trực tiếp từ Controller thật — không đoán.
- ⚠️ `mvn test` — **chưa chạy được trong môi trường viết code này** (không có mạng để tải
  Maven wrapper). Tự chạy ở máy bạn trước khi coi bất kỳ module nào là "đã xong".
- ⚠️ Luồng end-to-end qua Docker Compose (đăng ký → tạo quán → CRUD dish/video/
  ingredient-source/proof → xem điểm minh bạch đổi đúng) — **chưa được test tay thật**.
- ⚠️ `.dockerignore` hiện đang thiếu ở root — cần thêm trước khi build image, tránh copy
  `target/`, `.git/` vào image.
