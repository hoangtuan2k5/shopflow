INSERT INTO products (name, description, price, active, low_stock_threshold)
SELECT demo.name, demo.description, demo.price, TRUE, demo.low_stock_threshold
FROM (
    VALUES
        ('Giá đỡ laptop nhôm', 'Giá đỡ gập gọn giúp nâng tầm màn hình khi làm việc.', 499000, 4),
        ('Chuột không dây Silent Click', 'Chuột kết nối USB với nút bấm êm và pin dùng lâu.', 279000, 8),
        ('Bàn phím cơ 87 phím', 'Bàn phím gọn nhẹ với switch tactile cho góc làm việc.', 1599000, 4),
        ('Tai nghe Bluetooth chụp tai', 'Tai nghe không dây đệm êm cho học tập và giải trí.', 899000, 5),
        ('Hub USB-C 7 cổng', 'Hub mở rộng cổng USB, HDMI và đầu đọc thẻ cho laptop.', 749000, 4),
        ('Webcam Full HD', 'Webcam 1080p tích hợp micro cho họp trực tuyến.', 690000, 3),
        ('Túi chống sốc laptop 14 inch', 'Túi đệm mềm bảo vệ laptop khi di chuyển.', 359000, 5),
        ('Sạc nhanh USB-C 65W', 'Củ sạc GaN nhỏ gọn cho điện thoại và laptop.', 549000, 4),
        ('Đèn bàn LED cảm ứng', 'Đèn ba mức sáng với cần xoay linh hoạt.', 429000, 3),
        ('Sổ tay chấm bi A5', 'Sổ giấy dày cho kế hoạch và ghi chú hằng ngày.', 69000, 12),
        ('Bộ bút gel 12 màu', 'Bộ bút viết mượt cho học tập và trang trí sổ tay.', 89000, 10),
        ('Bình nước giữ nhiệt 750 ml', 'Bình inox hai lớp giữ nóng lạnh cho ngày dài.', 189000, 6),
        ('Túi tote canvas', 'Túi canvas tối giản, dùng đi làm hoặc đi chợ.', 149000, 6),
        ('Cốc sứ 350 ml', 'Cốc sứ men mờ phù hợp cho trà và cà phê.', 129000, 6),
        ('Ly giữ nhiệt 500 ml', 'Ly có nắp kín để mang cà phê hoặc trà.', 259000, 5),
        ('Quạt mini để bàn', 'Quạt USB ba tốc độ cho bàn làm việc nhỏ.', 219000, 5),
        ('Khăn tắm sợi microfiber', 'Khăn mềm thấm hút nhanh và dễ mang theo.', 99000, 8),
        ('Nến thơm gỗ tuyết tùng', 'Nến hương ấm cho không gian nghỉ ngơi.', 199000, 4),
        ('Bộ hộp đựng thực phẩm', 'Bộ ba hộp kín khí dùng bảo quản thức ăn.', 179000, 5),
        ('Chảo chống dính 24 cm', 'Chảo đáy từ dùng cho bếp gas và bếp từ.', 389000, 4),
        ('Bộ dao bếp 3 món', 'Bộ dao cơ bản cho các công việc bếp hằng ngày.', 459000, 3),
        ('Thớt tre ép', 'Thớt tre bền chắc, phù hợp sơ chế thực phẩm.', 159000, 6),
        ('Hộp cơm văn phòng', 'Hộp cơm nhiều ngăn kèm muỗng nĩa.', 249000, 5),
        ('Nồi cơm điện mini 1.2L', 'Nồi cơm nhỏ cho một đến hai người.', 890000, 3),
        ('Dầu gội phục hồi 500 ml', 'Dầu gội dịu nhẹ giúp làm sạch tóc hằng ngày.', 179000, 6),
        ('Kem chống nắng SPF 50', 'Kem chống nắng phổ rộng dùng mỗi ngày.', 239000, 6),
        ('Kem dưỡng da tay 50 ml', 'Kem dưỡng ẩm nhanh thấm cho da tay.', 119000, 6),
        ('Thảm tập yoga 6 mm', 'Thảm chống trượt có độ dày êm chân.', 349000, 4),
        ('Bộ dây kháng lực 5 mức', 'Năm mức kháng lực cho bài tập tại nhà.', 229000, 5),
        ('Bóng massage cơ', 'Bóng nhỏ hỗ trợ thư giãn cơ sau tập luyện.', 149000, 5),
        ('Loa Bluetooth mini', 'Loa nhỏ gọn cho nhạc và podcast.', 499000, 4),
        ('Pin sạc dự phòng 10000 mAh', 'Pin sạc nhanh hai cổng cho thiết bị di động.', 599000, 4),
        ('Ổ cắm thông minh Wi-Fi', 'Ổ cắm điều khiển từ xa qua ứng dụng.', 329000, 4),
        ('Ô gấp chống UV', 'Ô gấp nhẹ có lớp phủ chống nắng.', 179000, 6),
        ('Balo học sinh 20L', 'Balo nhiều ngăn cho sách vở và đồ dùng.', 399000, 5),
        ('Bộ bài Uno', 'Trò chơi thẻ bài cho gia đình và bạn bè.', 129000, 6)
) AS demo(name, description, price, low_stock_threshold)
WHERE NOT EXISTS (SELECT 1 FROM products product WHERE product.name = demo.name);

INSERT INTO inventory_items (product_id, on_hand_stock, reserved_stock)
SELECT product.id, demo.on_hand_stock, 0
FROM products product
JOIN (
    VALUES
        ('Giá đỡ laptop nhôm', 18),
        ('Chuột không dây Silent Click', 40),
        ('Bàn phím cơ 87 phím', 20),
        ('Tai nghe Bluetooth chụp tai', 24),
        ('Hub USB-C 7 cổng', 16),
        ('Webcam Full HD', 12),
        ('Túi chống sốc laptop 14 inch', 30),
        ('Sạc nhanh USB-C 65W', 22),
        ('Đèn bàn LED cảm ứng', 15),
        ('Sổ tay chấm bi A5', 60),
        ('Bộ bút gel 12 màu', 50),
        ('Bình nước giữ nhiệt 750 ml', 28),
        ('Túi tote canvas', 35),
        ('Cốc sứ 350 ml', 32),
        ('Ly giữ nhiệt 500 ml', 24),
        ('Quạt mini để bàn', 18),
        ('Khăn tắm sợi microfiber', 40),
        ('Nến thơm gỗ tuyết tùng', 14),
        ('Bộ hộp đựng thực phẩm', 26),
        ('Chảo chống dính 24 cm', 16),
        ('Bộ dao bếp 3 món', 12),
        ('Thớt tre ép', 30),
        ('Hộp cơm văn phòng', 24),
        ('Nồi cơm điện mini 1.2L', 10),
        ('Dầu gội phục hồi 500 ml', 28),
        ('Kem chống nắng SPF 50', 26),
        ('Kem dưỡng da tay 50 ml', 30),
        ('Thảm tập yoga 6 mm', 14),
        ('Bộ dây kháng lực 5 mức', 20),
        ('Bóng massage cơ', 22),
        ('Loa Bluetooth mini', 16),
        ('Pin sạc dự phòng 10000 mAh', 18),
        ('Ổ cắm thông minh Wi-Fi', 16),
        ('Ô gấp chống UV', 30),
        ('Balo học sinh 20L', 18),
        ('Bộ bài Uno', 24)
) AS demo(name, on_hand_stock) ON demo.name = product.name
WHERE NOT EXISTS (
    SELECT 1 FROM inventory_items inventory WHERE inventory.product_id = product.id
);
