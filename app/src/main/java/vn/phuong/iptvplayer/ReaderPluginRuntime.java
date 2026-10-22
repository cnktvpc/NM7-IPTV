package vn.phuong.iptvplayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

/**
 * Small, deliberately restricted JavaScript source-plugin runtime.
 * Plug-ins see only the NM7 HTTP/DOM facade; Java packages/reflection are hidden.
 *
 * Source contract:
 * var NM7_SOURCE = {
 *   id: "site-id", name: "Site name", type: "comic", baseUrl: "https://site.example",
 *   description: "Optional",
 *   popular: function(page) { ... return [{title:"...", url:"...", cover:"..."}]; },
 *   search: function(query,page) { ... return [{title:"...", url:"...", cover:"..."}]; },
 *   details: function(url) { ... return {title:"...", cover:"...", description:"..."}; },
 *   chapters: function(url) { ... return [{title:"Chapter 1", url:"..."}]; },
 *   pages: function(chapterUrl) { ... return ["https://.../1.jpg", ...]; },
 *   chapter: function(chapterUrl) { ... return "<p>...</p>"; } // text/novel source
 * };
 *
 * The helpers are NM7.http.get(url) and NM7.dom.{text,attr,select}(html,...).
 */
public final class ReaderPluginRuntime {
    public static final int MAX_SCRIPT_BYTES = 512 * 1024;
    private static final int MAX_HTTP_BYTES = 5 * 1024 * 1024;

    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(250000);
            cx.setClassShutter(new ClassShutter() {
                @Override public boolean visibleToScripts(String className) {
                    return false;
                }
            });
            return cx;
        }
        @Override protected void observeInstructionCount(Context cx, int instructionCount) {
            throw new EvaluatorException("JavaScript source vượt giới hạn thực thi; đã dừng để bảo vệ ứng dụng.");
        }
    };

    private ReaderPluginRuntime() { }

    public static JSONObject inspect(String script) throws Exception {
        checkScript(script);
        return withSource(script, (cx, scope, source) -> {
            String id = propertyString(source, "id");
            String name = propertyString(source, "name");
            String type = normalizeType(propertyString(source, "type"));
            String baseUrl = propertyString(source, "baseUrl");
            String description = propertyString(source, "description");
            String version = propertyString(source, "version");
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
                throw new IllegalArgumentException("Plugin cần id ngắn, chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.");
            }
            if (name.trim().isEmpty()) throw new IllegalArgumentException("Plugin chưa khai báo name.");
            if (!"comic".equals(type) && !"novel".equals(type)) {
                throw new IllegalArgumentException("type phải là comic hoặc novel.");
            }
            if (!isHttpUrl(baseUrl)) throw new IllegalArgumentException("baseUrl phải là URL http/https hợp lệ.");
            boolean hasSearch = hasAny(source, "search", "searchManga", "searchNovels");
            boolean hasPopular = hasAny(source, "popular", "latest", "home");
            boolean hasDetails = hasAny(source, "details", "mangaDetails", "getDetails", "getMangaDetails");
            boolean hasChapters = hasAny(source, "chapters", "getChapters", "getChapterList");
            boolean hasPages = hasAny(source, "pages", "getPages", "chapterPages");
            boolean hasChapter = hasAny(source, "chapter", "chapterContent", "getChapterContent");
            if (!hasSearch && !hasPopular) throw new IllegalArgumentException("Plugin phải có hàm search(query,page) hoặc popular(page).");
            if ("comic".equals(type) && !hasPages) throw new IllegalArgumentException("Nguồn truyện tranh cần hàm pages(chapterUrl).");
            if ("novel".equals(type) && !hasChapter) throw new IllegalArgumentException("Nguồn truyện chữ cần hàm chapter(chapterUrl).");
            if (!hasDetails && !hasChapters) throw new IllegalArgumentException("Plugin cần hàm details(url) hoặc chapters(url).");

            JSONObject out = new JSONObject();
            out.put("id", id);
            out.put("name", name);
            out.put("type", type);
            out.put("baseUrl", baseUrl);
            out.put("description", description);
            out.put("version", version.isEmpty() ? "1.0.0" : version);
            return out;
        });
    }

    public static Object invoke(String script, String method, JSONArray args) throws Exception {
        checkScript(script);
        return withSource(script, (cx, scope, source) -> {
            String[] aliases = aliasesFor(method);
            Function fn = null;
            for (String name : aliases) {
                Object candidate = ScriptableObject.getProperty(source, name);
                if (candidate instanceof Function) {
                    fn = (Function) candidate;
                    break;
                }
            }
            if (fn == null) throw new NoSuchMethodException("Nguồn chưa hỗ trợ hàm " + method + "().");
            Object[] jsArgs = toJsArgs(args);
            Object result = fn.call(cx, scope, source, jsArgs);
            if (result == Undefined.instance) return null;
            Object jsonObject = ScriptableObject.getProperty(scope, "JSON");
            Object stringifyObject = ScriptableObject.getProperty((Scriptable) jsonObject, "stringify");
            if (!(stringifyObject instanceof Function)) return Context.toString(result);
            Object serialized = ((Function) stringifyObject).call(cx, scope, (Scriptable) jsonObject, new Object[]{result});
            if (serialized == null || serialized == Undefined.instance) return null;
            String json = Context.toString(serialized);
            if ("undefined".equals(json)) return null;
            try {
                return new JSONTokener(json).nextValue();
            } catch (JSONException ignored) {
                return json;
            }
        });
    }

    public static String downloadScript(String sourceUrl) throws IOException {
        if (!isHttpUrl(sourceUrl)) throw new IOException("URL plugin phải bắt đầu bằng http:// hoặc https://.");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NM7-IPTV-Mobile/1.10.145 (Reader Plugin Installer)");
            connection.setRequestProperty("Accept", "text/javascript,application/javascript,text/plain,*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("Máy chủ trả HTTP " + code);
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = readBounded(in, MAX_SCRIPT_BYTES);
                if (bytes.length == MAX_SCRIPT_BYTES) throw new IOException("File .js quá lớn (giới hạn 512 KiB).");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private interface SourceAction<T> {
        T run(Context cx, Scriptable scope, Scriptable source) throws Exception;
    }

    private static <T> T withSource(String script, SourceAction<T> action) throws Exception {
        return FACTORY.call(new ContextAction<T>() {
            @Override public T run(Context cx) {
                try {
                    Scriptable scope = cx.initStandardObjects();
                    installApi(cx, scope);
                    ScriptableObject module = (ScriptableObject) cx.newObject(scope);
                    ScriptableObject exports = (ScriptableObject) cx.newObject(scope);
                    ScriptableObject.putProperty(module, "exports", exports);
                    ScriptableObject.putProperty(scope, "module", module);
                    ScriptableObject.putProperty(scope, "exports", exports);

                    final Scriptable[] registered = new Scriptable[1];
                    BaseFunction register = function(scope, "registerSource", (context, args) -> {
                        if (args.length > 0 && args[0] instanceof Scriptable) registered[0] = (Scriptable) args[0];
                        return args.length > 0 ? args[0] : Undefined.instance;
                    });
                    ScriptableObject.putProperty(scope, "registerSource", register);
                    cx.evaluateString(scope, script, "NM7-source-plugin.js", 1, null);
                    Scriptable source = registered[0];
                    if (source == null) source = asScriptable(ScriptableObject.getProperty(scope, "NM7_SOURCE"));
                    if (source == null) source = asScriptable(ScriptableObject.getProperty(scope, "source"));
                    if (source == null) source = asScriptable(ScriptableObject.getProperty(module, "exports"));
                    if (source == null) source = asScriptable(ScriptableObject.getProperty(scope, "exports"));
                    if (source == null) throw new IllegalArgumentException("Không tìm thấy NM7_SOURCE hoặc module.exports trong file .js.");
                    return action.run(cx, scope, source);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new WrappedSourceException(e);
                }
            }
        });
    }

    private static Scriptable asScriptable(Object object) {
        if (object instanceof Scriptable && !(object instanceof Function)) {
            Scriptable value = (Scriptable) object;
            if (value instanceof ScriptableObject) {
                Object id = ScriptableObject.getProperty(value, "id");
                Object name = ScriptableObject.getProperty(value, "name");
                if (id != Scriptable.NOT_FOUND || name != Scriptable.NOT_FOUND) return value;
            }
        }
        return null;
    }

    private static void installApi(Context cx, Scriptable scope) {
        ScriptableObject root = (ScriptableObject) cx.newObject(scope);
        ScriptableObject http = (ScriptableObject) cx.newObject(scope);
        ScriptableObject dom = (ScriptableObject) cx.newObject(scope);
        http.put("get", http, function(scope, "get", (context, args) ->
                httpGet(arg(args, 0))));
        dom.put("text", dom, function(scope, "text", (context, args) ->
                domText(arg(args, 0), arg(args, 1), arg(args, 2))));
        dom.put("attr", dom, function(scope, "attr", (context, args) ->
                domAttr(arg(args, 0), arg(args, 1), arg(args, 2), arg(args, 3))));
        dom.put("select", dom, function(scope, "select", (context, args) ->
                domSelect(arg(args, 0), arg(args, 1), arg(args, 2), arg(args, 3))));
        root.put("http", root, http);
        root.put("dom", root, dom);
        ScriptableObject.putProperty(scope, "NM7", root);
    }

    private interface ApiCall {
        Object call(Context cx, Object[] args) throws Exception;
    }

    private static BaseFunction function(Scriptable scope, String name, ApiCall action) {
        BaseFunction function = new BaseFunction() {
            @Override public Object call(Context cx, Scriptable callScope, Scriptable thisObj, Object[] args) {
                try {
                    return action.call(cx, args);
                } catch (Exception e) {
                    throw Context.throwAsScriptRuntimeEx(e);
                }
            }
            @Override public String getFunctionName() { return name; }
        };
        function.setParentScope(scope);
        function.setPrototype(ScriptableObject.getFunctionPrototype(scope));
        return function;
    }

    private static String httpGet(String url) throws IOException {
        if (!isHttpUrl(url)) throw new IOException("NM7.http.get chỉ cho phép URL http/https.");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36");
            conn.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.5");
            conn.setRequestProperty("Accept", "text/html,application/json,application/xml,*/*");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) throw new IOException("HTTP " + code + " khi tải " + url);
            try (InputStream input = conn.getInputStream()) {
                byte[] bytes = readBounded(input, MAX_HTTP_BYTES);
                if (bytes.length == MAX_HTTP_BYTES) throw new IOException("Nội dung nguồn vượt giới hạn 5 MiB.");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String domText(String html, String baseUrl, String selector) {
        Document doc = Jsoup.parse(nullToEmpty(html), nullToEmpty(baseUrl));
        Element node = selectOne(doc, selector);
        return node == null ? "" : node.text();
    }

    private static String domAttr(String html, String baseUrl, String selector, String attr) {
        Document doc = Jsoup.parse(nullToEmpty(html), nullToEmpty(baseUrl));
        Element node = selectOne(doc, selector);
        if (node == null || attr == null || attr.isEmpty()) return "";
        String value = node.attr(attr);
        if (value.isEmpty() && ("src".equalsIgnoreCase(attr) || "href".equalsIgnoreCase(attr))) {
            value = node.attr("data-src");
            if (value.isEmpty()) value = node.attr("data-original");
            if (value.isEmpty()) value = node.attr("data-lazy-src");
        }
        if ("src".equalsIgnoreCase(attr) || "href".equalsIgnoreCase(attr)) {
            String absolute = node.absUrl(attr);
            if (absolute.isEmpty() && !value.isEmpty()) absolute = node.absUrl("data-src");
            return absolute.isEmpty() ? value : absolute;
        }
        return value;
    }

    private static String domSelect(String html, String baseUrl, String selector, String fieldsJson)
            throws JSONException {
        Document doc = Jsoup.parse(nullToEmpty(html), nullToEmpty(baseUrl));
        JSONObject fields = new JSONObject(fieldsJson == null || fieldsJson.trim().isEmpty() ? "{}" : fieldsJson);
        JSONArray out = new JSONArray();
        for (Element item : doc.select(selector == null || selector.isEmpty() ? "*" : selector)) {
            JSONObject row = new JSONObject();
            Iterator<String> keys = fields.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject rule = fields.optJSONObject(key);
                if (rule == null) continue;
                String descendant = rule.optString("selector", "");
                String attr = rule.optString("attr", "text");
                Element node = descendant.isEmpty() ? item : item.selectFirst(descendant);
                String value = "";
                if (node != null) {
                    if ("text".equalsIgnoreCase(attr)) value = node.text();
                    else if ("html".equalsIgnoreCase(attr)) value = node.html();
                    else {
                        String raw = node.attr(attr);
                        if (raw.isEmpty() && ("src".equalsIgnoreCase(attr) || "href".equalsIgnoreCase(attr))) {
                            raw = node.attr("data-src");
                            if (raw.isEmpty()) raw = node.attr("data-original");
                            if (raw.isEmpty()) raw = node.attr("data-lazy-src");
                        }
                        if ("src".equalsIgnoreCase(attr) || "href".equalsIgnoreCase(attr)) {
                            String absolute = node.absUrl(attr);
                            if (absolute.isEmpty()) absolute = node.absUrl("data-src");
                            if (absolute.isEmpty()) absolute = node.absUrl("data-original");
                            value = absolute.isEmpty() ? raw : absolute;
                        } else value = raw;
                    }
                }
                row.put(key, value);
            }
            out.put(row);
            if (out.length() >= 500) break;
        }
        return out.toString();
    }

    private static Element selectOne(Document document, String selector) {
        if (selector == null || selector.trim().isEmpty()) return document.body();
        return document.selectFirst(selector);
    }

    private static String propertyString(Scriptable source, String key) {
        Object value = ScriptableObject.getProperty(source, key);
        if (value == Scriptable.NOT_FOUND || value == null || value == Undefined.instance) return "";
        return Context.toString(value).trim();
    }

    private static boolean hasAny(Scriptable source, String... names) {
        for (String name : names) if (ScriptableObject.getProperty(source, name) instanceof Function) return true;
        return false;
    }

    private static String[] aliasesFor(String method) {
        switch (method) {
            case "popular": return new String[]{"popular", "latest", "home"};
            case "search": return new String[]{"search", "searchManga", "searchNovels"};
            case "details": return new String[]{"details", "mangaDetails", "getDetails", "getMangaDetails"};
            case "chapters": return new String[]{"chapters", "getChapters", "getChapterList"};
            case "pages": return new String[]{"pages", "getPages", "chapterPages"};
            case "chapter": return new String[]{"chapter", "chapterContent", "getChapterContent"};
            default: return new String[]{method};
        }
    }

    private static Object[] toJsArgs(JSONArray args) throws JSONException {
        if (args == null) return new Object[0];
        Object[] out = new Object[args.length()];
        for (int i = 0; i < args.length(); i++) {
            Object value = args.get(i);
            if (value == JSONObject.NULL) out[i] = null;
            else if (value instanceof Number || value instanceof Boolean || value instanceof String) out[i] = value;
            else out[i] = value.toString();
        }
        return out;
    }

    private static String arg(Object[] args, int index) {
        if (args == null || index >= args.length || args[index] == null || args[index] == Undefined.instance) return "";
        return Context.toString(args[index]);
    }

    private static void checkScript(String script) {
        if (script == null || script.trim().isEmpty()) throw new IllegalArgumentException("File .js đang trống.");
        if (script.getBytes(StandardCharsets.UTF_8).length > MAX_SCRIPT_BYTES) {
            throw new IllegalArgumentException("File .js quá lớn (giới hạn 512 KiB).");
        }
    }

    private static String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if ("manga".equals(type) || "mangas".equals(type) || "truyen-tranh".equals(type)) return "comic";
        if ("book".equals(type) || "novel".equals(type) || "text".equals(type) || "truyen-chu".equals(type)) return "novel";
        return type;
    }

    private static boolean isHttpUrl(String raw) {
        if (raw == null) return false;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return (value.startsWith("http://") || value.startsWith("https://")) && value.length() > 10;
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private static byte[] readBounded(InputStream input, int max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (output.size() + count > max) throw new IOException("Dữ liệu tải về vượt giới hạn cho phép.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static final class WrappedSourceException extends RuntimeException {
        WrappedSourceException(Exception cause) { super(cause); }
    }
}
