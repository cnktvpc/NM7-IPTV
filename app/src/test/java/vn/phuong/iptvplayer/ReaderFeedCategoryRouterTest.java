package vn.phuong.iptvplayer;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReaderFeedCategoryRouterTest {
    @Test public void homepageIsAllowedToMixSectionsButCategoryIsNot() {
        String homepageHeadline = "https://www.24h.com.vn/tin-tuc-trong-ngay/story-c46a123.html";
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "Trang chủ", homepageHeadline));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá", homepageHeadline));
    }

    @Test public void footballTabRejectsCrimeAndHomepageStories() {
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá",
                "https://www.24h.com.vn/video-bong-da/video-bong-da-demo-c48a123.html"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá",
                "https://www.24h.com.vn/an-ninh-xa-hoi/tin-an-ninh-c51a123.html"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá",
                "https://www.24h.com.vn/tin-tuc-trong-ngay/tin-chung-c46a123.html"));
    }

    @Test public void crimeTabDoesNotShowFootball() {
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "An ninh - Hình sự",
                "https://www.24h.com.vn/an-ninh-xa-hoi/tin-an-ninh-c51a123.html"));
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "An ninh - Hình sự",
                "https://www.24h.com.vn/an-ninh-hinh-su/vu-an-demo-c51a123.html"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "An ninh - Hình sự",
                "https://www.24h.com.vn/bong-da/tin-bong-da-c48a123.html"));
    }

    @Test public void fashionAndHiTechAreSeparateCategories() {
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "Thời trang",
                "https://www.24h.com.vn/thoi-trang/tin-thoi-trang-c78a123.html"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Thời trang",
                "https://www.24h.com.vn/thoi-trang-hi-tech/tin-cong-nghe-c407a123.html"));
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "Thời trang Hi-tech",
                "https://www.24h.com.vn/thoi-trang-hi-tech/tin-cong-nghe-c407a123.html"));
    }

    @Test public void a24hCategoryNeverFallsBackToHomePage() {
        String[] categories = {
                "Tin tức", "Bóng đá", "Ngoại Hạng Anh", "Thế giới", "Kinh doanh", "Giải trí",
                "An ninh - Hình sự", "Thời trang", "Thời trang Hi-tech",
                "Tài chính - Bất động sản", "Ẩm thực", "Làm đẹp", "Phim", "Giáo dục - du học",
                "Bạn trẻ - Cuộc sống", "Ca nhạc - MTV", "Thể thao", "Phi thường - kỳ quặc",
                "Công nghệ", "Ô tô", "Thị trường - Tiêu dùng", "Du lịch", "Sức khỏe đời sống", "Cười 24h"
        };
        for (String category : categories) {
            String page = ReaderFeedCategoryRouter.htmlPageUrl("24h", category, "https://www.24h.com.vn/");
            assertNotNull("Missing HTML route for " + category, page);
            assertFalse("Category fell back to homepage: " + category,
                    page.isEmpty() || "https://www.24h.com.vn/".equals(page));
        }
    }

    @Test public void englishPremierLeagueUsesOfficialListingAndAllowsFootballStoryRoutes() {
        assertEquals("https://www.24h.com.vn/bong-da-ngoai-hang-anh-c149.html",
                ReaderFeedCategoryRouter.htmlPageUrl("24h", "Ngoại Hạng Anh", "https://www.24h.com.vn/"));
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("24h", "Ngoại Hạng Anh",
                "https://www.24h.com.vn/bong-da/dua-vo-dich-ngoai-hang-anh-c48a1181471.html"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Ngoại Hạng Anh",
                "https://example.com/bong-da/ngoai-hang-anh.html"));
    }

    @Test public void categoryRouterLeavesOtherPublishersUnchanged() {
        assertEquals("https://vnexpress.net/the-thao",
                ReaderFeedCategoryRouter.htmlPageUrl("vnexpress", "Thể thao", "https://vnexpress.net/the-thao"));
        assertTrue(ReaderFeedCategoryRouter.matchesArticle("vnexpress", "Thể thao",
                "https://vnexpress.net/the-gioi/example.html"));
    }

    @Test public void foreignAndMalformedUrlsCannotLeakInto24hCategories() {
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá",
                "https://example.com/bong-da/hello.html"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá", "not a URL"));
        assertFalse(ReaderFeedCategoryRouter.matchesArticle("24h", "Bóng đá", ""));
    }
}
