package vn.phuong.iptvplayer;

import org.junit.Test;

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
