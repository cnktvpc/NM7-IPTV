package vn.phuong.iptvplayer;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReaderArticleExtractorTest {
    @Test public void rendersReadableNativeDarkArticleAndRemovesSiteChrome() {
        String html = "<html><head><meta property='og:title' content='Bài báo thử nghiệm'></head><body>"
                + "<header class='site-header'>MENU KHÔNG ĐƯỢC HIỆN</header>"
                + "<aside class='advert-box'>QUẢNG CÁO</aside>"
                + "<article class='article-content'>"
                + "<p>Đoạn nội dung quan trọng của bài báo được dựng trong chế độ đọc, "
                + "với chữ sáng và khoảng cách dòng đủ lớn để người dùng đọc trên điện thoại.</p>"
                + "<figure><img src='/images/story.jpg'><figcaption>Ảnh minh họa</figcaption></figure>"
                + "<p>Đoạn thứ hai chứa thông tin tiếp theo để xác minh trích xuất thân bài không bị bỏ trống.</p>"
                + "<div class='related-stories'>TIN LIÊN QUAN KHÔNG ĐƯỢC HIỆN</div>"
                + "</article></body></html>";
        String result = ReaderArticleExtractor.renderForTest(html, "https://news.example/story/1",
                "Bài báo thử nghiệm", "Tóm tắt đọc nhanh", "/images/hero.jpg",
                "Nguồn báo thử", "22/10/2026", 100);

        assertTrue(result.contains("#101114"));
        assertTrue(result.contains("#e9e9ed"));
        assertTrue(result.contains("Bài báo thử nghiệm"));
        assertTrue(result.contains("Đoạn nội dung quan trọng"));
        assertTrue(result.contains("https://news.example/images/story.jpg"));
        assertFalse(result.contains("MENU KHÔNG ĐƯỢC HIỆN"));
        assertFalse(result.contains("QUẢNG CÁO"));
        assertFalse(result.contains("TIN LIÊN QUAN KHÔNG ĐƯỢC HIỆN"));
        assertFalse(result.contains("<script"));
    }

    @Test public void givesAReadableFallbackWithoutReturningRawPublisherMarkup() {
        String result = ReaderArticleExtractor.fallback("https://news.example/a",
                "Tiêu đề", "Tóm tắt bài viết", "", "VnExpress", "22/10/2026", 100,
                "Chỉ có phần tóm tắt.");
        assertTrue(result.contains("Tiêu đề"));
        assertTrue(result.contains("Tóm tắt bài viết"));
        assertTrue(result.contains("Chỉ có phần tóm tắt."));
        assertTrue(result.contains("#101114"));
        assertFalse(result.contains("<script"));
    }
}
