package vn.phuong.iptvplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class XtreamM3uSourceTest {
    @Test public void buildsM3u8EndpointAndEncodesCredentials() {
        String url = XtreamM3uSource.buildPlaylistUrl(
                "http://portal.example:8080/", "user name", "p+ ss&?#", "m3u8");
        assertEquals("http://portal.example:8080/get.php?username=user+name&password=p%2B+ss%26%3F%23&type=m3u_plus&output=m3u8", url);
    }
    @Test public void acceptsServerPathAndCommonXtreamEndpoints() {
        assertEquals("https://portal.example/xtream/get.php?username=u&password=p&type=m3u_plus&output=ts",
                XtreamM3uSource.buildPlaylistUrl("https://portal.example/xtream/player_api.php", "u", "p", "ts"));
    }
    @Test public void rejectsUnsupportedSchemesAndCredentialsInServerUrl() {
        assertThrows(IllegalArgumentException.class, () -> XtreamM3uSource.buildPlaylistUrl("ftp://portal.example", "u", "p", "m3u8"));
        assertThrows(IllegalArgumentException.class, () -> XtreamM3uSource.buildPlaylistUrl("https://user@portal.example", "u", "p", "m3u8"));
        assertThrows(IllegalArgumentException.class, () -> XtreamM3uSource.buildPlaylistUrl("https://portal.example", "u", "p", "mp4"));
    }
}
