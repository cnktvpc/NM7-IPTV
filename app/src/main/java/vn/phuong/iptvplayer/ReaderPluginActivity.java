package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Source manager and reader for user-installed JavaScript novel/comic sources.
 * All imported scripts run in ReaderPluginRuntime's restricted JS environment.
 */
public final class ReaderPluginActivity extends Activity {
    public static final String EXTRA_TYPE = "reader_plugin_type";

    private static final int BG = Color.rgb(16, 17, 20);
    private static final int SURFACE = Color.rgb(29, 30, 37);
    private static final int SURFACE_2 = Color.rgb(39, 41, 49);
    private static final int TEXT = Color.rgb(242, 243, 247);
    private static final int MUTED = Color.rgb(164, 167, 178);
    private static final int ORANGE = Color.rgb(255, 132, 20);
    private static final int REQUEST_IMPORT_SCRIPT = 7401;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    private LinearLayout root;
    private LinearLayout toolbar;
    private FrameLayout content;
    private String type = "comic";
    private String typeLabel = "Truyện tranh";
    private Mode mode = Mode.SOURCES;
    private ReaderPluginStore.SourceInfo activeSource;
    private String activeScript = "";
    private JSONObject activeItem;
    private JSONObject activeDetail;
    private JSONObject activeChapter;
    private JSONArray activeChapters = new JSONArray();
    private JSONArray currentRows = new JSONArray();
    private String lastReaderHtml = "";
    private String searchText = "";
    private int fontScale = 100;
    private int requestToken = 0;

    private enum Mode { SOURCES, RESULTS, DETAIL, CHAPTERS, READER }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (!"novel".equals(type)) type = "comic";
        typeLabel = "novel".equals(type) ? "Truyện chữ" : "Truyện tranh";
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, 0, 0, 0);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                top = insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
                bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.requestApplyInsets();

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(2), dp(8), dp(2));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(54)));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        showSources();
    }

    private void showSources() {
        mode = Mode.SOURCES;
        ++requestToken;
        activeSource = null;
        activeScript = "";
        buildToolbar("Nguồn " + typeLabel, true, "＋", this::showAddSourceOptions);
        content.removeAllViews();

        LinearLayout body = verticalContainer();
        TextView hint = text("Cài nguồn " + typeLabel.toLowerCase(Locale.ROOT) + " bằng tệp JavaScript (.js) hoặc đường dẫn HTTPS. Nguồn chỉ hoạt động khi có đúng API NM7.", 13, MUTED, false);
        hint.setPadding(dp(4), dp(4), dp(4), dp(14));
        body.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        List<ReaderPluginStore.SourceInfo> sources = ReaderPluginStore.list(this, type);
        TextView count = text("Đã cài " + sources.size() + " nguồn • " + typeLabel, 13, MUTED, true);
        count.setPadding(dp(4), 0, dp(4), dp(10));
        body.addView(count, new LinearLayout.LayoutParams(-1, -2));

        if (sources.isEmpty()) {
            TextView empty = text("Chưa có nguồn " + typeLabel.toLowerCase(Locale.ROOT)
                    + ". Chọn ＋ để nhập đường dẫn .js hoặc chọn tệp .js từ thiết bị.", 15, TEXT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(26), dp(24), dp(22));
            body.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            Button add = actionButton("＋ Thêm nguồn .js", true);
            body.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
            add.setOnClickListener(v -> showAddSourceOptions());
            Button help = actionButton("Hướng dẫn tạo nguồn", false);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(44));
            hp.topMargin = dp(8);
            body.addView(help, hp);
            help.setOnClickListener(v -> showPluginHelp());
        } else {
            for (ReaderPluginStore.SourceInfo source : sources) addSourceCard(body, source);
            Button help = actionButton("Hướng dẫn định dạng .js", false);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(44));
            hp.topMargin = dp(4);
            body.addView(help, hp);
            help.setOnClickListener(v -> showPluginHelp());
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), dp(6), dp(12), dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addSourceCard(LinearLayout body, ReaderPluginStore.SourceInfo source) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(SURFACE, 13));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(9);
        body.addView(card, cp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView logo = text(type.equals("comic") ? "漫" : "文", 20, ORANGE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(SURFACE_2, 12));
        top.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(10), 0, dp(8), 0);
        top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(source.name, 16, TEXT, true), new LinearLayout.LayoutParams(-1, -2));
        TextView base = text(source.baseUrl, 11, ORANGE, false);
        base.setMaxLines(1);
        base.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(base, new LinearLayout.LayoutParams(-1, -2));
        TextView version = text("v" + source.version + (source.enabled ? "  •  Đang bật" : "  •  Đang tắt"), 11, MUTED, false);
        labels.addView(version, new LinearLayout.LayoutParams(-1, -2));
        TextView description = text(source.description.isEmpty() ? "Nguồn JavaScript NM7" : source.description, 12, MUTED, false);
        description.setPadding(dp(2), dp(8), dp(2), dp(8));
        card.addView(description, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, new LinearLayout.LayoutParams(-1, dp(42)));
        Button open = actionButton("Mở nguồn", true);
        actions.addView(open, new LinearLayout.LayoutParams(0, -1, 1f));
        Switch enabled = new Switch(this);
        enabled.setText(source.enabled ? "Bật" : "Tắt");
        enabled.setTextColor(MUTED);
        enabled.setTextSize(12);
        enabled.setChecked(source.enabled);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-2, -1);
        switchParams.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(enabled, switchParams);
        Button remove = actionButton("Gỡ", false);
        actions.addView(remove, new LinearLayout.LayoutParams(dp(62), -1));

        open.setEnabled(source.enabled);
        open.setOnClickListener(v -> openSource(source));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            try {
                ReaderPluginStore.setEnabled(this, source, checked);
                enabled.setText(checked ? "Bật" : "Tắt");
                open.setEnabled(checked);
                toast(checked ? "Đã bật nguồn" : "Đã tắt nguồn");
            } catch (Exception e) {
                enabled.setChecked(!checked);
                toast("Không cập nhật được nguồn: " + readable(e));
            }
        });
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Gỡ nguồn?")
                .setMessage(source.name + "\n" + source.baseUrl)
                .setPositiveButton("Gỡ", (d, w) -> {
                    ReaderPluginStore.remove(this, source);
                    showSources();
                    toast("Đã gỡ nguồn " + source.name);
                })
                .setNegativeButton("Hủy", null).show());
    }

    private void showAddSourceOptions() {
        new AlertDialog.Builder(this).setTitle("Thêm nguồn " + typeLabel)
                .setItems(new String[]{"Nhập URL tệp .js", "Chọn tệp .js trên thiết bị", "Hướng dẫn định dạng plugin"}, (d, which) -> {
                    if (which == 0) showUrlImportDialog();
                    else if (which == 1) chooseLocalFile();
                    else showPluginHelp();
                }).setNegativeButton("Hủy", null).show();
    }

    private void showUrlImportDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(6), dp(20), 0);
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://.../source.js");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(url, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("Cài nguồn từ URL")
                .setMessage("NM7 tải tệp .js, kiểm tra API, rồi lưu riêng trong dữ liệu ứng dụng.")
                .setView(form)
                .setPositiveButton("Cài đặt", (dialog, which) -> {
                    String address = url.getText().toString().trim();
                    if (!(address.startsWith("https://") || address.startsWith("http://"))) {
                        toast("URL phải bắt đầu bằng https:// hoặc http://");
                        return;
                    }
                    installRemote(address);
                }).setNegativeButton("Hủy", null).show();
    }

    private void chooseLocalFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/javascript", "text/javascript", "text/plain", "application/x-javascript"});
        startActivityForResult(intent, REQUEST_IMPORT_SCRIPT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_SCRIPT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("Không đọc được tệp.");
                byte[] bytes = readBounded(input, ReaderPluginRuntime.MAX_SCRIPT_BYTES);
                if (bytes.length >= ReaderPluginRuntime.MAX_SCRIPT_BYTES) throw new IllegalArgumentException("Tệp .js quá lớn (giới hạn 512 KiB).");
                String script = new String(bytes, StandardCharsets.UTF_8);
                ReaderPluginStore.SourceInfo added = ReaderPluginStore.install(this, script, type, uri.toString());
                main.post(() -> {
                    showSources();
                    toast("Đã cài nguồn " + added.name);
                });
            } catch (Exception e) {
                main.post(() -> showError("Không cài được tệp .js: " + readable(e)));
            }
        });
    }

    private void installRemote(String address) {
        int token = ++requestToken;
        showBusy("Đang tải và kiểm tra plugin…");
        io.execute(() -> {
            try {
                String script = ReaderPluginStore.download(address);
                ReaderPluginStore.SourceInfo added = ReaderPluginStore.install(this, script, type, address);
                main.post(() -> {
                    if (token != requestToken || isFinishing()) return;
                    showSources();
                    toast("Đã cài nguồn " + added.name);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (token == requestToken && !isFinishing()) showError("Không cài được nguồn: " + readable(e));
                });
            }
        });
    }

    private void openSource(ReaderPluginStore.SourceInfo source) {
        if (!source.enabled) {
            toast("Nguồn đang tắt");
            return;
        }
        try {
            activeScript = ReaderPluginStore.script(this, source);
            activeSource = source;
            searchText = "";
            showResults(true);
        } catch (Exception e) {
            showError("Không mở được nguồn: " + readable(e));
        }
    }

    private void showResults(boolean popular) {
        mode = Mode.RESULTS;
        ++requestToken;
        buildToolbar(activeSource.name, true, "⚙", this::showSources);
        content.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(6), dp(12), dp(8));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(46)));
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(TEXT);
        query.setHintTextColor(MUTED);
        query.setText(searchText);
        query.setHint("Tìm " + (type.equals("comic") ? "truyện tranh" : "truyện chữ"));
        query.setPadding(dp(12), 0, dp(8), 0);
        query.setBackground(round(SURFACE_2, 10));
        searchRow.addView(query, new LinearLayout.LayoutParams(0, -1, 1f));
        Button search = actionButton("Tìm", true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(64), -1);
        sp.setMargins(dp(6), 0, 0, 0);
        searchRow.addView(search, sp);
        Button home = actionButton("Mới", false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(60), -1);
        hp.setMargins(dp(6), 0, 0, 0);
        searchRow.addView(home, hp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = dp(8);
        page.addView(scroll, lp);
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));

        search.setOnClickListener(v -> {
            searchText = query.getText().toString().trim();
            if (searchText.isEmpty()) {
                toast("Nhập tên cần tìm");
                return;
            }
            loadResultItems("search", searchText, rows);
        });
        query.setOnEditorActionListener((v, actionId, event) -> {
            searchText = query.getText().toString().trim();
            if (!searchText.isEmpty()) loadResultItems("search", searchText, rows);
            return true;
        });
        home.setOnClickListener(v -> {
            searchText = query.getText().toString().trim();
            loadResultItems("popular", "", rows);
        });
        if (popular) loadResultItems("popular", "", rows);
        else if (!searchText.isEmpty()) loadResultItems("search", searchText, rows);
        else loadResultItems("popular", "", rows);
    }

    private void loadResultItems(String method, String query, LinearLayout rows) {
        final int token = ++requestToken;
        rows.removeAllViews();
        TextView loading = text("Đang tải danh sách…", 14, MUTED, false);
        loading.setGravity(Gravity.CENTER);
        rows.addView(loading, new LinearLayout.LayoutParams(-1, dp(100)));
        io.execute(() -> {
            try {
                JSONArray args = new JSONArray();
                if ("search".equals(method)) args.put(query);
                args.put(1);
                Object result = ReaderPluginRuntime.invoke(activeScript, method, args);
                JSONArray array = asArray(result, "items", "results", "manga", "mangas", "books", "stories", "data");
                main.post(() -> {
                    if (token != requestToken || mode != Mode.RESULTS) return;
                    rows.removeAllViews();
                    currentRows = array;
                    if (array.length() == 0) {
                        TextView empty = text("Không có kết quả. Kiểm tra selector trong file .js hoặc thử lại.", 14, MUTED, false);
                        empty.setGravity(Gravity.CENTER);
                        rows.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
                        return;
                    }
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) {
                            Object element = array.opt(i);
                            if (element instanceof String) {
                                item = new JSONObject();
                                item.put("title", String.valueOf(element));
                                item.put("url", String.valueOf(element));
                            }
                        }
                        if (item != null) addItemRow(rows, item);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (token != requestToken || mode != Mode.RESULTS) return;
                    rows.removeAllViews();
                    TextView error = text("Nguồn không tải được danh sách:\n" + readable(e) +
                            "\n\nHãy kiểm tra đường dẫn và hàm search/popular trong plugin.", 14, MUTED, false);
                    error.setPadding(dp(12), dp(16), dp(12), dp(16));
                    rows.addView(error, new LinearLayout.LayoutParams(-1, -2));
                });
            }
        });
    }

    private void addItemRow(LinearLayout rows, JSONObject item) {
        String title = first(item, "title", "name", "manga", "book", "story");
        String url = absolute(first(item, "url", "link", "href", "slug"), activeSource.baseUrl);
        String cover = absolute(first(item, "cover", "image", "thumbnail", "coverUrl", "imageUrl", "src"), activeSource.baseUrl);
        if (title.isEmpty()) title = "Không có tiêu đề";
        JSONObject stableItem = new JSONObject(item.toString());
        try {
            stableItem.put("title", title);
            stableItem.put("url", url);
            stableItem.put("cover", cover);
        } catch (Exception ignored) { }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(9), dp(6), dp(9));
        row.setClickable(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round(SURFACE_2, 8));
        image.setClipToOutline(true);
        row.addView(image, new LinearLayout.LayoutParams(dp(type.equals("comic") ? 74 : 62), dp(88)));
        if (!cover.isEmpty()) loadThumb(cover, image);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView name = text(title, 15, TEXT, true);
        name.setMaxLines(3);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
        String description = first(stableItem, "description", "summary", "author", "latest");
        if (!description.isEmpty()) {
            TextView detail = text(description, 12, MUTED, false);
            detail.setMaxLines(2);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            detail.setPadding(0, dp(4), 0, 0);
            labels.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        }
        rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(0xff292a32);
        rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        row.setOnClickListener(v -> showDetail(stableItem));
    }

    private void showDetail(JSONObject item) {
        activeItem = item;
        mode = Mode.DETAIL;
        final int token = ++requestToken;
        showBusy("Đang tải thông tin truyện…");
        io.execute(() -> {
            JSONObject details = new JSONObject(item.toString());
            JSONArray chapters = null;
            try {
                Object value = ReaderPluginRuntime.invoke(activeScript, "details", oneArg(item.optString("url")));
                JSONObject parsed = asObject(value, "item", "manga", "book", "data");
                if (parsed != null) details = merge(details, parsed);
            } catch (Exception ignored) { }
            try {
                chapters = asArray(details.opt("chapters"), "items", "results", "chapters", "data");
            } catch (Exception ignored) { }
            if (chapters == null || chapters.length() == 0) {
                try {
                    Object value = ReaderPluginRuntime.invoke(activeScript, "chapters", oneArg(item.optString("url")));
                    chapters = asArray(value, "items", "results", "chapters", "data");
                } catch (Exception ignored) { }
            }
            JSONObject finalDetails = details;
            JSONArray finalChapters = chapters == null ? new JSONArray() : chapters;
            main.post(() -> {
                if (token != requestToken || mode != Mode.DETAIL) return;
                activeDetail = finalDetails;
                activeChapters = finalChapters;
                showDetailPage(finalDetails, finalChapters);
            });
        });
    }

    private void showDetailPage(JSONObject details, JSONArray chapters) {
        mode = Mode.DETAIL;
        buildToolbar(first(details, "title", "name"), true, "‹", this::backToResults);
        content.removeAllViews();
        LinearLayout body = verticalContainer();
        body.setPadding(dp(14), dp(8), dp(14), dp(18));
        String cover = absolute(first(details, "cover", "image", "thumbnail", "coverUrl"), activeSource.baseUrl);
        if (!cover.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(round(SURFACE_2, 12));
            image.setClipToOutline(true);
            LinearLayout.LayoutParams im = new LinearLayout.LayoutParams(dp(120), dp(160));
            im.gravity = Gravity.CENTER_HORIZONTAL;
            body.addView(image, im);
            loadThumb(cover, image);
        }
        TextView title = text(first(details, "title", "name"), 20, TEXT, true);
        title.setPadding(0, dp(12), 0, dp(6));
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        String description = first(details, "description", "summary", "synopsis", "content");
        if (!description.isEmpty()) {
            TextView synopsis = text(description, 14, MUTED, false);
            synopsis.setPadding(0, dp(4), 0, dp(12));
            body.addView(synopsis, new LinearLayout.LayoutParams(-1, -2));
        }
        Button chaptersButton = actionButton(type.equals("comic") ? "Danh sách chương" : "Mục lục", true);
        body.addView(chaptersButton, new LinearLayout.LayoutParams(-1, dp(44)));
        chaptersButton.setOnClickListener(v -> showChapterList(chapters));
        TextView note = text(chapters.length() + " chương được nhận diện", 12, MUTED, false);
        note.setPadding(0, dp(9), 0, dp(7));
        body.addView(note, new LinearLayout.LayoutParams(-1, -2));
        if (chapters.length() == 0) {
            TextView empty = text("Plugin chưa trả về danh sách chương. Kiểm tra hàm chapters(url) trong tệp .js.", 13, MUTED, false);
            empty.setPadding(dp(6), dp(8), dp(6), dp(8));
            body.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
        addChapterRows(body, chapters);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showChapterList(JSONArray chapters) {
        mode = Mode.CHAPTERS;
        buildToolbar("Mục lục", true, "‹", this::backToDetail);
        content.removeAllViews();
        LinearLayout body = verticalContainer();
        body.setPadding(dp(12), dp(6), dp(12), dp(14));
        addChapterRows(body, chapters);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addChapterRows(LinearLayout body, JSONArray chapters) {
        if (chapters == null) return;
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject chapter = chapters.optJSONObject(i);
            if (chapter == null) {
                Object raw = chapters.opt(i);
                if (raw instanceof String) {
                    chapter = new JSONObject();
                    try { chapter.put("title", raw); chapter.put("url", raw); } catch (Exception ignored) { }
                }
            }
            if (chapter == null) continue;
            JSONObject stable = new JSONObject(chapter.toString());
            String title = first(stable, "title", "name", "chapter", "number");
            String url = absolute(first(stable, "url", "link", "href"), activeSource.baseUrl);
            if (title.isEmpty()) title = "Chương " + (i + 1);
            try { stable.put("title", title); stable.put("url", url); } catch (Exception ignored) { }
            TextView row = text(title, 14, TEXT, false);
            row.setPadding(dp(12), dp(13), dp(10), dp(13));
            row.setBackground(makeRipple());
            body.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View divider = new View(this);
            divider.setBackgroundColor(0xff303139);
            body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            JSONObject clicked = stable;
            row.setOnClickListener(v -> openChapter(clicked));
        }
    }

    private void openChapter(JSONObject chapter) {
        activeChapter = chapter;
        mode = Mode.READER;
        final int token = ++requestToken;
        String title = first(chapter, "title", "name");
        buildToolbar(title.isEmpty() ? "Đọc" : title, true, "‹", this::backToChapters);
        TextView font = text("A", 18, ORANGE, true);
        font.setGravity(Gravity.CENTER);
        toolbar.addView(font, new LinearLayout.LayoutParams(dp(38), -1));
        font.setOnClickListener(v -> {
            fontScale = fontScale >= 130 ? 90 : fontScale + 10;
            if (!lastReaderHtml.isEmpty()) loadReaderHtml(lastReaderHtml, activeChapter.optString("url"));
        });
        showBusy(type.equals("comic") ? "Đang tải trang truyện…" : "Đang tải nội dung chương…");
        io.execute(() -> {
            try {
                Object data = ReaderPluginRuntime.invoke(activeScript, type.equals("comic") ? "pages" : "chapter",
                        oneArg(chapter.optString("url")));
                String html;
                if ("comic".equals(type)) {
                    JSONArray pages = asArray(data, "pages", "images", "items", "data");
                    html = comicReaderHtml(first(chapter, "title", "name"), pages);
                } else {
                    String contentValue = contentString(data);
                    String safe = contentValue.trim().startsWith("<")
                            ? Jsoup.clean(contentValue, activeSource.baseUrl, Safelist.relaxed().addTags("h2", "h3", "figure", "figcaption", "blockquote", "hr"))
                            : "<p>" + escape(contentValue).replace("\n", "<br>") + "</p>";
                    html = textReaderHtml(first(chapter, "title", "name"), safe);
                }
                String finalHtml = html;
                main.post(() -> {
                    if (token != requestToken || mode != Mode.READER) return;
                    lastReaderHtml = finalHtml;
                    loadReaderHtml(finalHtml, chapter.optString("url"));
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (token != requestToken || mode != Mode.READER) return;
                    showBusy("Không đọc được chương:\n" + readable(e) + "\n\nHãy kiểm tra hàm "
                            + (type.equals("comic") ? "pages" : "chapter") + "() trong plugin.");
                });
            }
        });
    }

    private void loadReaderHtml(String html, String baseUrl) {
        content.removeAllViews();
        WebView reader = new WebView(this);
        reader.setBackgroundColor(BG);
        WebSettings settings = reader.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(fontScale);
        reader.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        reader.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private String comicReaderHtml(String title, JSONArray pages) {
        StringBuilder out = new StringBuilder(4096);
        out.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5\"><style>")
                .append("html,body{margin:0;background:#101114;color:#e9e9ed;font-family:Roboto,Arial,sans-serif}")
                .append(".page{max-width:1000px;margin:0 auto;padding:0 0 24px;text-align:center}")
                .append(".title{padding:14px 12px;font-size:17px;font-weight:600;color:#fff}")
                .append("img{display:block;width:100%;max-width:100%;height:auto;margin:0 auto 2px}")
                .append(".empty{padding:24px 16px;color:#aeb1bb;line-height:1.7}")
                .append("</style></head><body><main class=\"page\"><div class=\"title\">")
                .append(escape(title)).append("</div>");
        int count = 0;
        for (int i = 0; pages != null && i < pages.length(); i++) {
            Object value = pages.opt(i);
            String imageUrl = value instanceof JSONObject
                    ? first((JSONObject) value, "url", "src", "image", "imageUrl")
                    : String.valueOf(value == null ? "" : value);
            imageUrl = absolute(imageUrl, activeSource.baseUrl);
            if (!isWebUrl(imageUrl)) continue;
            out.append("<img src=\"").append(escape(imageUrl)).append("\" alt=\"Trang ")
                    .append(++count).append("\" loading=\"eager\">");
        }
        if (count == 0) out.append("<div class=\"empty\">Plugin chưa trả về URL ảnh trang truyện. Kiểm tra hàm pages(chapterUrl).</div>");
        out.append("</main></body></html>");
        return out.toString();
    }

    private String textReaderHtml(String title, String contentHtml) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>html,body{margin:0;background:#101114;color:#e9e9ed;font-family:Roboto,Arial,sans-serif}article{max-width:900px;margin:auto;padding:16px 18px 36px;font-size:19px;line-height:1.85}h1{font-size:25px;line-height:1.4;color:#fff}p{margin:0 0 1.1em}img{max-width:100%;height:auto;display:block;margin:16px auto}a{color:#ffad65}</style></head><body><article><h1>" +
                escape(title) + "</h1>" + contentHtml + "</article></body></html>";
    }

    private void buildToolbar(String title, boolean back, String rightLabel, Runnable rightAction) {
        toolbar.removeAllViews();
        if (back) {
            TextView backView = text("‹", 34, TEXT, false);
            backView.setGravity(Gravity.CENTER);
            toolbar.addView(backView, new LinearLayout.LayoutParams(dp(42), -1));
            backView.setOnClickListener(v -> rightAction.run());
        }
        TextView titleView = text(title == null || title.isEmpty() ? typeLabel : title, 17, TEXT, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toolbar.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1f));
        if (rightLabel != null && !rightLabel.isEmpty()) {
            TextView action = text(rightLabel, 23, ORANGE, true);
            action.setGravity(Gravity.CENTER);
            toolbar.addView(action, new LinearLayout.LayoutParams(dp(44), -1));
            action.setOnClickListener(v -> rightAction.run());
        }
    }

    private void showPluginHelp() {
        String example = "var NM7_SOURCE = {\n" +
                "  id: \"comic-site\", name: \"Tên nguồn\", type: \"comic\",\n" +
                "  baseUrl: \"https://example.com\", version: \"1.0.0\",\n" +
                "  description: \"Mô tả nguồn\",\n" +
                "  popular: function(page) { return []; },\n" +
                "  search: function(keyword, page) { return []; },\n" +
                "  details: function(url) { return {title:\"Tên truyện\", description:\"...\"}; },\n" +
                "  chapters: function(url) { return [{title:\"Chương 1\", url:\"https://example.com/chapter/1\"}]; },\n" +
                "  pages: function(chapterUrl) { return [\"https://example.com/page/1.jpg\"]; }\n" +
                "};\n\n" +
                "API có sẵn:\n" +
                "NM7.http.get(url) → tải HTML/text.\n" +
                "NM7.dom.text(html, baseUrl, selector) → lấy chữ.\n" +
                "NM7.dom.attr(html, baseUrl, selector, attribute) → lấy thuộc tính/URL tuyệt đối.\n" +
                "NM7.dom.select(html, baseUrl, selector, JSON.stringify(fields)) → chuỗi JSON; dùng JSON.parse(...) để đổi thành mảng. Mỗi field có selector và attr (text, html, href, src hoặc thuộc tính HTML).\n\n" +
                "Với Truyện tranh cần pages(chapterUrl). Với Truyện chữ cần chapter(chapterUrl) trả về HTML hoặc văn bản. Cần có search(query,page) hoặc popular(page), và details(url) hoặc chapters(url). Dùng nguồn .js chỉ khi tin cậy; plugin được giới hạn quyền truy cập Java nhưng có thể tải dữ liệu từ các URL mà nó gọi.";
        new AlertDialog.Builder(this).setTitle("API nguồn JS của NM7")
                .setMessage(example).setPositiveButton("Đóng", null).show();
    }

    private void showBusy(String message) {
        content.removeAllViews();
        TextView text = text(message, 14, MUTED, false);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(24), dp(20), dp(24), dp(20));
        content.addView(text, new FrameLayout.LayoutParams(-1, -1));
    }

    private void backToResults() { showResults(false); }
    private void backToDetail() { showDetail(activeItem); }
    private void backToChapters() {
        showChapterList(activeChapters == null ? new JSONArray() : activeChapters);
    }

    @Override public void onBackPressed() {
        if (mode == Mode.READER) backToChapters();
        else if (mode == Mode.CHAPTERS) backToDetail();
        else if (mode == Mode.DETAIL) backToResults();
        else if (mode == Mode.RESULTS) showSources();
        else super.onBackPressed();
    }

    private JSONArray oneArg(String value) {
        JSONArray args = new JSONArray();
        args.put(value == null ? "" : value);
        return args;
    }

    private JSONArray asArray(Object value, String... wrappers) {
        if (value instanceof JSONArray) return (JSONArray) value;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : wrappers) {
                JSONArray arr = object.optJSONArray(key);
                if (arr != null) return arr;
            }
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.startsWith("[") || raw.startsWith("{")) {
                try {
                    Object parsed = new org.json.JSONTokener(raw).nextValue();
                    if (parsed instanceof JSONArray) return (JSONArray) parsed;
                    if (parsed instanceof JSONObject) return asArray(parsed, wrappers);
                } catch (Exception ignored) { }
            }
        }
        return new JSONArray();
    }

    private JSONObject asObject(Object value, String... wrappers) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : wrappers) {
                JSONObject wrapped = object.optJSONObject(key);
                if (wrapped != null) return wrapped;
            }
            return object;
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.startsWith("{")) {
                try { return asObject(new org.json.JSONTokener(raw).nextValue(), wrappers); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private JSONObject merge(JSONObject base, JSONObject extra) throws Exception {
        JSONObject merged = new JSONObject(base.toString());
        java.util.Iterator<String> keys = extra.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (extra.opt(key) != JSONObject.NULL) merged.put(key, extra.opt(key));
        }
        return merged;
    }

    private String contentString(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String text = first(object, "content", "html", "text", "body", "chapter");
            if (!text.isEmpty()) return text;
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private String absolute(String raw, String base) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String value = raw.trim();
        if (value.startsWith("//")) return "https:" + value;
        if (isWebUrl(value)) return value;
        try { return new URL(new URL(base), value).toString(); } catch (Exception ignored) { return ""; }
    }

    private boolean isWebUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private void loadThumb(String url, ImageView target) {
        target.setTag(url);
        io.execute(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(9000);
                conn.setRequestProperty("User-Agent", "NM7-IPTV-Mobile/1.10.145");
                try (InputStream input = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) main.post(() -> {
                        if (url.equals(target.getTag())) target.setImageBitmap(bitmap);
                    });
                } finally { conn.disconnect(); }
            } catch (Exception ignored) { }
        });
    }

    private byte[] readBounded(InputStream input, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) >= 0) {
            if (out.size() + n > max) throw new IllegalArgumentException("Tệp vượt giới hạn 512 KiB.");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private LinearLayout verticalContainer() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setBackground(round(primary ? 0xffff7a00 : SURFACE_2, 10));
        return button;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private android.graphics.drawable.Drawable makeRipple() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        try { return getDrawable(value.resourceId); } catch (Exception ignored) { return round(SURFACE, 8); }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void showError(String message) { new AlertDialog.Builder(this).setTitle("Nguồn JS").setMessage(message).setPositiveButton("Đóng", null).show(); }
    private String readable(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && (cause instanceof RuntimeException || cause.getClass().getSimpleName().contains("WrappedSource"))) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
