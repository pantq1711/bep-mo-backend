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
- ✅ `mvn test` đã chạy thật trên máy dev (Windows, JDK 23): **57/58 pass**.
  - Toàn bộ unit test service layer (Auth, Restaurant, Dish, IngredientSource, ProfileVideo,
    RecentProof, TransparencyScore, Admin) và `RestaurantControllerSecurityTest` (verify đúng
    401 vs 403) đều pass.
  - 1 test fail: `ProfileVideoUniqueActiveIndexTest` (Testcontainers) — **fail do môi trường,
    không phải lỗi code**. Testcontainers 1.20.1 không tương thích với Docker Engine
    29.4.0/API 1.54 trên máy dev (đã xác nhận qua log: handshake trả `400 Bad Request` dù
    `docker compose up` chạy Postgres/Redis bình thường, `curl :2375/version` xác nhận Docker
    Engine API sống tốt). Business rule mà test này verify (partial unique index của
    `ProfileVideo`, xem mục Business rules) không có vấn đề — chỉ chưa verify được bằng
    Testcontainers trên máy này. Cần verify qua CI (GitHub Actions, Linux runner) hoặc nâng
    `testcontainers-bom` lên bản mới hơn (2.0.5 — major version, chưa thử vì rủi ro breaking
    change).
- ✅ Luồng end-to-end qua Docker Compose (3 container: app + Postgres + Redis) — **đã test
  tay thật, thành công**.
  - Đăng ký owner mới → tự động chuyển `/dashboard/new` khi chưa có quán → tạo quán →
    CRUD đủ cả 4 tab (dish, video, ingredient source, recent proof) qua giao diện thật,
    không phải mock.
  - Điểm minh bạch tính đúng theo từng bước thay đổi dữ liệu, verify tại UI:
    `0 → +video KITCHEN(20) → 20 → +ingredient source(15) → 35 → +recent proof mới(20
    Freshness) → 55`. Khớp chính xác công thức ở mục Business rules.
  - Verify chéo lần 2 bằng Postman (quán khác, tạo độc lập qua API thật, không qua UI):
    cùng tổ hợp 1 video KITCHEN + 1 ingredient source + 1 recent proof mới → cũng ra
    đúng **55/100** — 2 đường test độc lập (UI và Postman) cho cùng kết quả, xác nhận
    logic tính điểm đáng tin cậy, không phải trùng hợp ngẫu nhiên.
  - Postman collection chạy tay qua Auth (register/login/refresh) → Restaurant (create/
    update) → Dish (create/toggle availability) → ProfileVideo (upload) →
    IngredientSource (create) → RecentProof (create — xác nhận backend tự suy đúng
    `mediaKind` từ `proofType`, không cần client gửi) → TransparencyScore (get). Toàn
    bộ request trả đúng status code kỳ vọng (201/200), không có request nào fail.
- 🐛 Trong lần test tay đầu tiên, phát hiện và fix 1 bug thật (không phải lỗi môi
  trường): `SecurityConfig` thiếu cấu hình CORS, khiến frontend dev server (port 5173)
  bị trình duyệt chặn khi gọi API (port 8080) — dù Swagger UI (cùng origin) vẫn truy cập
  bình thường nên không lộ ra khi chỉ test qua Swagger. Đã thêm bean
  `corsConfigurationSource()` whitelist origin `http://localhost:5173`.
- ✅ `.dockerignore` đã có ở root, loại trừ `target/`, `.git/` khi build image.