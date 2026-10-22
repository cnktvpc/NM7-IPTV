# NM7 Mobile — Nghiên cứu và triển khai tab Đọc báo 1.10.143

Ngày: 2026-10-22  
Baseline: `stable/mobile-1.10.142` — versionCode 158  
Candidate: `fix/mobile-news-reader-20261022` — version 1.10.143 / versionCode 159  
Build: [GitHub Actions 39755662838 — SUCCESS](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39755662838)

## 1. Những gì quan sát được từ video Super OK

Luồng thao tác nhìn thấy trong video:

1. Tab đọc báo mở danh sách tin; thanh trên có tên nguồn hiện tại, tìm kiếm, điều chỉnh cỡ chữ, quản lý nguồn và làm mới.
2. Danh mục bài viết được đặt trên một thanh ngang có thể cuộn; thay đổi danh mục sẽ đổi tập bài.
3. Bài viết được hiển thị theo hàng: ảnh thu nhỏ bên trái, tiêu đề và thông tin nguồn/ngày ở bên phải.
4. Màn hình **Quản lý nguồn đọc** có số lượng nguồn đã cài/đang bật, các nhóm **Báo**, **Truyện chữ**, **Truyện tranh**, thao tác cài tất cả/xóa tất cả, và các thẻ nguồn có nút cài đặt hoặc bật/tắt.
5. Sau khi chọn nguồn khác, giao diện quay về danh sách tin của nguồn đó; thanh điều hướng ở dưới vẫn tồn tại.

## 2. Dấu vết cơ chế nội bộ trong APK mẫu

Các chuỗi/class trong DEX cho thấy mô-đun đọc báo của Super OK không chỉ là một WebView thuần. Những tên liên quan gồm `JsNewsStore`, `JsCatalogSource`, `JsSourceRecord`, `NewsSource`, `NewsSites`, `NewsCategory`, `NewsArticle`, `ArticleExtractor`, `ArticleContent`, `ArticleBlock` và `NewsTtsReader`. Điều này cho thấy ứng dụng dùng dữ liệu nguồn/plugin, mô hình bài viết/danh mục và có thành phần trích xuất nội dung đọc. APK cũng chứa URL catalog bên ngoài cho danh mục tin/truyện và các endpoint RSS VnExpress.

Mẫu dùng catalog nguồn bên ngoài có nội dung mã hóa và engine JavaScript. Trong khi đó, thông báo đi kèm hệ thống catalog cho biết dịch vụ Super OK đã ngừng hoạt động. Vì vậy, candidate NM7 không phụ thuộc vào catalog đó, không cố thực thi plugin từ catalog mã hóa và không sao chép cơ chế bên thứ ba; thay vào đó nó dùng RSS công khai và danh mục nguồn tích hợp trong app.

## 3. Thiết kế được triển khai trong NM7

### Luồng tải và hiển thị bài

- `NewsReaderActivity` là Activity riêng; mở lên sẽ khôi phục nguồn đang chọn từ `SharedPreferences`, mặc định là VnExpress.
- Mỗi nguồn cung cấp một map chuyên mục → URL RSS. Nhấn danh mục sẽ tải lại feed tương ứng.
- Yêu cầu HTTP chạy trong executor nền, không trên UI thread. Kết nối có timeout, User-Agent và Accept-Language.
- Parser RSS/Atom chuyển mỗi item thành mô hình gồm tiêu đề, URL bài, mô tả, ảnh và ngày xuất bản. Parser lấy ảnh từ enclosure/media content/thumbnail hoặc thẻ ảnh trong description/summary khi có.
- Danh sách hiển thị thẻ ảnh + tiêu đề + nguồn/ngày. Tìm kiếm lọc tiêu đề/mô tả trong feed đang tải; nút chữ điều chỉnh cỡ chữ và nút làm mới tải feed lại.
- Khi nhấn bài, bài mở bên trong WebView của NM7, bật JavaScript và DOM storage để tương thích trang báo hiện đại. Có nút quay về danh sách và chia sẻ link bài viết.

### Quản lý nguồn

- VnExpress là nguồn tích hợp sẵn nên vẫn dùng được dù chưa cài nguồn bổ sung.
- Nguồn bổ sung có trạng thái cài đặt và bật/tắt lưu riêng bằng `SharedPreferences`. Danh sách nguồn cho phép cài từng nguồn, bật/tắt, gỡ, cài tất cả hoặc xóa tất cả.
- Catalog RSS tích hợp hiện có: Kênh14, Thể Thao 247, Dân Trí, GenK, VnReview, 24h, Báo Mới, Tuổi Trẻ, Thanh Niên, Tin Mới và VTV. Các nguồn khai báo những chuyên mục phổ biến riêng theo feed.
- Tab **Đọc báo** được chèn vào thanh dưới hiện có của NM7, cùng YouTube, IPTV và Tùy chọn. Activity được khai báo trong AndroidManifest và dùng icon riêng.

## 4. Giới hạn cần biết của candidate

- Phạm vi release này là **đọc báo/RSS**. Các tab Truyện chữ và Truyện tranh hiện là placeholder giao diện, chưa có engine đọc truyện.
- NM7 không tải catalog plugin JavaScript của Super OK. Đây là lựa chọn để tránh phụ thuộc backend đã ngừng và không thực thi code plugin từ nguồn xa.
- Bản hiện tại tải RSS khi mở/đổi chuyên mục/nhấn làm mới; chưa có cơ chế refresh định kỳ hoặc cache bài viết offline.
- Toàn bài được hiển thị bằng trang web gốc trong WebView, chưa thay thế bằng một bộ trích xuất nội dung native như các class `ArticleExtractor`/TTS có dấu vết trong APK mẫu.
- RSS và HTML do nhà xuất bản quản lý; nguồn có thể thay đổi URL, định dạng hoặc chặn request. Khi feed không có bài, app báo lỗi tải và cung cấp lối mở trang báo.
- CI không kiểm tra giao diện bằng thao tác chạm trên điện thoại thật. Trước khi chuyển nhánh này thành stable, cần cài APK và thử mở nguồn, chuyển chuyên mục, tải ảnh, mở bài, nút quay lại, tìm kiếm và quản lý bật/tắt nguồn trên thiết bị.

## 5. Kết quả kiểm thử và artifact

- Reader wiring/version assertions: PASS.
- 30 kiểm tra tĩnh Live feed và kiểm tra server fullscreen kế thừa từ baseline: PASS.
- Gradle `:app:assembleMobileDebug`: PASS.
- Full Mobile unit-test suite: PASS.
- [Build run 39755662838](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39755662838).
- [Artifact ZIP — NM7-IPTV-Mobile-1.10.143-NEWS-READER](https://github.com/cnktvpc/NM7-IPTV/actions/runs/39755662838/artifacts/12416400323).
- APK SHA-256: `1ac35fe96e8f492f00d3054f18a76387f8a2f58ac194aab6ba5c6b80d833479b`.
- Build source commit: `2598a0869a19590eb028cc8daa5a546f99d587bc`.
- Nhánh stable `stable/mobile-1.10.142` vẫn giữ nguyên.
