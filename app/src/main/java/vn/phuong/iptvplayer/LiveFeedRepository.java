package vn.phuong.iptvplayer;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** Independent Live-feed source; it never reads or writes the user's selected IPTV playlist. */
final class LiveFeedRepository {
    static final String FEED_URL = "https://thethaonm7.phuongnm7-iptv.workers.dev/playlist.m3u";
    private static final String CACHE_FILE = "nm7-live-feed.m3u";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private LiveFeedRepository() { }

    static String readCache(Context context) {
        File file = new File(context.getApplicationContext().getCacheDir(), CACHE_FILE);
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) return "";
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            return readLimited(input);
        } catch (Exception ignored) {
            return "";
        }
    }

    static void writeCache(Context context, String content) {
        if (content == null || content.trim().isEmpty() || content.length() > MAX_BYTES) return;
        File file = new File(context.getApplicationContext().getCacheDir(), CACHE_FILE);
        File temporary = new File(file.getParentFile(), CACHE_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception ignored) {
            // A cache failure must never break the Live list or the regular playlist.
            return;
        }
        if (!temporary.renameTo(file)) {
            try (InputStream input = new FileInputStream(temporary);
                 FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            } catch (Exception ignored) { }
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        }
    }

    static String download() throws Exception {
        HttpURLConnection connection = null;
        try {
            // Some edge caches ignore Cache-Control on M3U routes. Keep the official
            // source path, but add a unique query key so periodic refreshes fetch current data.
            String separator = FEED_URL.contains("?") ? "&" : "?";
            URL address = new URL(FEED_URL + separator + "_nm7_refresh=" + System.currentTimeMillis());
            connection = (HttpURLConnection) address.openConnection();
            connection.setUseCaches(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NM7-IPTV-Mobile-Live/1.10.133 Android");
            connection.setRequestProperty("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("If-Modified-Since", "0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
            String content;
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                content = readLimited(input);
            }
            if (content.trim().isEmpty() || !content.replace("\uFEFF", "").trim().startsWith("#EXTM3U")) {
                throw new IllegalStateException("Nguồn Live không trả về playlist M3U hợp lệ");
            }
            return content;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static List<LiveEvent> parse(String content, long nowMillis) {
        if (content == null || content.trim().isEmpty()) return Collections.emptyList();
        M3uParser.Result parsed = new M3uParser().parse(content, FEED_URL);
        return LiveEvent.fromDedicatedFeed(parsed.channels, nowMillis);
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new IllegalStateException("Nguồn Live vượt quá giới hạn 8 MB");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
