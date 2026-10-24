package vn.phuong.iptvplayer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** URL policy for the first-party TikTok mobile site hosted inside the NM7 APK. */
public final class TikTokWebPolicy {
    public static final String START_URL = "https://www.tiktok.com/foryou?lang=vi-VN";

    private TikTokWebPolicy() { }

    /** Accept only normal HTTP(S) URLs with a parsed host and no embedded credentials. */
    public static boolean isWebUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return false;
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null
                    && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && host != null && !host.isEmpty()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * Rewrites Android WebView's embedded-app marker into a normal mobile-browser UA.
     * Keep the actual Chromium/device tokens so responsive rendering still matches the phone.
     */
    public static String normalizeMobileUserAgent(String userAgent) {
        if (userAgent == null) return "";
        return userAgent.replace("; wv", "").replace(" Version/4.0", "");
    }

    /** HTTP(S) links stay inside NM7; all custom URI schemes must be consumed by the app. */
    public static boolean shouldStayInWebView(String rawUrl) {
        return isWebUrl(rawUrl);
    }

    /** Identifies TikTok-owned domains without treating look-alike hostnames as TikTok. */
    public static boolean isTikTokUrl(String rawUrl) {
        if (!isWebUrl(rawUrl)) return false;
        try {
            String host = new URI(rawUrl.trim()).getHost().toLowerCase(Locale.ROOT);
            return "tiktok.com".equals(host) || host.endsWith(".tiktok.com");
        } catch (URISyntaxException | NullPointerException ignored) {
            return false;
        }
    }
}
