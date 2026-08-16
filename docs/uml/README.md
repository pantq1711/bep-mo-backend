# Bếp Mở - Phân tích thiết kế UML

Bộ sản phẩm UML dùng để đưa lên GitHub cùng đồ án PTIT.

## Cấu trúc

- `plantuml/`: nguồn `.puml` chỉnh sửa được.
- `preview/`: ảnh PNG dùng để xem nhanh và chèn vào báo cáo/slide.
- `document/`: `Bep_Mo_Phan_tich_thiet_ke_UML.docx` / `.pdf` — tài liệu phân tích tổng hợp.

## Trạng thái đối chiếu source (quan trọng)

Bộ sơ đồ này đã qua vòng đối chiếu độc lập với source code thật (backend + frontend,
ngày 15-16/08/2026), phát hiện 0/17 sơ đồ khớp hoàn toàn ở lần dựng đầu — chủ yếu sai
chữ ký method/tham số và thứ tự gọi ở các sequence/class diagram. Toàn bộ 17 file đã
được sửa lại theo đúng phát hiện đó và render thành công (17/17 PASS cú pháp).

Phạm vi sửa chính: chữ ký method đúng tham số thật (AuthService, JwtUtil,
RestaurantService, DishService, AdminService, TransparencyScoreService,
CloudinaryMediaGateway...), HTTP status code đúng theo controller thật (204 cho
restaurant hide/unhide, 409 chỉ cho video/source/proof transition, 403/404 cho
ownership/not-found thay vì 409), loại bỏ method không tồn tại (`moderateResource`),
và sửa thứ tự lời gọi (issue access token trước khi lưu refresh token, response luôn
đi qua controller).

## Cách render PlantUML

Mở `.puml` bằng VS Code + PlantUML extension, IntelliJ PlantUML plugin, hoặc PlantUML
CLI. Không đưa secret, JWT, access token, refresh token hoặc Cloudinary signature vào
sơ đồ.

## Trạng thái đối chiếu source (repo)

- Backend: `https://github.com/pantq1711/bep-mo-backend.git`, tag `v1.0-defense`,
  commit `ef44896bd6a9bee038036fbf992a1e871dc1be52`, đã commit đầy đủ.
- Frontend: `https://github.com/pantq1711/bep-mo-frontend.git`, tag `v1.0-defense`,
  commit `2e7fc3326f4154761f4b540244a80fefe8c65ee8`, đã commit đầy đủ.

Không đưa `.env`, token hoặc secret vào repository công khai.
