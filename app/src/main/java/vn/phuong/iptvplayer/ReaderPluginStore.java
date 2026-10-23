package vn.phuong.iptvplayer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Persists user-installed JavaScript reader sources in app-private storage. */
public final class ReaderPluginStore {
    private ReaderPluginStore() { }

    public static final class SourceInfo {
        public final String id;
        public final String name;
        public final String type;
        public final String baseUrl;
        public final String description;
        public final String version;
        public final String sourceUrl;
        public final boolean enabled;

        SourceInfo(JSONObject json) {
            id = json.optString("id", "");
            name = json.optString("name", id);
            type = json.optString("type", "comic");
            baseUrl = json.optString("baseUrl", "");
            description = json.optString("description", "");
            version = json.optString("version", "1.0.0");
            sourceUrl = json.optString("sourceUrl", "");
            enabled = json.optBoolean("enabled", true);
        }
    }

    public static List<SourceInfo> list(Context context, String type) {
        File dir = sourceDir(context, type);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        List<SourceInfo> result = new ArrayList<>();
        if (files == null) return result;
        for (File file : files) {
            try {
                JSONObject json = new JSONObject(readFile(file));
                if (type.equals(json.optString("type", ""))) result.add(new SourceInfo(json));
            } catch (Exception ignored) {
                // A broken sidecar should not prevent the remaining installed sources loading.
            }
        }
        Collections.sort(result, Comparator.comparing((SourceInfo x) -> x.name.toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    public static SourceInfo find(Context context, String type, String id) throws Exception {
        File file = metadataFile(context, type, id);
        if (!file.isFile()) throw new IllegalArgumentException("Không tìm thấy nguồn đã cài.");
        JSONObject json = new JSONObject(readFile(file));
        if (!type.equals(json.optString("type", ""))) throw new IllegalArgumentException("Loại nguồn không khớp.");
        return new SourceInfo(json);
    }

    public static String script(Context context, SourceInfo source) throws Exception {
        File file = scriptFile(context, source.type, source.id);
        if (!file.isFile()) throw new IllegalArgumentException("Không tìm thấy tệp plugin .js.");
        String text = readFile(file);
        if (text.getBytes(StandardCharsets.UTF_8).length > ReaderPluginRuntime.MAX_SCRIPT_BYTES) {
            throw new IllegalArgumentException("Plugin vượt quá giới hạn 512 KiB.");
        }
        return text;
    }

    public static SourceInfo install(Context context, String script, String expectedType, String sourceUrl)
            throws Exception {
        JSONObject validated = ReaderPluginRuntime.inspect(script);
        String actualType = validated.optString("type", "");
        if (!expectedType.equals(actualType)) {
            throw new IllegalArgumentException("File này là nguồn " + label(actualType)
                    + ", không phải nhóm " + label(expectedType) + ".");
        }
        String id = validated.getString("id");
        File dir = sourceDir(context, expectedType);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục lưu nguồn.");
        JSONObject sidecar = new JSONObject();
        sidecar.put("id", id);
        sidecar.put("name", validated.optString("name", id));
        sidecar.put("type", actualType);
        sidecar.put("baseUrl", validated.optString("baseUrl", ""));
        sidecar.put("description", validated.optString("description", ""));
        sidecar.put("version", validated.optString("version", "1.0.0"));
        sidecar.put("sourceUrl", sourceUrl == null ? "" : sourceUrl.trim());
        sidecar.put("enabled", true);
        sidecar.put("installedAt", System.currentTimeMillis());

        writeAtomic(scriptFile(context, expectedType, id), script);
        writeAtomic(metadataFile(context, expectedType, id), sidecar.toString(2));
        return new SourceInfo(sidecar);
    }

    public static void setEnabled(Context context, SourceInfo source, boolean enabled) throws Exception {
        File file = metadataFile(context, source.type, source.id);
        JSONObject json = new JSONObject(readFile(file));
        json.put("enabled", enabled);
        writeAtomic(file, json.toString(2));
    }

    public static void remove(Context context, SourceInfo source) {
        File js = scriptFile(context, source.type, source.id);
        File json = metadataFile(context, source.type, source.id);
        if (js.exists()) js.delete();
        if (json.exists()) json.delete();
    }

    public static String download(String url) throws Exception {
        return ReaderPluginRuntime.downloadScript(url);
    }

    private static File sourceDir(Context context, String type) {
        String safeType;
        if ("novel".equals(type)) safeType = "novel";
        else if ("news".equals(type)) safeType = "news";
        else safeType = "comic";
        return new File(new File(context.getFilesDir(), "reader-plugins"), safeType);
    }

    private static File scriptFile(Context context, String type, String id) {
        return new File(sourceDir(context, type), safeId(id) + ".js");
    }

    private static File metadataFile(Context context, String type, String id) {
        return new File(sourceDir(context, type), safeId(id) + ".json");
    }

    private static String safeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("ID nguồn không hợp lệ.");
        }
        return id;
    }

    private static String readFile(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (out.size() + n > ReaderPluginRuntime.MAX_SCRIPT_BYTES + 16384) {
                    throw new IllegalArgumentException("Dữ liệu plugin quá lớn.");
                }
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeAtomic(File target, String content) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IllegalStateException("Không cập nhật được tệp nguồn hiện tại.");
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IllegalStateException("Không lưu được tệp nguồn.");
        }
    }

    private static String label(String type) {
        if ("comic".equals(type)) return "Truyện tranh";
        if ("novel".equals(type)) return "Truyện chữ";
        if ("news".equals(type)) return "Báo";
        return type;
    }
}
