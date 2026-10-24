package vn.phuong.iptvplayer;

import java.net.URI;
import java.util.Locale;

/** Prevent 24h.com.vn category tabs from silently showing a different section or homepage. */
final class ReaderFeedCategoryRouter {
    private ReaderFeedCategoryRouter() { }

    static String htmlPageUrl(String sourceId, String category, String sourceHomeUrl) {
        if (!"24h".equals(sourceId)) return sourceHomeUrl;
        if (category == null) return "";
        switch (category) {
            case "Trang chủ": return "https://www.24h.com.vn/";
            case "Tin tức": return "https://www.24h.com.vn/tin-tuc-trong-ngay-c46.html";
            case "Bóng đá": return "https://www.24h.com.vn/bong-da-c48.html";
            case "Ngoại Hạng Anh": return "https://www.24h.com.vn/bong-da-ngoai-hang-anh-c149.html";
            case "Thế giới": return "https://www.24h.com.vn/the-gioi-24h-c415e7888.html";
            case "Kinh doanh": return "https://www.24h.com.vn/kinh-doanh-c161.html";
            case "Giải trí": return "https://www.24h.com.vn/giai-tri-c731.html";
            case "An ninh - Hình sự": return "https://www.24h.com.vn/an-ninh-xa-hoi-c51.html";
            case "Thời trang": return "https://www.24h.com.vn/thoi-trang-c78.html";
            case "Thời trang Hi-tech": return "https://www.24h.com.vn/thoi-trang-hi-tech-c407.html";
            case "Tài chính - Bất động sản": return "https://www.24h.com.vn/tai-chinh-bat-dong-san/";
            case "Ẩm thực": return "https://www.24h.com.vn/am-thuc/";
            case "Làm đẹp": return "https://www.24h.com.vn/lam-dep-c746e2229.html";
            case "Phim": return "https://www.24h.com.vn/phim-c74.html";
            case "Giáo dục - du học": return "https://www.24h.com.vn/giao-duc-du-hoc-c216.html";
            case "Bạn trẻ - Cuộc sống": return "https://www.24h.com.vn/ban-tre-cuoc-song-c64.html";
            case "Ca nhạc - MTV": return "https://www.24h.com.vn/ca-nhac-mtv/";
            case "Thể thao": return "https://www.24h.com.vn/the-thao-c101.html";
            case "Phi thường - kỳ quặc": return "https://www.24h.com.vn/phi-thuong-ky-quac/";
            case "Công nghệ": return "https://www.24h.com.vn/cong-nghe-thong-tin-c55.html";
            case "Ô tô": return "https://www.24h.com.vn/o-to-c747.html";
            case "Thị trường - Tiêu dùng": return "https://www.24h.com.vn/thi-truong-tieu-dung-c52.html";
            case "Du lịch": return "https://www.24h.com.vn/du-lich/";
            case "Sức khỏe đời sống": return "https://www.24h.com.vn/suc-khoe-doi-song-c62.html";
            case "Cười 24h": return "https://www.24h.com.vn/cuoi24h/";
            default: return "";
        }
    }

    static boolean matchesArticle(String sourceId, String category, String articleUrl) {
        if (!"24h".equals(sourceId) || category == null || "Trang chủ".equals(category)) return true;
        if (articleUrl == null || articleUrl.trim().isEmpty()) return false;
        final String host, path;
        try {
            String raw = articleUrl.trim();
            URI uri = URI.create(raw.startsWith("//") ? "https:" + raw : raw);
            host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            path = (uri.getPath() == null ? "" : uri.getPath()).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) { return false; }
        if (!(host.equals("24h.com.vn") || host.endsWith(".24h.com.vn"))) return false;

        switch (category) {
            case "Tin tức": return has(path, "tin-tuc-trong-ngay", "-c46a");
            case "Bóng đá": return has(path, "bong-da", "-c48a");
            // The EPL listing publishes stories under the site's shared football route (c48a),
            // not consistently under c149a; only allow same-site football article URLs here.
            case "Ngoại Hạng Anh": return has(path, "/bong-da/", "bong-da-") || has(path, "-c48a");
            case "Thế giới": return has(path, "the-gioi-24h", "the-gioi", "-c415a", "-c788a");
            case "Kinh doanh": return has(path, "kinh-doanh", "-c161a");
            case "Giải trí": return has(path, "giai-tri", "-c731a");
            case "An ninh - Hình sự": return has(path, "an-ninh-xa-hoi", "an-ninh-hinh-su", "-c51a");
            case "Thời trang":
                return !has(path, "thoi-trang-hi-tech", "-c407a") && has(path, "thoi-trang", "-c78a");
            case "Thời trang Hi-tech": return has(path, "thoi-trang-hi-tech", "-c407a");
            case "Tài chính - Bất động sản": return has(path, "tai-chinh-bat-dong-san", "-c161a");
            case "Ẩm thực": return has(path, "am-thuc");
            case "Làm đẹp": return has(path, "lam-dep", "-c746a");
            case "Phim": return has(path, "/phim-", "/phim/", "-c74a");
            case "Giáo dục - du học": return has(path, "giao-duc-du-hoc", "-c216a");
            case "Bạn trẻ - Cuộc sống": return has(path, "ban-tre-cuoc-song", "-c64a");
            case "Ca nhạc - MTV": return has(path, "ca-nhac-mtv", "canhacmtv", "am-nhac");
            case "Thể thao": return !has(path, "bong-da", "-c48a")
                    && has(path, "the-thao", "tin-tuc-the-thao", "-c101a");
            case "Phi thường - kỳ quặc": return has(path, "phi-thuong-ky-quac", "phithuongkyquac");
            case "Công nghệ": return has(path, "cong-nghe-thong-tin", "-c55a");
            case "Ô tô": return has(path, "o-to", "/oto", "-c747a");
            case "Thị trường - Tiêu dùng": return has(path, "thi-truong-tieu-dung", "-c52a");
            case "Du lịch": return has(path, "du-lich");
            case "Sức khỏe đời sống": return has(path, "suc-khoe-doi-song", "-c62a");
            case "Cười 24h": return has(path, "cuoi24h", "cuoi-24h");
            default: return false;
        }
    }

    private static boolean has(String value, String... patterns) {
        for (String pattern : patterns) if (value.contains(pattern)) return true;
        return false;
    }
}
