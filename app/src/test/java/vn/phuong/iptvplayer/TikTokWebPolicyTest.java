package vn.phuong.iptvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TikTokWebPolicyTest {
    @Test
    public void startUrlIsTheOfficialFirstPartyForYouPage() {
        assertTrue(TikTokWebPolicy.isWebUrl(TikTokWebPolicy.START_URL));
        assertTrue(TikTokWebPolicy.isTikTokUrl(TikTokWebPolicy.START_URL));
        assertTrue(TikTokWebPolicy.START_URL.startsWith("https://www.tiktok.com/foryou"));
    }

    @Test
    public void acceptsHttpsAndHttpPagesInsideTheSingleAppWebView() {
        assertTrue(TikTokWebPolicy.isWebUrl("https://www.tiktok.com/@creator/video/1234567890123456789"));
        assertTrue(TikTokWebPolicy.isWebUrl("https://www.tiktok.com/login"));
        assertTrue(TikTokWebPolicy.isWebUrl("https://accounts.google.com/"));
        assertTrue(TikTokWebPolicy.isWebUrl("http://example.com/redirect"));
    }

    @Test
    public void onlyRecognizesTikTokOwnedDomainsAsTikTok() {
        assertTrue(TikTokWebPolicy.isTikTokUrl("https://tiktok.com/"));
        assertTrue(TikTokWebPolicy.isTikTokUrl("https://www.tiktok.com/"));
        assertTrue(TikTokWebPolicy.isTikTokUrl("https://m.tiktok.com/"));
        assertFalse(TikTokWebPolicy.isTikTokUrl("https://www.tiktok.com.evil.example/"));
        assertFalse(TikTokWebPolicy.isTikTokUrl("https://tiktok.example/"));
    }

    @Test
    public void normalizesWebViewUserAgentForMobileWebsiteRendering() {
        String ua = "Mozilla/5.0 (Linux; Android 15; Device; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/134.0.0.0 Mobile Safari/537.36";
        String mobile = TikTokWebPolicy.normalizeMobileUserAgent(ua);
        assertFalse(mobile.contains("; wv"));
        assertFalse(mobile.contains("Version/4.0"));
        assertTrue(mobile.contains("Android 15"));
        assertTrue(mobile.contains("Mobile Safari"));
        assertEquals("", TikTokWebPolicy.normalizeMobileUserAgent(null));
    }

    @Test
    public void keepsOnlyWebSchemesInsideNm7AndBlocksAppDeepLinks() {
        assertTrue(TikTokWebPolicy.shouldStayInWebView("https://www.tiktok.com/foryou"));
        assertTrue(TikTokWebPolicy.shouldStayInWebView("https://www.tiktok.com/login"));
        assertFalse(TikTokWebPolicy.shouldStayInWebView("tiktok://user/profile"));
        assertFalse(TikTokWebPolicy.shouldStayInWebView("intent://foryou#Intent;scheme=https;end"));
        assertFalse(TikTokWebPolicy.shouldStayInWebView("market://details?id=com.zhiliaoapp.musically"));
        assertFalse(TikTokWebPolicy.shouldStayInWebView("javascript:window.location='tiktok://home'"));
    }

    @Test
    public void rejectsJavascriptFilesAndCredentialSmugglingUrls() {
        assertFalse(TikTokWebPolicy.isWebUrl("javascript:alert(1)"));
        assertFalse(TikTokWebPolicy.isWebUrl("file:///data/local/tmp/page.html"));
        assertFalse(TikTokWebPolicy.isWebUrl("intent://foryou#Intent;scheme=https;end"));
        assertFalse(TikTokWebPolicy.isWebUrl("https://user:password@www.tiktok.com/"));
        assertFalse(TikTokWebPolicy.isWebUrl("not a URL"));
        assertFalse(TikTokWebPolicy.isWebUrl(""));
        assertFalse(TikTokWebPolicy.isWebUrl(null));
    }
}
