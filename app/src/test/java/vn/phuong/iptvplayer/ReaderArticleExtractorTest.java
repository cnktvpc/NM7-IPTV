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

    @Test public void resolvesLazyImagesAndPrefersHighResolutionSrcset() {
        String html = "<article><p>Đoạn tin này mô tả đầy đủ thông tin của bài báo để trình trích xuất "
                + "xác định đúng vùng nội dung chính. Nội dung bao gồm nhiều câu, ngữ cảnh và chi tiết cần thiết "
                + "để vượt qua ngưỡng lọc bài viết, đồng thời bảo đảm ảnh bên trong được kiểm tra thực sự. "
                + "Đoạn văn bổ sung mô tả bối cảnh của bản tin và các thông tin liên quan để vùng nội dung dài "
                + "hơn mức tối thiểu mà trình trích xuất yêu cầu, tránh nhầm lẫn giữa phần tiêu đề, phần tóm tắt "
                + "và nội dung chính. Đây là dữ liệu kiểm thử cố ý đủ dài để bài viết được nhận diện hợp lệ.</p>"
                + "<figure><img src='data:image/gif;base64,AAAA' data-src='/img/low.jpg' "
                + "srcset='/img/small.jpg 320w, /img/large.jpg 1280w'></figure>"
                + "<img data-original='//cdn.news.example/story.jpg' src='/blank.gif'>"
                + "</article>";
        String result = ReaderArticleExtractor.renderForTest(html, "https://news.example/a/story",
                "Bài thử ảnh", "", "/hero.jpg", "Nguồn", "", 100);
        assertTrue(result.contains("https://news.example/img/large.jpg"));
        assertTrue(result.contains("https://cdn.news.example/story.jpg"));
        assertTrue(result.contains("https://news.example/hero.jpg"));
        assertFalse(result.contains("data:image/gif"));
        assertTrue(result.contains("loading=\"eager\""));
    }

    @Test public void resolvesAdditionalPublisherLazyImageAttributes() {
        String html = "<article><p>Đoạn tin dài đủ để nhận diện nội dung chính và kiểm tra ảnh lazy load từ "
                + "nhiều hệ thống báo khác nhau. Bài viết có các chi tiết về sự kiện, nhân vật và bối cảnh "
                + "để bảo đảm trình trích xuất nhận ra vùng bài viết hợp lệ.</p>"
                + "<img data-lazy-srcset='/img/a.jpg 400w, /img/b.jpg 1200w' src='/pixel.gif'>"
                + "<img data-ks-lazyload='//cdn.news.example/c.jpg' src='/blank.png'>"
                + "</article>";
        String result = ReaderArticleExtractor.renderForTest(html, "https://news.example/a/story",
                "Bài thử", "", "", "Nguồn", "", 100);
        assertTrue(result.contains("https://news.example/img/b.jpg"));
        assertTrue(result.contains("https://cdn.news.example/c.jpg"));
        assertFalse(result.contains("data-lazy-srcset"));
        assertFalse(result.contains("data-ks-lazyload"));
    }

    @Test public void normalizesMixedInlineFormattingInsideArticleParagraphs() {
        String html = "<article><p>Đoạn mở đầu bình thường đủ dài cho bộ trích xuất nhận ra bài báo. "
                + "<strong>Phần in đậm</strong> <b>phần đậm khác</b> "
                + "<em>phần nghiêng</em> <span style='font-size:32px;font-weight:700'>phần phóng lớn</span> "
                + "và phần cuối đoạn vẫn cần có cùng cỡ chữ, kiểu chữ và độ đậm.</p></article>";
        String result = ReaderArticleExtractor.renderForTest(html, "https://news.example/a/typography",
                "Bài thử font", "", "", "Nguồn", "", 100);

        assertTrue(result.contains("Phần in đậm"));
        assertTrue(result.contains("phần phóng lớn"));
        assertTrue(result.contains(".body p,.body li,.body blockquote,.body td{font-family:Roboto,'Noto Sans',Arial,sans-serif;font-size:19px;font-weight:400;font-style:normal;line-height:1.82;}"));
        assertTrue(result.contains(".body p *,.body li *,.body blockquote *,.body td *{font-family:inherit!important;font-size:inherit!important;font-weight:400!important;font-style:normal!important;line-height:inherit!important;}"));
    }

    @Test public void appendsRelatedStoriesInsideReaderInsteadOfOpeningPublisherHome() {
        java.util.List<ReaderArticleExtractor.RelatedArticle> related = java.util.Arrays.asList(
                new ReaderArticleExtractor.RelatedArticle("Bài báo liên quan", "https://news.example/story/2",
                        "Tóm tắt bài liên quan", "https://news.example/image.jpg", "Nguồn thử"));
        String result = ReaderArticleExtractor.renderForTestWithRelated(
                "<article><p>Nội dung bài viết đủ dài để kiểm tra phần đọc bài riêng biệt của NM7 "
                        + "có thể hiển thị nội dung trong vùng đọc mà không chuyển sang trang web ngoài.</p></article>",
                "https://news.example/story/1", "Bài đang đọc", "Tóm tắt", "",
                "Nguồn thử", "22/10/2026", related, 100);
        assertTrue(result.contains("Bài viết liên quan"));
        assertTrue(result.contains("Bài báo liên quan"));
        assertTrue(result.contains("nm7://related/0"));
        assertFalse(result.contains("target=\"_blank\""));
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
