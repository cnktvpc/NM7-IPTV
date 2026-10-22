# NM7 Mobile 1.10.144 — Tab Đọc báo: sửa thanh hệ thống, giao diện tối và lọc quảng cáo

Ngày: 2026-10-22  
Baseline: `stable/mobile-1.10.142` (versionCode 158)  
Candidate: `fix/mobile-news-reader-20261022` — 1.10.144 / versionCode 160  
Build source: `1eb5769ac6faa5d3054901f94cf75dee26467f35`

## 1. Đối chiếu video NM7

Video cho thấy:
- Header của Reader bị vẽ vào vùng status bar: giờ và biểu tượng hệ thống chồng lên tên nguồn.
- Hàng tab điều hướng phía dưới bị đặt không cùng hệ tọa độ với tab YouTube.
- Khi mở bài, trang nhà xuất bản giữ màu chữ đậm trên nền tối nên khó đọc; giao diện website đầy đủ cũng để lộ quảng cáo, menu và khối đề xuất.

## 2. Sửa system insets cho thanh trên/dưới

### Reader trên cùng
`NewsReaderActivity` áp dụng phần inset của status bar và display cutout vào padding top của root. Do ứng dụng đặt target SDK 36, không dựa vào hành vi tự fit nội dung của các phiên bản Android cũ. Màu status/navigation bar đặt theo nền tối của app và tắt contrast enforcement trên API hỗ trợ.

### Thanh tab dùng chung
`HomeTabBar` đọc navigation-bar inset và đặt bottom margin tương ứng để hàng tab nằm phía trên vùng gesture/navigation. Với màn hình IPTV, phần padding của root và cuối danh sách kênh được tăng theo cùng inset để nội dung không bị che. Điều chỉnh này nằm trong component tab dùng chung, giúp YouTube, IPTV và Reader sử dụng cùng một hình học hệ thống; nhánh stable 1.10.142 không bị ghi đè.

## 3. Chế độ đọc tối và loại bỏ quảng cáo

### Trích xuất nội dung bài
Khi WebView báo `onPageFinished`, Reader inject `READER_MODE_SCRIPT`. Script tìm các container thường dùng cho phần bài viết, ví dụ:
- `[itemprop="articleBody"]`
- `.fck_detail`
- `.dt-news__content`
- `.singular-content`, `.article-content`, `.detail-content`, `article`, `main`

Nếu tìm thấy một khối có đủ văn bản và đoạn văn, script dựng lại phần đọc gồm tiêu đề, metadata/mô tả nếu có và nội dung bài. Nếu không tìm thấy container phù hợp, CSS chế độ tối vẫn được áp dụng cho trang gốc làm phương án dự phòng.

### Giao diện
- Nền `#101114`, chữ chính `#e9e9ed`, tiêu đề trắng và liên kết màu cam.
- Cỡ chữ thân bài và line-height được đặt cho đọc dài.
- Ảnh và video trong nội dung giữ giới hạn chiều rộng; ảnh lazy-load ưu tiên `data-src`, `data-original` và `data-lazy-src` trước ảnh placeholder `src`.
- Xóa script/style/form/nav/aside/button, khối liên quan/đề xuất/chia sẻ/comment và selector quảng cáo phổ biến trong bản sao phần bài viết.

### Chặn yêu cầu quảng cáo/tracker
`shouldInterceptRequest` chặn một số host quảng cáo/đo lường phổ biến như DoubleClick, Google Syndication/Ad Services, Taboola, Outbrain, Criteo, AdMicro và các host khác trong danh sách. Bộ lọc cũng chặn một số đường dẫn tài nguyên adserver/banner/tracking. Yêu cầu document chính không bị chặn, để bài viết vẫn mở được.

**Giới hạn:** bộ lọc theo host/URL/DOM không phải bộ chặn quảng cáo tuyệt đối. Quảng cáo được nhúng vào HTML chính hoặc phục vụ từ cùng host với bài viết có thể cần rule riêng cho từng nhà xuất bản. Chưa xác minh runtime trên thiết bị thật trong phiên build này.

## 4. Kiểm thử build 1.10.144

- [GitHub Actions run 39765597412 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39765597412)
- [Artifact ZIP](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39765597412/artifacts/12419494923)
- Reader insets/dark-mode/ad-filter guards: 11 checks PASS.
- Existing Live feed regression guards: 30 checks PASS.
- Fullscreen Live server regression: PASS.
- Gradle assemble Mobile debug: PASS.
- Full Mobile unit tests: PASS.
- APK SHA-256: `47e0447854ec92aa28a3598b394eec7faa1bcba8a3282403c4800a07c9ac6b11`.

