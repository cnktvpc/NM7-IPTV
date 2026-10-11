package vn.phuong.iptvplayer;

import java.net.URI;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

/** Builds a provider-standard Xtream Codes M3U endpoint without logging credentials. */
final class XtreamM3uSource {
    private XtreamM3uSource() { }

    static String buildPlaylistUrl(String server, String username, String password, String output) {
        String cleanServer = server == null ? "" : server.trim();
        String cleanUser = username == null ? "" : username.trim();
        String cleanPassword = password == null ? "" : password;
        String cleanOutput = output == null ? "" : output.trim().toLowerCase(Locale.ROOT);
        if (cleanServer.isEmpty()) throw new IllegalArgumentException("Hãy nhập địa chỉ máy chủ Xtream Codes.");
        if (cleanUser.isEmpty()) throw new IllegalArgumentException("Hãy nhập tên đăng nhập.");
        if (cleanPassword.trim().isEmpty()) throw new IllegalArgumentException("Hãy nhập mật khẩu.");
        if (!cleanOutput.equals("m3u8") && !cleanOutput.equals("ts")) {
            throw new IllegalArgumentException("Định dạng phải là m3u8 hoặc ts.");
        }
        final URI uri;
        try { uri = URI.create(cleanServer); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Địa chỉ máy chủ không hợp lệ."); }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getRawAuthority() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Máy chủ phải là URL HTTP(S) không chứa tài khoản, query hoặc fragment.");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (path.endsWith("/get.php")) path = path.substring(0, path.length() - "/get.php".length());
        else if (path.endsWith("/player_api.php")) path = path.substring(0, path.length() - "/player_api.php".length());
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String endpoint = scheme.toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority() + path + "/get.php";
        return endpoint + "?username=" + encode(cleanUser) + "&password=" + encode(cleanPassword)
                + "&type=m3u_plus&output=" + cleanOutput;
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
