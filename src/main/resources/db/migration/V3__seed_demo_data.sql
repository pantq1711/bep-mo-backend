-- Seed data phục vụ demo/quay video — không phải dữ liệu nghiệp vụ thật. 3 quán được
-- cố ý thiết kế ở 3 mức độ minh bạch khác nhau (đầy đủ / một phần / gần như trống) để
-- khi demo cho thấy rõ Transparency Score phản ánh đúng mức độ công khai, không phải
-- số ngẫu nhiên. Mật khẩu chung cho cả 3 tài khoản demo: Owner@12345 (bcrypt, đã hash
-- sẵn, KHÔNG lưu plaintext ở đâu khác ngoài ghi chú này).

-- ── 3 tài khoản owner mẫu ────────────────────────────────────────────────────
INSERT INTO users (email, password_hash, role, status) VALUES
    ('owner.pho@bepmo.local',   '$2b$10$E7GokBXB9/bnon4jAkeHF.1M6KlVYcLd25ccK0ljOXoroUrtVL0kG', 'RESTAURANT_OWNER', 'ACTIVE'),
    ('owner.buncha@bepmo.local','$2b$10$E7GokBXB9/bnon4jAkeHF.1M6KlVYcLd25ccK0ljOXoroUrtVL0kG', 'RESTAURANT_OWNER', 'ACTIVE'),
    ('owner.comtam@bepmo.local','$2b$10$E7GokBXB9/bnon4jAkeHF.1M6KlVYcLd25ccK0ljOXoroUrtVL0kG', 'RESTAURANT_OWNER', 'ACTIVE');

-- ── 3 quán, 3 mức minh bạch khác nhau ────────────────────────────────────────
INSERT INTO restaurants (owner_id, name, description, address, category, status)
SELECT id, 'Phở Ông Tuấn', 'Phở bò gia truyền 3 đời, nước dùng ninh 12 tiếng', '45 Hàng Bông, Hoàn Kiếm, Hà Nội', 'Phở', 'ACTIVE'
FROM users WHERE email = 'owner.pho@bepmo.local';

INSERT INTO restaurants (owner_id, name, description, address, category, status)
SELECT id, 'Bún Chả Bà Hạnh', 'Bún chả que tre nướng than hoa, công thức riêng', '12 Ngõ Huyện, Hoàn Kiếm, Hà Nội', 'Bún', 'ACTIVE'
FROM users WHERE email = 'owner.buncha@bepmo.local';

INSERT INTO restaurants (owner_id, name, description, address, category, status)
SELECT id, 'Cơm Tấm Sài Gòn', 'Cơm tấm sườn bì chả kiểu miền Nam', '78 Trần Hưng Đạo, Hải Châu, Đà Nẵng', 'Cơm', 'ACTIVE'
FROM users WHERE email = 'owner.comtam@bepmo.local';

-- ── Món ăn (không ảnh hưởng điểm minh bạch, chỉ để demo có nội dung) ─────────
INSERT INTO dishes (restaurant_id, name, description, price, category, is_available)
SELECT id, 'Phở bò tái nạm', 'Bánh phở tươi, thịt bò tái + nạm', 55000, 'Món chính', TRUE FROM restaurants WHERE name = 'Phở Ông Tuấn'
UNION ALL
SELECT id, 'Phở gà', 'Gà ta thả vườn, nước dùng thanh', 50000, 'Món chính', TRUE FROM restaurants WHERE name = 'Phở Ông Tuấn'
UNION ALL
SELECT id, 'Bún chả que tre', 'Thịt nướng que tre, nem cua bể', 45000, 'Món chính', TRUE FROM restaurants WHERE name = 'Bún Chả Bà Hạnh'
UNION ALL
SELECT id, 'Nem rán', 'Nem cua bể chiên giòn', 15000, 'Món phụ', TRUE FROM restaurants WHERE name = 'Bún Chả Bà Hạnh'
UNION ALL
SELECT id, 'Cơm tấm sườn bì chả', 'Sườn nướng + bì + chả trứng', 40000, 'Món chính', TRUE FROM restaurants WHERE name = 'Cơm Tấm Sài Gòn';

-- ── Video minh bạch ───────────────────────────────────────────────────────────
-- Phở Ông Tuấn: ĐỦ CẢ 4 LOẠI → Completeness tối đa từ video (20+20+15+10 = 65đ)
INSERT INTO profile_videos (restaurant_id, type, cloudinary_url, cloudinary_public_id, thumbnail_url, duration_seconds, file_size_bytes, status)
SELECT id, 'INGREDIENT_RECEIVING', 'https://res.cloudinary.com/demo/video/upload/v1/pho_receiving.mp4', 'pho_receiving_1', 'https://res.cloudinary.com/demo/video/upload/v1/pho_receiving.jpg', 20, 3145728, 'ACTIVE' FROM restaurants WHERE name = 'Phở Ông Tuấn'
UNION ALL
SELECT id, 'KITCHEN', 'https://res.cloudinary.com/demo/video/upload/v1/pho_kitchen.mp4', 'pho_kitchen_1', 'https://res.cloudinary.com/demo/video/upload/v1/pho_kitchen.jpg', 25, 4194304, 'ACTIVE' FROM restaurants WHERE name = 'Phở Ông Tuấn'
UNION ALL
SELECT id, 'HYGIENE', 'https://res.cloudinary.com/demo/video/upload/v1/pho_hygiene.mp4', 'pho_hygiene_1', 'https://res.cloudinary.com/demo/video/upload/v1/pho_hygiene.jpg', 15, 2097152, 'ACTIVE' FROM restaurants WHERE name = 'Phở Ông Tuấn'
UNION ALL
SELECT id, 'PREP', 'https://res.cloudinary.com/demo/video/upload/v1/pho_prep.mp4', 'pho_prep_1', 'https://res.cloudinary.com/demo/video/upload/v1/pho_prep.jpg', 18, 2621440, 'ACTIVE' FROM restaurants WHERE name = 'Phở Ông Tuấn';

-- Bún Chả Bà Hạnh: 2/4 loại → minh bạch một phần
INSERT INTO profile_videos (restaurant_id, type, cloudinary_url, cloudinary_public_id, thumbnail_url, duration_seconds, file_size_bytes, status)
SELECT id, 'KITCHEN', 'https://res.cloudinary.com/demo/video/upload/v1/buncha_kitchen.mp4', 'buncha_kitchen_1', 'https://res.cloudinary.com/demo/video/upload/v1/buncha_kitchen.jpg', 22, 3670016, 'ACTIVE' FROM restaurants WHERE name = 'Bún Chả Bà Hạnh'
UNION ALL
SELECT id, 'HYGIENE', 'https://res.cloudinary.com/demo/video/upload/v1/buncha_hygiene.mp4', 'buncha_hygiene_1', 'https://res.cloudinary.com/demo/video/upload/v1/buncha_hygiene.jpg', 12, 1835008, 'ACTIVE' FROM restaurants WHERE name = 'Bún Chả Bà Hạnh';

-- Cơm Tấm Sài Gòn: KHÔNG có video nào — minh bạch thấp, để demo tương phản rõ

-- ── Nguồn nguyên liệu ─────────────────────────────────────────────────────────
INSERT INTO ingredient_sources (restaurant_id, name, source_type, note, status)
SELECT id, 'Chợ Đồng Xuân', 'WHOLESALE_MARKET', 'Nhập thịt bò, xương ống mỗi sáng sớm', 'ACTIVE' FROM restaurants WHERE name = 'Phở Ông Tuấn';

INSERT INTO ingredient_sources (restaurant_id, name, source_type, note, status)
SELECT id, 'Trang trại Ba Vì', 'DIRECT_FARM', 'Thịt lợn sạch, giao 2 lần/tuần', 'ACTIVE' FROM restaurants WHERE name = 'Bún Chả Bà Hạnh';

-- Cơm Tấm Sài Gòn: chưa khai báo nguồn nào

-- ── Bằng chứng gần đây ────────────────────────────────────────────────────────
-- Phở Ông Tuấn: mới (trong 7 ngày) → Freshness tối đa +20
INSERT INTO recent_proofs (restaurant_id, proof_type, media_kind, media_url, cloudinary_public_id, note, status, uploaded_at)
SELECT id, 'INVOICE', 'IMAGE', 'https://res.cloudinary.com/demo/image/upload/v1/pho_invoice.jpg', 'pho_invoice_1', 'Hoá đơn nhập xương bò', 'ACTIVE', NOW() - INTERVAL '2 days'
FROM restaurants WHERE name = 'Phở Ông Tuấn';

-- Bún Chả Bà Hạnh: cũ hơn (8-14 ngày) → Freshness trung bình +10
INSERT INTO recent_proofs (restaurant_id, proof_type, media_kind, media_url, cloudinary_public_id, note, status, uploaded_at)
SELECT id, 'DELIVERY_NOTE', 'IMAGE', 'https://res.cloudinary.com/demo/image/upload/v1/buncha_delivery.jpg', 'buncha_delivery_1', 'Phiếu giao thịt lợn từ trang trại', 'ACTIVE', NOW() - INTERVAL '10 days'
FROM restaurants WHERE name = 'Bún Chả Bà Hạnh';

-- Cơm Tấm Sài Gòn: chưa có proof nào — Freshness = 0
