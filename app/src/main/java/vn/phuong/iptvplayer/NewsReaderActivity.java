package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NM7 Mobile built-in news reader.
 *
 * The sample reader's observable flow is implemented without its remote encrypted plugin
 * catalog: native RSS parsing supplies a stable feed model, source management persists in
 * SharedPreferences, and the publisher's own web page is used for the full article.
 */
public final class NewsReaderActivity extends Activity {
    private static final int BG = Color.rgb(18, 19, 24);
    private static final int SURFACE = Color.rgb(29, 30, 37);
    private static final int SURFACE_2 = Color.rgb(37, 39, 47);
    private static final int TEXT = Color.rgb(242, 243, 247);
    private static final int MUTED = Color.rgb(157, 160, 171);
    private static final int ORANGE = Color.rgb(255, 122, 0);
    private static final String PREFS = "nm7_news_reader_v1";
    private static final String KEY_INSTALLED = "installed_sources";
    private static final String KEY_ENABLED = "enabled_sources";
    private static final String KEY_SOURCE = "active_source";
    private static final String KEY_CUSTOM_RSS = "custom_rss_news_sources";
    private static final String KEY_PLUGIN_CATEGORY_PREFIX = "news_plugin_categories_";
    private static final int REQUEST_NEWS_PLUGIN_FILE = 7501;
    private static final int MAX_PLUGIN_FILE_BYTES = 512 * 1024;
    private static final Pattern IMG_SRC = Pattern.compile("(?is)<img[^>]+(?:src|data-src)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern TAGS = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final List<Article> articles = new ArrayList<>();
    // Keep feed results per source/category for the lifetime of this Activity so swiping
    // back to a visited category is instant and does not trigger another network load.
    private final Map<String, List<Article>> feedCache = new HashMap<>();
    private final Map<String, Integer> feedScrollPositions = new HashMap<>();
    private final Set<String> pluginCategoriesLoading = new HashSet<>();
    private LinearLayout root;
    private LinearLayout toolbar;
    private HorizontalScrollView categoryScroller;
    private LinearLayout categoryRow;
    private FrameLayout content;
    private LinearLayout articleRows;
    private FeedScrollView feedScrollView;
    private TextView status;
    private WebView articleWebView;
    private Mode mode = Mode.FEED;
    private String sourceId = "vnexpress";
    private String categoryName = "Trang chủ";
    private String currentArticleUrl;
    private String currentArticleTitle;
    private String currentArticleSummary = "";
    private String currentArticleImageUrl = "";
    private String currentArticleDate = "";
    private String currentArticleSource = "";
    private String searchQuery = "";
    private String managerKind = "Báo";
    private int textScale = 100;
    private int requestToken = 0;

    private enum Mode { FEED, ARTICLE, MANAGER }

    private static final Source VNEXPRESS = makeSource("vnexpress", "VnExpress",
            "Báo điện tử hàng đầu Việt Nam", "vnexpress.net",
            cats(
                    "Trang chủ", "https://vnexpress.net/rss/tin-moi-nhat.rss",
                    "Thế giới", "https://vnexpress.net/rss/the-gioi.rss",
                    "Thời sự", "https://vnexpress.net/rss/thoi-su.rss",
                    "Kinh doanh", "https://vnexpress.net/rss/kinh-doanh.rss",
                    "Giải trí", "https://vnexpress.net/rss/giai-tri.rss",
                    "Thể thao", "https://vnexpress.net/rss/the-thao.rss",
                    "Pháp luật", "https://vnexpress.net/rss/phap-luat.rss",
                    "Giáo dục", "https://vnexpress.net/rss/giao-duc.rss",
                    "Góc nhìn", "https://vnexpress.net/rss/goc-nhin.rss",
                    "Bất động sản", "https://vnexpress.net/rss/bat-dong-san.rss",
                    "Sức khỏe", "https://vnexpress.net/rss/suc-khoe.rss",
                    "Đời sống", "https://vnexpress.net/rss/doi-song.rss",
                    "Du lịch", "https://vnexpress.net/rss/du-lich.rss",
                    "Khoa học công nghệ", "https://vnexpress.net/rss/khoa-hoc-cong-nghe.rss",
                    "Ô tô xe máy", "https://vnexpress.net/rss/oto-xe-may.rss",
                    "Ý kiến", "https://vnexpress.net/rss/y-kien.rss",
                    "Tâm sự", "https://vnexpress.net/rss/tam-su.rss",
                    "Thư giãn", "https://vnexpress.net/rss/thu-gian.rss",
                    "Spotlight", "https://vnexpress.net/rss/spotlight.rss",
                    "Tin xem nhiều", "https://vnexpress.net/rss/tin-xem-nhieu.rss"));

    private static final List<Source> CATALOG = Collections.unmodifiableList(Arrays.asList(
            makeSource("kenh14", "Kênh14", "Giải trí, đời sống và giới trẻ", "kenh14.vn",
                    cats("Trang chủ", "https://kenh14.vn/rss/home.rss",
                            "Star", "https://kenh14.vn/rss/star.rss",
                            "Sao Việt", "https://kenh14.vn/rss/star/sao-viet.rss",
                            "Đời sống", "https://kenh14.vn/rss/doisong.rss")),
            makeSource("thethao247", "Thể Thao 247", "Tin thể thao và bóng đá", "thethao247.vn",
                    cats("Trang chủ", "https://thethao247.vn/trang-chu.rss",
                            "Thể thao", "https://thethao247.vn/the-thao-c5.rss",
                            "Bóng đá", "https://thethao247.vn/bong-da.rss")),
            makeSource("dantri", "Dân Trí", "Tin tức Việt Nam và quốc tế", "dantri.com.vn",
                    cats("Trang chủ", "https://dantri.com.vn/rss/home.rss",
                            "Thời sự", "https://dantri.com.vn/rss/thoi-su.rss",
                            "Thế giới", "https://dantri.com.vn/rss/the-gioi.rss",
                            "Kinh doanh", "https://dantri.com.vn/rss/kinh-doanh.rss",
                            "Thể thao", "https://dantri.com.vn/rss/the-thao.rss",
                            "Công nghệ", "https://dantri.com.vn/rss/suc-manh-so.rss")),
            makeSource("genk", "GenK", "Công nghệ, Internet và thiết bị số", "genk.vn",
                    cats("Trang chủ", "https://genk.vn/rss/home.rss",
                            "Mobile", "https://genk.vn/rss/mobile.rss",
                            "Tin ICT", "https://genk.vn/rss/tin-ict.rss",
                            "Internet", "https://genk.vn/rss/internet.rss",
                            "AI", "https://genk.vn/ai.rss")),
            makeSource("vnreview", "VnReview", "Đánh giá và tin công nghệ", "vnreview.vn",
                    cats("Tin mới nhất", "https://vnreview.vn/rss/tin-moi-nhat.rss",
                            "Công nghệ", "https://vnreview.vn/rss/cong-nghe.rss")),
            makeSource("24h", "24h", "Tin tức, đời sống và thể thao", "24h.com.vn",
                    cats("Trang chủ", "https://www.24h.com.vn/upload/rss/trangchu24h.rss",
                            "Tin tức", "https://www.24h.com.vn/upload/rss/tintuctrongngay.rss",
                            "Bóng đá", "https://www.24h.com.vn/upload/rss/bongda.rss",
                            "An ninh - Hình sự", "https://www.24h.com.vn/upload/rss/anninhhinhsu.rss",
                            "Thời trang", "https://www.24h.com.vn/upload/rss/thoitrang.rss",
                            "Thời trang Hi-tech", "https://www.24h.com.vn/upload/rss/thoitranghitech.rss",
                            "Tài chính - Bất động sản", "https://www.24h.com.vn/upload/rss/taichinhbatdongsan.rss",
                            "Ẩm thực", "https://www.24h.com.vn/upload/rss/amthuc.rss",
                            "Làm đẹp", "https://www.24h.com.vn/upload/rss/lamdep.rss",
                            "Phim", "https://www.24h.com.vn/upload/rss/phim.rss",
                            "Giáo dục - du học", "https://www.24h.com.vn/upload/rss/giaoducduhoc.rss",
                            "Bạn trẻ - Cuộc sống", "https://www.24h.com.vn/upload/rss/bantrecuocsong.rss",
                            "Ca nhạc - MTV", "https://www.24h.com.vn/upload/rss/canhacmtv.rss",
                            "Thể thao", "https://www.24h.com.vn/upload/rss/thethao.rss",
                            "Phi thường - kỳ quặc", "https://www.24h.com.vn/upload/rss/phithuongkyquac.rss",
                            "Công nghệ", "https://www.24h.com.vn/upload/rss/congnghethongtin.rss",
                            "Ô tô", "https://www.24h.com.vn/upload/rss/oto.rss",
                            "Thị trường - Tiêu dùng", "https://www.24h.com.vn/upload/rss/thitruongtieudung.rss",
                            "Du lịch", "https://www.24h.com.vn/upload/rss/dulich.rss",
                            "Sức khỏe đời sống", "https://www.24h.com.vn/upload/rss/suckhoedoisong.rss",
                            "Cười 24h", "https://www.24h.com.vn/upload/rss/cuoi24h.rss")),
            makeSource("baomoi", "Báo Mới", "Tổng hợp tin từ nhiều báo", "baomoi.com",
                    cats("Trang chủ", "https://baomoi.com/rss/home.rss")),
            makeSource("tuoitre", "Tuổi Trẻ", "Tin tức trong nước và quốc tế", "tuoitre.vn",
                    cats("Trang chủ", "https://tuoitre.vn/rss/tin-moi-nhat.rss",
                            "Thời sự", "https://tuoitre.vn/rss/thoi-su.rss",
                            "Thế giới", "https://tuoitre.vn/rss/the-gioi.rss",
                            "Công nghệ", "https://tuoitre.vn/rss/cong-nghe.rss")),
            makeSource("thanhnien", "Thanh Niên", "Tin nhanh và bình luận", "thanhnien.vn",
                    cats("Trang chủ", "https://thanhnien.vn/rss/home.rss",
                            "Thời sự", "https://thanhnien.vn/rss/thoi-su.rss",
                            "Công nghệ", "https://thanhnien.vn/rss/cong-nghe.rss")),
            makeSource("tinmoi", "Tin Mới", "Tin tức, giải trí và đời sống", "tinmoi.vn",
                    cats("Trang chủ", "https://tinmoi.vn/rss/trang-chu.rss",
                            "Giải trí", "https://tinmoi.vn/rss/giai-tri.rss",
                            "Thể thao", "https://tinmoi.vn/rss/the-thao.rss",
                            "Công nghệ", "https://tinmoi.vn/rss/cong-nghe.rss")),
            makeSource("vtv", "VTV", "Tin tức từ Đài Truyền hình Việt Nam", "vtv.vn",
                    cats("Trang chủ", "https://vtv.vn/rss/trang-chu.rss",
                            "Thời sự", "https://vtv.vn/rss/thoi-su.rss"))
    ));


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Đọc báo");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        sourceId = getPreferencesStore().getString(KEY_SOURCE, "vnexpress");
        textScale = Math.max(50, Math.min(160, getPreferencesStore().getInt("reader_text_scale", 100)));
        buildShell();
        renderFeed(true);
        HomeTabBar.attachReader(this);
    }

    private SharedPreferences getPreferencesStore() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        final int readerBarHeight = dp(64);
        root.setPadding(0, 0, 0, readerBarHeight);
        setContentView(root);
        // Match the YouTube activity's system-bar spacing. Target SDK 36 draws edge-to-edge,
        // so explicitly reserve status/navigation insets in this activity's own content.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                topInset = insets.getInsets(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.displayCutout()).top;
                bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(view.getPaddingLeft(), topInset, view.getPaddingRight(),
                    readerBarHeight + bottomInset);
            return insets;
        });
        root.requestApplyInsets();

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(14), dp(4), dp(10), dp(4));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        categoryScroller = new HorizontalScrollView(this);
        categoryScroller.setHorizontalScrollBarEnabled(false);
        categoryScroller.setFillViewport(false);
        categoryRow = new LinearLayout(this);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setPadding(dp(8), dp(4), dp(8), dp(5));
        categoryScroller.addView(categoryRow, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(categoryScroller, new LinearLayout.LayoutParams(-1, dp(48)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void renderFeed(boolean refresh) {
        mode = Mode.FEED;
        closeArticleWebView();
        categoryScroller.setVisibility(View.VISIBLE);
        buildToolbar();
        buildCategories();
        showArticleList();
        if (refresh || articles.isEmpty()) loadFeed(refresh);
        else {
            renderArticleRows();
            restoreFeedScrollPosition();
        }
    }

    private void buildToolbar() {
        toolbar.removeAllViews();
        Source source = getSource(sourceId);
        TextView sourceButton = text(source.name, 19, TEXT, true);
        sourceButton.setSingleLine(true);
        sourceButton.setEllipsize(TextUtils.TruncateAt.END);
        sourceButton.setCompoundDrawablePadding(dp(4));
        sourceButton.setOnClickListener(v -> showSourcePicker());
        toolbar.addView(sourceButton, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView search = text("⌕", 29, TEXT, false);
        search.setGravity(Gravity.CENTER);
        search.setContentDescription("Tìm trong danh sách tin");
        toolbar.addView(search, new LinearLayout.LayoutParams(dp(42), -1));
        search.setOnClickListener(v -> showSearchDialog());

        TextView font = text("A", 20, TEXT, true);
        font.setGravity(Gravity.CENTER);
        toolbar.addView(font, new LinearLayout.LayoutParams(dp(38), -1));
        font.setContentDescription("Chọn cỡ chữ");
        font.setOnClickListener(v -> showTextScalePicker());

        TextView manage = text("▤", 25, ORANGE, true);
        manage.setGravity(Gravity.CENTER);
        manage.setContentDescription("Quản lý nguồn đọc");
        toolbar.addView(manage, new LinearLayout.LayoutParams(dp(42), -1));
        manage.setOnClickListener(v -> renderManager());
        TextView refresh = text("↻", 25, ORANGE, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("Làm mới tin");
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(34), -1));
        refresh.setOnClickListener(v -> loadFeed(true));
    }

    private void showTextScalePicker() {
        final int minScale = 50;
        final int maxScale = 160;
        final int step = 10;
        final int count = (maxScale - minScale) / step + 1;
        final int[] scales = new int[count];
        final String[] labels = new String[count];
        int selected = 5;
        for (int i = 0; i < count; i++) {
            scales[i] = minScale + i * step;
            labels[i] = scales[i] + "%";
            if (scales[i] == textScale) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cỡ chữ")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    textScale = scales[which];
                    getPreferencesStore().edit().putInt("reader_text_scale", textScale).apply();
                    if (articleWebView != null) articleWebView.getSettings().setTextZoom(textScale);
                    if (mode == Mode.FEED) renderArticleRows();
                    toast("Cỡ chữ " + textScale + "%");
                    dialog.dismiss();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void buildCategories() {
        categoryRow.removeAllViews();
        Source source = getSource(sourceId);
        if (!source.categories.containsKey(categoryName)) categoryName = source.categories.keySet().iterator().next();
        ensurePluginCategories(source);
        for (String name : source.categories.keySet()) {
            TextView tab = text(name, 14, name.equals(categoryName) ? ORANGE : MUTED, name.equals(categoryName));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(13), 0, dp(13), 0);
            tab.setSingleLine(true);
            categoryRow.addView(tab, new LinearLayout.LayoutParams(-2, -1));
            tab.setOnClickListener(v -> {
                rememberFeedScrollPosition();
                categoryName = name;
                searchQuery = "";
                buildCategories();
                loadFeed();
            });
        }
        categoryScroller.post(() -> {
            for (int i = 0; i < categoryRow.getChildCount(); i++) {
                View child = categoryRow.getChildAt(i);
                if (child instanceof TextView && categoryName.equals(((TextView) child).getText().toString())) {
                    int target = child.getLeft() - Math.max(0, (categoryScroller.getWidth() - child.getWidth()) / 2);
                    categoryScroller.smoothScrollTo(Math.max(0, target), 0);
                    break;
                }
            }
        });
    }

    private void stepCategoryBySwipe(int direction) {
        rememberFeedScrollPosition();
        Source source = getSource(sourceId);
        List<String> names = new ArrayList<>(source.categories.keySet());
        if (names.size() < 2) return;
        int current = names.indexOf(categoryName);
        if (current < 0) current = 0;
        int next = (current + direction + names.size()) % names.size();
        if (next == current) return;
        categoryName = names.get(next);
        searchQuery = "";
        buildCategories();
        loadFeed();
    }

    private final class FeedScrollView extends ScrollView {
        private float downX;
        private float downY;
        private boolean horizontalSwipe;
        private boolean categorySwipeDispatched;
        private int swipeDirection;

        FeedScrollView(Context context) { super(context); }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    horizontalSwipe = false;
                    categorySwipeDispatched = false;
                    swipeDirection = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) >= dp(28) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        horizontalSwipe = true;
                        swipeDirection = dx < 0 ? 1 : -1;
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (horizontalSwipe) return true;
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            final int action = event.getActionMasked();
            if (horizontalSwipe) {
                if (action == MotionEvent.ACTION_UP) {
                    if (!categorySwipeDispatched && swipeDirection != 0) {
                        categorySwipeDispatched = true;
                        final int move = swipeDirection;
                        post(() -> stepCategoryBySwipe(move));
                    }
                    horizontalSwipe = false;
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    horizontalSwipe = false;
                    return true;
                }
                return true;
            }
            // Do not consume UP/CANCEL for vertical gestures: ScrollView needs them to
            // calculate velocity and perform native fling scrolling.
            return super.onTouchEvent(event);
        }
    }

    private void showArticleList() {
        content.removeAllViews();
        FeedScrollView scroll = new FeedScrollView(this);
        feedScrollView = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(2), 0, dp(8));
        articleRows = new LinearLayout(this);
        articleRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(articleRows, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        status = text("Đang tải tin…", 14, MUTED, false);
        status.setGravity(Gravity.CENTER);
        articleRows.addView(status, new LinearLayout.LayoutParams(-1, dp(100)));
    }

    private String feedCacheKey(String source, String feedUrl) {
        return source + "\n" + feedUrl;
    }

    private String feedScrollKey() {
        return sourceId + "\n" + categoryName;
    }

    private void rememberFeedScrollPosition() {
        if (feedScrollView != null) {
            feedScrollPositions.put(feedScrollKey(), Math.max(0, feedScrollView.getScrollY()));
        }
    }

    private void restoreFeedScrollPosition() {
        final FeedScrollView scroll = feedScrollView;
        if (scroll == null) return;
        Integer saved = feedScrollPositions.get(feedScrollKey());
        final int targetY = saved == null ? 0 : Math.max(0, saved);
        scroll.post(() -> scroll.scrollTo(0, targetY));
    }

    private void loadFeed() {
        loadFeed(false);
    }

    private void loadFeed(boolean forceRefresh) {
        if (mode != Mode.FEED) return;
        Source source = getSource(sourceId);
        if (source.categories.isEmpty()) {
            sourceId = VNEXPRESS.id;
            source = VNEXPRESS;
            getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
        }
        String feed = source.categories.get(categoryName);
        if (feed == null) {
            categoryName = source.categories.keySet().iterator().next();
            feed = source.categories.get(categoryName);
        }
        final String feedUrl = feed;
        final String selectedCategory = categoryName;
        final Source selectedSource = source;
        final String cacheKey = feedCacheKey(source.id, feedUrl);
        final int token = ++requestToken;
        if (!forceRefresh) {
            List<Article> cached = feedCache.get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                articles.clear();
                articles.addAll(cached);
                renderArticleRows();
                restoreFeedScrollPosition();
                return;
            }
        }
        showLoading("Đang tải tin từ " + source.name + "…");
        io.execute(() -> {
            List<Article> loaded = Collections.emptyList();
            String error = null;
            try {
                loaded = loadArticlesWithFallback(selectedSource, feedUrl, selectedCategory);
                if (loaded.isEmpty()) error = "Nguồn chưa trả về bài nào. Hãy thử chuyên mục khác hoặc thêm nguồn RSS/JS hoạt động.";
            } catch (Exception e) {
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                error = "Không tải được danh sách tin từ RSS/trang nguồn. " + detail;
            }
            final List<Article> result = loaded;
            final String message = error;
            main.post(() -> {
                // Cache completed responses even if the user has already swiped to a
                // different category while this request was in flight.
                if (message == null && !result.isEmpty()) {
                    feedCache.put(cacheKey, new ArrayList<>(result));
                }
                if (token != requestToken || mode != Mode.FEED) return;
                articles.clear();
                articles.addAll(result);
                if (message != null && articles.isEmpty()) {
                    showLoading(message + "\n\nBạn vẫn ở trong NM7; ứng dụng sẽ không tự mở trang chủ bên ngoài.");
                } else {
                    renderArticleRows();
                    restoreFeedScrollPosition();
                }
            });
        });
    }

    private void showLoading(String message) {
        if (articleRows == null) return;
        articleRows.removeAllViews();
        status = text(message, 14, MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), dp(20), dp(24), dp(20));
        articleRows.addView(status, new LinearLayout.LayoutParams(-1, dp(122)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(12), dp(4), dp(12), dp(12));
        articleRows.addView(actions, new LinearLayout.LayoutParams(-1, dp(54)));
        Button retry = actionButton("Thử lại", true);
        Button manage = actionButton("Nguồn báo", false);
        actions.addView(retry, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(0, -1, 1f);
        manageParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(manage, manageParams);
        retry.setOnClickListener(v -> loadFeed(true));
        manage.setOnClickListener(v -> {
            managerKind = "Báo";
            renderManager();
        });
    }

    private void renderArticleRows() {
        if (articleRows == null || mode != Mode.FEED) return;
        articleRows.removeAllViews();
        List<Article> visible = new ArrayList<>();
        for (Article a : articles) {
            if (searchQuery.isEmpty() || a.title.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))
                    || a.summary.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))) {
                visible.add(a);
            }
        }
        if (visible.isEmpty()) {
            status = text(searchQuery.isEmpty() ? "Chưa có tin để hiển thị." : "Không tìm thấy tin phù hợp.",
                    14, MUTED, false);
            status.setGravity(Gravity.CENTER);
            articleRows.addView(status, new LinearLayout.LayoutParams(-1, dp(120)));
            return;
        }
        for (Article a : visible) addArticleRow(a);
    }

    private void addArticleRow(Article article) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(makeRound(SURFACE, 0));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(makeRound(SURFACE_2, 14));
        image.setClipToOutline(true);
        int imageW = dp(104), imageH = dp(86);
        row.addView(image, new LinearLayout.LayoutParams(imageW, imageH));
        if (!TextUtils.isEmpty(article.imageUrl)) loadThumbnail(article.imageUrl, image);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        textColumn.setPadding(dp(13), 0, 0, 0);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = text(article.title, 16f * textScale / 100f, TEXT, false);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = text(getSource(sourceId).name + (TextUtils.isEmpty(article.date) ? "" : "  •  " + article.date),
                12, MUTED, false);
        meta.setPadding(0, dp(6), 0, 0);
        textColumn.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        row.setOnClickListener(v -> {
            rememberFeedScrollPosition();
            currentArticleTitle = article.title;
            currentArticleSummary = article.summary;
            currentArticleImageUrl = article.imageUrl;
            currentArticleDate = article.date;
            currentArticleSource = getSource(sourceId).name;
            openArticlePage(article.url);
        });
        articleRows.addView(row, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(0xff292a32);
        articleRows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void loadThumbnail(String url, ImageView target) {
        target.setTag(url);
        io.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(9000);
                conn.setRequestProperty("User-Agent", userAgent());
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) main.post(() -> {
                        if (url.equals(target.getTag())) target.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void openArticlePage(String url) {
        if (TextUtils.isEmpty(url) || !(url.startsWith("https://") || url.startsWith("http://"))) {
            toast("Liên kết bài viết không hợp lệ");
            return;
        }
        mode = Mode.ARTICLE;
        currentArticleUrl = url;
        closeArticleWebView();
        categoryScroller.setVisibility(View.GONE);
        toolbar.removeAllViews();

        TextView back = text("‹", 34, TEXT, false);
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(40), -1));
        back.setOnClickListener(v -> renderFeed(false));

        TextView title = text(currentArticleTitle == null || currentArticleTitle.trim().isEmpty()
                ? getSource(sourceId).name : currentArticleTitle, 15, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView font = text("Aᴀ", 18, ORANGE, true);
        font.setGravity(Gravity.CENTER);
        font.setContentDescription("Đổi cỡ chữ bài viết");
        toolbar.addView(font, new LinearLayout.LayoutParams(dp(38), -1));
        font.setOnClickListener(v -> showTextScalePicker());

        TextView original = text("↗", 22, ORANGE, true);
        original.setGravity(Gravity.CENTER);
        original.setContentDescription("Mở bài viết gốc");
        toolbar.addView(original, new LinearLayout.LayoutParams(dp(34), -1));
        original.setOnClickListener(v -> openOriginalPage(currentArticleUrl));

        TextView share = text("⋯", 24, ORANGE, true);
        share.setGravity(Gravity.CENTER);
        share.setContentDescription("Chia sẻ bài viết");
        toolbar.addView(share, new LinearLayout.LayoutParams(dp(34), -1));
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, currentArticleUrl);
            startActivity(Intent.createChooser(send, "Chia sẻ bài viết"));
        });

        content.removeAllViews();
        TextView loading = text("Đang chuẩn bị chế độ đọc…", 14, MUTED, false);
        loading.setGravity(Gravity.CENTER);
        content.addView(loading, new FrameLayout.LayoutParams(-1, -1));

        final int token = ++requestToken;
        final String articleTitle = currentArticleTitle == null ? "" : currentArticleTitle;
        final String articleSummary = currentArticleSummary == null ? "" : currentArticleSummary;
        final String articleImage = currentArticleImageUrl == null ? "" : currentArticleImageUrl;
        final String articleDate = currentArticleDate == null ? "" : currentArticleDate;
        final String sourceName = currentArticleSource == null || currentArticleSource.isEmpty()
                ? getSource(sourceId).name : currentArticleSource;
        final int scale = textScale;
        final List<ReaderArticleExtractor.RelatedArticle> related = collectRelatedArticles(url);
        io.execute(() -> {
            String html;
            try {
                html = ReaderArticleExtractor.fetchAndRender(url, articleTitle, articleSummary,
                        articleImage, sourceName, articleDate, related, scale);
            } catch (Exception error) {
                // Never fall back to the publisher's full page: retain a legible article shell.
                html = ReaderArticleExtractor.fallback(url, articleTitle, articleSummary,
                        articleImage, sourceName, articleDate, related, scale,
                        "Không tải được nội dung đầy đủ từ nguồn báo. NM7 vẫn giữ chế độ đọc; hãy thử lại hoặc mở bài gốc bằng nút ↗.");
            }
            String rendered = html;
            main.post(() -> {
                if (token != requestToken || mode != Mode.ARTICLE || isFinishing()) return;
                renderArticleDocument(rendered, url);
            });
        });
    }

    private void renderArticleDocument(String html, String baseUrl) {
        content.removeAllViews();
        articleWebView = new WebView(this);
        articleWebView.setBackgroundColor(BG);
        articleWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = articleWebView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(textScale);
        articleWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String newUrl) {
                return handleArticleWebViewNavigation(newUrl);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request == null || request.getUrl() == null
                        || handleArticleWebViewNavigation(request.getUrl().toString());
            }
        });
        content.addView(articleWebView, new FrameLayout.LayoutParams(-1, -1));
        articleWebView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    private List<ReaderArticleExtractor.RelatedArticle> collectRelatedArticles(String currentUrl) {
        List<ReaderArticleExtractor.RelatedArticle> result = new ArrayList<>();
        String current = canonicalArticleUrl(currentUrl);
        for (Article article : articles) {
            if (article == null || !isWebUrl(article.url)
                    || canonicalArticleUrl(article.url).equals(current)) continue;
            result.add(new ReaderArticleExtractor.RelatedArticle(article.title, article.url,
                    article.summary, article.imageUrl, article.source.isEmpty() ? getSource(sourceId).name : article.source));
            if (result.size() >= 8) break;
        }
        return result;
    }

    private boolean handleArticleWebViewNavigation(String target) {
        if (TextUtils.isEmpty(target)) return true;
        Uri uri = Uri.parse(target);
        if ("nm7".equalsIgnoreCase(uri.getScheme()) && "related".equalsIgnoreCase(uri.getHost())) {
            int index = -1;
            try { index = Integer.parseInt(uri.getLastPathSegment()); } catch (Exception ignored) { }
            List<ReaderArticleExtractor.RelatedArticle> related = collectRelatedArticles(currentArticleUrl);
            if (index >= 0 && index < related.size()) {
                ReaderArticleExtractor.RelatedArticle item = related.get(index);
                for (Article article : articles) {
                    if (canonicalArticleUrl(article.url).equals(canonicalArticleUrl(item.url))) {
                        currentArticleTitle = article.title;
                        currentArticleSummary = article.summary;
                        currentArticleImageUrl = article.imageUrl;
                        currentArticleDate = article.date;
                        currentArticleSource = article.source.isEmpty() ? getSource(sourceId).name : article.source;
                        openArticlePage(article.url);
                        return true;
                    }
                }
                currentArticleTitle = item.title;
                currentArticleSummary = item.summary;
                currentArticleImageUrl = item.imageUrl;
                currentArticleSource = item.source;
                currentArticleDate = "";
                openArticlePage(item.url);
            }
            return true;
        }
        openOriginalPage(target);
        return true;
    }

    private void openOriginalPage(String url) {
        if (TextUtils.isEmpty(url) || !(url.startsWith("https://") || url.startsWith("http://"))) {
            toast("Liên kết không hợp lệ");
            return;
        }
        try {
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(open);
        } catch (Exception e) {
            toast("Không mở được trình duyệt");
        }
    }

    private void closeArticleWebView() {
        if (articleWebView != null) {
            try {
                articleWebView.stopLoading();
                articleWebView.loadUrl("about:blank");
                articleWebView.clearHistory();
                articleWebView.removeAllViews();
                articleWebView.destroy();
            } catch (RuntimeException ignored) { }
            articleWebView = null;
        }
    }

    private void showSourcePicker() {
        List<Source> choices = getEnabledSources();
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).name;
        new AlertDialog.Builder(this).setTitle("Chọn nguồn đọc")
                .setItems(labels, (dialog, which) -> {
                    rememberFeedScrollPosition();
                    sourceId = choices.get(which).id;
                    categoryName = choices.get(which).categories.keySet().iterator().next();
                    searchQuery = "";
                    getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
                    renderFeed(true);
                }).setNegativeButton("Đóng", null).show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Nhập từ khóa tiêu đề");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setText(searchQuery);
        new AlertDialog.Builder(this).setTitle("Tìm tin đang tải")
                .setView(input)
                .setPositiveButton("Tìm", (dialog, which) -> {
                    searchQuery = input.getText().toString().trim();
                    renderArticleRows();
                })
                .setNeutralButton("Xóa", (dialog, which) -> {
                    searchQuery = "";
                    renderArticleRows();
                })
                .setNegativeButton("Hủy", null).show();
    }

    private void renderManager() {
        rememberFeedScrollPosition();
        mode = Mode.MANAGER;
        ++requestToken;
        closeArticleWebView();
        categoryScroller.setVisibility(View.GONE);
        toolbar.removeAllViews();

        TextView title = text("Quản lý nguồn đọc", 18, TEXT, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView refresh = text("↻", 25, ORANGE, true);
        refresh.setGravity(Gravity.CENTER);
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(38), -1));
        refresh.setOnClickListener(v -> renderManager());
        TextView close = text("×", 29, ORANGE, false);
        close.setGravity(Gravity.CENTER);
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(38), -1));
        close.setOnClickListener(v -> renderFeed(true));

        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(16));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        Set<String> installed = installedIds();
        Set<String> enabled = enabledIds();
        int totalInstalled = installed.size() + getCustomRssSources().size() + ReaderPluginStore.list(this, "news").size() + 1;
        TextView count = text("Có " + totalInstalled + " nguồn báo đã cài; nguồn RSS có thể dùng RSS hoặc trang danh sách tin",
                13, MUTED, false);
        count.setPadding(dp(3), dp(4), 0, dp(12));
        body.addView(count, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        kindRow.setGravity(Gravity.CENTER);
        body.addView(kindRow, new LinearLayout.LayoutParams(-1, dp(42)));
        for (String kind : new String[]{"Báo", "Truyện chữ", "Truyện tranh"}) {
            TextView tab = text(kind, 14, kind.equals(managerKind) ? Color.WHITE : MUTED, kind.equals(managerKind));
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(makeRound(kind.equals(managerKind) ? ORANGE : SURFACE_2, 10));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1f);
            tp.setMargins(dp(2), 0, dp(2), 0);
            kindRow.addView(tab, tp);
            tab.setOnClickListener(v -> {
                if ("Truyện chữ".equals(kind) || "Truyện tranh".equals(kind)) {
                    Intent plugin = new Intent(NewsReaderActivity.this, ReaderPluginActivity.class);
                    plugin.putExtra(ReaderPluginActivity.EXTRA_TYPE,
                            "Truyện chữ".equals(kind) ? "novel" : "comic");
                    startActivity(plugin);
                    return;
                }
                managerKind = kind;
                renderManager();
            });
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, dp(10));
        body.addView(actions, new LinearLayout.LayoutParams(-1, dp(52)));
        Button installAll = actionButton("Cài đặt tất cả", true);
        Button removeAll = actionButton("Xóa tất cả", false);
        actions.addView(installAll, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(0, -1, 1f);
        removeLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(removeAll, removeLp);
        installAll.setOnClickListener(v -> {
            if (!"Báo".equals(managerKind)) {
                toast("Danh mục nguồn này chưa được cung cấp.");
                return;
            }
            Set<String> all = new HashSet<>(installedIds());
            for (Source s : CATALOG) all.add(s.id);
            saveSet(KEY_INSTALLED, all);
            saveSet(KEY_ENABLED, all);
            renderManager();
        });
        removeAll.setOnClickListener(v -> {
            if (!"Báo".equals(managerKind)) {
                toast("Danh mục nguồn này chưa có gói để gỡ.");
                return;
            }
            saveSet(KEY_INSTALLED, new HashSet<>());
            saveSet(KEY_ENABLED, new HashSet<>());
            saveCustomRssSources(new JSONArray());
            for (ReaderPluginStore.SourceInfo info : ReaderPluginStore.list(this, "news")) {
                ReaderPluginStore.remove(this, info);
            }
            sourceId = "vnexpress";
            categoryName = "Trang chủ";
            getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
            renderManager();
        });

        if (!"Báo".equals(managerKind)) {
            TextView empty = text("Chưa có gói " + managerKind.toLowerCase(Locale.ROOT)
                    + " trong bản NM7 Reader này.\n\nDanh mục Báo đã có bộ đọc RSS/HTML và cài được nguồn JS riêng.",
                    14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(32), dp(20), dp(32));
            body.addView(empty, new LinearLayout.LayoutParams(-1, dp(170)));
            return;
        }

        Button addNews = actionButton("＋ Thêm nguồn báo RSS / JavaScript", true);
        body.addView(addNews, new LinearLayout.LayoutParams(-1, dp(48)));
        addNews.setOnClickListener(v -> showAddNewsSourceOptions());

        addDefaultNewsSourceCard(body);
        for (Source s : CATALOG) addSourceCard(body, s, installed, enabled);
        for (Source s : getCustomRssSources()) addCustomRssCard(body, s);
        for (ReaderPluginStore.SourceInfo info : ReaderPluginStore.list(this, "news")) addNewsPluginCard(body, info);
    }

    private void addDefaultNewsSourceCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(makeRound(SURFACE, 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(9), 0, dp(9));
        body.addView(card, cp);
        TextView logo = text("V", 20, ORANGE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(makeRound(SURFACE_2, 11));
        card.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11), 0, 0, 0);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text("VnExpress", 15, TEXT, true), new LinearLayout.LayoutParams(-1, -2));
        labels.addView(text("Nguồn mặc định · RSS, tự chuyển sang đọc trang tin khi RSS rỗng", 11, MUTED, false),
                new LinearLayout.LayoutParams(-1, -2));
        TextView state = text("Mặc định", 12, ORANGE, true);
        card.addView(state, new LinearLayout.LayoutParams(-2, dp(36)));
    }

    private void addCustomRssCard(LinearLayout body, Source source) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(makeRound(SURFACE, 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(8));
        body.addView(card, cp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(source.name, 15, TEXT, true), new LinearLayout.LayoutParams(-1, -2));
        TextView url = text(source.categories.get("Trang chủ"), 11, MUTED, false);
        url.setMaxLines(2);
        labels.addView(url, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        card.addView(controls, new LinearLayout.LayoutParams(-2, -2));
        Switch enabled = new Switch(this);
        enabled.setText(source.sourceEnabled ? "Bật" : "Tắt");
        enabled.setTextColor(MUTED);
        enabled.setChecked(source.sourceEnabled);
        controls.addView(enabled, new LinearLayout.LayoutParams(-2, dp(40)));
        enabled.setOnCheckedChangeListener((button, checked) -> updateCustomRssSource(source.id, checked));
        Button remove = actionButton("Gỡ", false);
        controls.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(32)));
        remove.setOnClickListener(v -> removeCustomRssSource(source.id));
    }

    private void addNewsPluginCard(LinearLayout body, ReaderPluginStore.SourceInfo info) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(makeRound(SURFACE, 12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(8));
        body.addView(card, cp);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text(info.name + " · JS", 15, TEXT, true), new LinearLayout.LayoutParams(-1, -2));
        labels.addView(text(info.baseUrl, 11, ORANGE, false), new LinearLayout.LayoutParams(-1, -2));
        labels.addView(text(info.description.isEmpty() ? "Nguồn báo dạng plugin JavaScript" : info.description, 11, MUTED, false),
                new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        card.addView(controls, new LinearLayout.LayoutParams(-2, -2));
        Switch enabled = new Switch(this);
        enabled.setText(info.enabled ? "Bật" : "Tắt");
        enabled.setTextColor(MUTED);
        enabled.setChecked(info.enabled);
        controls.addView(enabled, new LinearLayout.LayoutParams(-2, dp(40)));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            try { ReaderPluginStore.setEnabled(this, info, checked); enabled.setText(checked ? "Bật" : "Tắt"); }
            catch (Exception e) { enabled.setChecked(!checked); toast("Không cập nhật được nguồn: " + safeError(e)); }
        });
        Button remove = actionButton("Gỡ", false);
        controls.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(32)));
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Gỡ plugin báo?")
                .setMessage(info.name)
                .setPositiveButton("Gỡ", (dialog, which) -> {
                    ReaderPluginStore.remove(this, info);
                    if (sourceId.equals("jsnews_" + info.id)) {
                        sourceId = VNEXPRESS.id;
                        getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
                    }
                    renderManager();
                }).setNegativeButton("Hủy", null).show());
    }

    private void showAddNewsSourceOptions() {
        new AlertDialog.Builder(this).setTitle("Thêm nguồn báo")
                .setItems(new String[]{"Thêm RSS hoặc trang danh sách tin", "Cài plugin báo JavaScript từ URL", "Chọn tệp plugin .js trên máy"},
                        (dialog, which) -> {
                            if (which == 0) showAddRssSourceDialog();
                            else if (which == 1) showAddNewsPluginUrlDialog();
                            else chooseNewsPluginFile();
                        }).setNegativeButton("Hủy", null).show();
    }

    private void showAddRssSourceDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Tên nguồn, ví dụ Báo mới");
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://.../rss hoặc trang danh sách tin");
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));
        form.addView(url, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("Thêm RSS/trang tin")
                .setMessage("NM7 sẽ thử đọc RSS trước, sau đó thử trích danh sách bài trực tiếp từ trang nguồn. Bấm bài sẽ mở trong Reader của NM7.")
                .setView(form)
                .setPositiveButton("Thêm", (dialog, which) -> addCustomRssSource(
                        name.getText().toString().trim(), url.getText().toString().trim()))
                .setNegativeButton("Hủy", null).show();
    }

    private void addCustomRssSource(String name, String url) {
        if (name == null || name.trim().isEmpty()) { toast("Hãy nhập tên nguồn"); return; }
        if (!isWebUrl(url) || hostOf(url).isEmpty()) { toast("URL nguồn phải bắt đầu bằng http:// hoặc https://"); return; }
        try {
            JSONArray items = new JSONArray(getPreferencesStore().getString(KEY_CUSTOM_RSS, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject existing = items.optJSONObject(i);
                if (existing != null && url.equalsIgnoreCase(existing.optString("url", ""))) {
                    toast("Nguồn này đã được thêm");
                    return;
                }
            }
            JSONObject item = new JSONObject();
            item.put("id", "custom_rss_" + System.currentTimeMillis());
            item.put("name", name.trim());
            item.put("url", url);
            item.put("enabled", true);
            items.put(item);
            saveCustomRssSources(items);
            renderManager();
            toast("Đã thêm nguồn báo. Chọn nguồn ở thanh trên để tải tin.");
        } catch (Exception e) { toast("Không lưu được nguồn báo: " + safeError(e)); }
    }

    private void updateCustomRssSource(String id, boolean enabled) {
        try {
            JSONArray items = new JSONArray(getPreferencesStore().getString(KEY_CUSTOM_RSS, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && id.equals(item.optString("id", ""))) item.put("enabled", enabled);
            }
            saveCustomRssSources(items);
            if (!enabled && sourceId.equals(id)) {
                sourceId = VNEXPRESS.id;
                getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
            }
            renderManager();
        } catch (Exception e) { toast("Không cập nhật được nguồn: " + safeError(e)); }
    }

    private void removeCustomRssSource(String id) {
        try {
            JSONArray items = new JSONArray(getPreferencesStore().getString(KEY_CUSTOM_RSS, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && !id.equals(item.optString("id", ""))) next.put(item);
            }
            saveCustomRssSources(next);
            if (sourceId.equals(id)) {
                sourceId = VNEXPRESS.id;
                getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
            }
            renderManager();
        } catch (Exception e) { toast("Không gỡ được nguồn: " + safeError(e)); }
    }

    private void showAddNewsPluginUrlDialog() {
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://.../source.js");
        new AlertDialog.Builder(this).setTitle("Cài plugin báo từ URL")
                .setMessage("Plugin cần khai báo type: news và hàm articles(category,page) hoặc popular(category,page).")
                .setView(url)
                .setPositiveButton("Cài đặt", (dialog, which) -> installNewsPluginFromUrl(url.getText().toString().trim()))
                .setNegativeButton("Hủy", null).show();
    }

    private void chooseNewsPluginFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/javascript", "text/javascript", "text/plain", "application/x-javascript"});
        startActivityForResult(intent, REQUEST_NEWS_PLUGIN_FILE);
    }

    private void installNewsPluginFromUrl(String url) {
        if (!isWebUrl(url)) { toast("URL plugin phải bắt đầu bằng http:// hoặc https://"); return; }
        toast("Đang tải và kiểm tra plugin báo…");
        io.execute(() -> {
            try {
                String script = ReaderPluginStore.download(url);
                ReaderPluginStore.SourceInfo info = ReaderPluginStore.install(this, script, "news", url);
                main.post(() -> {
                    managerKind = "Báo";
                    renderManager();
                    toast("Đã cài plugin báo " + info.name);
                });
            } catch (Exception e) {
                main.post(() -> new AlertDialog.Builder(this).setTitle("Không cài được plugin báo")
                        .setMessage(safeError(e)).setPositiveButton("Đóng", null).show());
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_NEWS_PLUGIN_FILE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("Không đọc được tệp.");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) >= 0) {
                    if (out.size() + n > MAX_PLUGIN_FILE_BYTES) throw new java.io.IOException("File .js quá lớn (giới hạn 512 KiB).");
                    out.write(buffer, 0, n);
                }
                String script = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                ReaderPluginStore.SourceInfo info = ReaderPluginStore.install(this, script, "news", uri.toString());
                main.post(() -> {
                    managerKind = "Báo";
                    renderManager();
                    toast("Đã cài plugin báo " + info.name);
                });
            } catch (Exception e) {
                main.post(() -> new AlertDialog.Builder(this).setTitle("Không cài được tệp JS")
                        .setMessage(safeError(e)).setPositiveButton("Đóng", null).show());
            }
        });
    }

    private static String safeError(Exception e) {
        if (e == null) return "Lỗi không xác định";
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void addSourceCard(LinearLayout body, Source source, Set<String> installed, Set<String> enabled) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(makeRound(SURFACE, 12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(9));
        body.addView(card, cardLp);

        TextView logo = text(source.name.substring(0, 1).toUpperCase(Locale.ROOT), 20, ORANGE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(makeRound(SURFACE_2, 11));
        card.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout description = new LinearLayout(this);
        description.setOrientation(LinearLayout.VERTICAL);
        description.setPadding(dp(11), 0, dp(8), 0);
        card.addView(description, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView name = text(source.name, 16, TEXT, true);
        description.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView version = text("Phiên bản: v1.0.0  •  RSS", 11, ORANGE, false);
        version.setPadding(0, dp(3), 0, dp(3));
        description.addView(version, new LinearLayout.LayoutParams(-1, -2));
        TextView detail = text(source.description, 12, MUTED, false);
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        description.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.VERTICAL);
        card.addView(controls, new LinearLayout.LayoutParams(-2, -2));
        if (!installed.contains(source.id)) {
            Button install = actionButton("+ Cài đặt", true);
            install.setTextSize(12);
            controls.addView(install, new LinearLayout.LayoutParams(dp(82), dp(38)));
            install.setOnClickListener(v -> {
                Set<String> next = installedIds();
                next.add(source.id);
                saveSet(KEY_INSTALLED, next);
                Set<String> nextEnabled = enabledIds();
                nextEnabled.add(source.id);
                saveSet(KEY_ENABLED, nextEnabled);
                renderManager();
            });
        } else {
            Switch enabledSwitch = new Switch(this);
            enabledSwitch.setText(enabled.contains(source.id) ? "Bật" : "Tắt");
            enabledSwitch.setTextColor(MUTED);
            enabledSwitch.setTextSize(11);
            enabledSwitch.setChecked(enabled.contains(source.id));
            controls.addView(enabledSwitch, new LinearLayout.LayoutParams(-2, dp(40)));
            enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
                Set<String> next = enabledIds();
                if (checked) next.add(source.id); else next.remove(source.id);
                saveSet(KEY_ENABLED, next);
                enabledSwitch.setText(checked ? "Bật" : "Tắt");
            });
            Button remove = actionButton("Gỡ", false);
            remove.setTextSize(12);
            controls.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(32)));
            remove.setOnClickListener(v -> {
                Set<String> nextInstalled = installedIds();
                nextInstalled.remove(source.id);
                saveSet(KEY_INSTALLED, nextInstalled);
                Set<String> nextEnabled = enabledIds();
                nextEnabled.remove(source.id);
                saveSet(KEY_ENABLED, nextEnabled);
                if (sourceId.equals(source.id)) sourceId = "vnexpress";
                getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
                renderManager();
            });
        }
    }

    private LinkedHashMap<String, String> pluginCategoriesFor(String pluginId) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String key = KEY_PLUGIN_CATEGORY_PREFIX + pluginId;
        try {
            if (getPreferencesStore().contains(key)) {
                JSONArray categories = new JSONArray(getPreferencesStore().getString(key, "[]"));
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject item = categories.optJSONObject(i);
                    if (item == null) continue;
                    String name = item.optString("name", "").trim();
                    String value = item.optString("key", name).trim();
                    if (!name.isEmpty()) result.put(name, value.isEmpty() ? name : value);
                }
            }
        } catch (Exception ignored) { }
        if (result.isEmpty()) result.put("Trang chủ", "home");
        return result;
    }

    private void ensurePluginCategories(Source source) {
        if (source == null || !source.jsPlugin) return;
        String prefKey = KEY_PLUGIN_CATEGORY_PREFIX + source.pluginId;
        if (getPreferencesStore().contains(prefKey) || !pluginCategoriesLoading.add(source.pluginId)) return;
        io.execute(() -> {
            JSONArray saved = new JSONArray();
            try {
                ReaderPluginStore.SourceInfo info = ReaderPluginStore.find(this, "news", source.pluginId);
                String script = ReaderPluginStore.script(this, info);
                Object raw = ReaderPluginRuntime.invoke(script, "categories", new JSONArray());
                JSONArray list = asNewsArray(raw, "categories", "sections", "tabs", "items", "data");
                for (int i = 0; i < list.length(); i++) {
                    Object value = list.opt(i);
                    String name = "";
                    String key = "";
                    if (value instanceof JSONObject) {
                        JSONObject object = (JSONObject) value;
                        name = firstNonBlankNews(object.optString("name", ""), object.optString("title", ""));
                        key = firstNonBlankNews(object.optString("key", ""), object.optString("id", ""),
                                object.optString("slug", ""), name);
                    } else if (value != null) {
                        name = String.valueOf(value).trim();
                        key = name;
                    }
                    if (!name.isEmpty()) {
                        JSONObject item = new JSONObject();
                        item.put("name", name);
                        item.put("key", key.isEmpty() ? name : key);
                        saved.put(item);
                    }
                }
            } catch (Exception ignored) {
                // categories() is optional; the provider still works as a single home tab.
            }
            final JSONArray categories = saved;
            main.post(() -> {
                pluginCategoriesLoading.remove(source.pluginId);
                getPreferencesStore().edit().putString(prefKey, categories.toString()).apply();
                if (categories.length() > 0 && mode == Mode.FEED && sourceId.equals(source.id)) {
                    LinkedHashMap<String, String> loaded = pluginCategoriesFor(source.pluginId);
                    source.categories.clear();
                    source.categories.putAll(loaded);
                    if (!source.categories.containsKey(categoryName)) {
                        categoryName = source.categories.keySet().iterator().next();
                    }
                    buildCategories();
                    loadFeed();
                }
            });
        });
    }

    private List<Source> getEnabledSources() {
        List<Source> result = new ArrayList<>();
        result.add(VNEXPRESS);
        Set<String> installed = installedIds();
        Set<String> enabled = enabledIds();
        for (Source source : CATALOG) {
            if (installed.contains(source.id) && enabled.contains(source.id)) result.add(source);
        }
        for (Source source : getCustomRssSources()) if (source.sourceEnabled) result.add(source);
        for (ReaderPluginStore.SourceInfo info : ReaderPluginStore.list(this, "news")) {
            if (!info.enabled) continue;
            String host = hostOf(info.baseUrl);
            result.add(new Source("jsnews_" + info.id, info.name, info.description,
                    host, pluginCategoriesFor(info.id), true, false, true, info.id));
        }
        return result;
    }

    private Source getSource(String id) {
        if (VNEXPRESS.id.equals(id)) return VNEXPRESS;
        for (Source source : CATALOG) if (source.id.equals(id)) {
            if (installedIds().contains(id) && enabledIds().contains(id)) return source;
        }
        for (Source source : getCustomRssSources()) if (source.id.equals(id) && source.sourceEnabled) return source;
        for (ReaderPluginStore.SourceInfo info : ReaderPluginStore.list(this, "news")) {
            if (("jsnews_" + info.id).equals(id) && info.enabled) {
                return new Source(id, info.name, info.description, hostOf(info.baseUrl),
                        pluginCategoriesFor(info.id), true, false, true, info.id);
            }
        }
        return VNEXPRESS;
    }

    private List<Source> getCustomRssSources() {
        List<Source> result = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(getPreferencesStore().getString(KEY_CUSTOM_RSS, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                String name = item.optString("name", "").trim();
                String url = item.optString("url", "").trim();
                if (id.isEmpty() || name.isEmpty() || !isWebUrl(url)) continue;
                result.add(new Source(id, name, "Nguồn báo do người dùng thêm", hostOf(url),
                        cats("Trang chủ", url), false, true, item.optBoolean("enabled", true), ""));
            }
        } catch (Exception ignored) { }
        return result;
    }

    private static String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) { return ""; }
    }

    private void saveCustomRssSources(JSONArray items) {
        getPreferencesStore().edit().putString(KEY_CUSTOM_RSS, items.toString()).apply();
    }

    private Set<String> installedIds() { return readSet(KEY_INSTALLED); }
    private Set<String> enabledIds() { return readSet(KEY_ENABLED); }
    private Set<String> readSet(String key) {
        Set<String> value = getPreferencesStore().getStringSet(key, Collections.emptySet());
        return value == null ? new HashSet<>() : new HashSet<>(value);
    }
    private void saveSet(String key, Set<String> value) {
        getPreferencesStore().edit().putStringSet(key, new HashSet<>(value)).apply();
    }

    private List<Article> loadArticlesWithFallback(Source source, String feedUrl, String category) throws Exception {
        if (source.jsPlugin) return parsePluginNewsSource(source, category);

        final boolean strict24hCategory = "24h".equals(source.id) && !"Trang chủ".equals(category);
        Exception rssFailure = null;
        List<Article> rssArticles = Collections.emptyList();
        try {
            rssArticles = filterArticlesForCategory(source, category, parseFeed(feedUrl, source.name));
        } catch (Exception e) {
            rssFailure = e;
        }

        // Never trust a non-empty RSS response blindly for a 24h category. Some RSS
        // responses may contain homepage/trending entries; keep only links whose
        // publisher route belongs to the selected category.
        if (!rssArticles.isEmpty() && (!strict24hCategory || rssArticles.size() >= 8)) {
            return rssArticles;
        }

        String pageUrl = htmlUrlFor(source, category);
        Exception pageFailure = null;
        List<Article> pageArticles = Collections.emptyList();
        if (isWebUrl(pageUrl)) {
            try {
                pageArticles = filterArticlesForCategory(source, category, parseHtmlFeed(pageUrl, source));
                if (!pageArticles.isEmpty()) {
                    return strict24hCategory ? mergeArticles(rssArticles, pageArticles) : pageArticles;
                }
            } catch (Exception e) {
                pageFailure = e;
            }
        }

        // If the category page was unavailable but RSS returned some correctly
        // categorized stories, return those stories—not the previous tab's list.
        if (!rssArticles.isEmpty()) return rssArticles;

        // This final fallback remains for existing sources, but for 24h it is also
        // filtered by route, so an RSS URL can never turn into a generic homepage list.
        if (isWebUrl(feedUrl) && !feedUrl.equals(pageUrl)) {
            try {
                List<Article> feedPage = filterArticlesForCategory(source, category, parseHtmlFeed(feedUrl, source));
                if (!feedPage.isEmpty()) return strict24hCategory
                        ? mergeArticles(rssArticles, feedPage) : feedPage;
            } catch (Exception e) {
                if (pageFailure == null) pageFailure = e;
            }
        }

        if (pageFailure != null) throw new java.io.IOException("RSS và trang tin đều không tải được: "
                + safeError(pageFailure), pageFailure);
        if (rssFailure != null) throw new java.io.IOException("RSS không dùng được và trang tin không có bài đúng chuyên mục: "
                + safeError(rssFailure), rssFailure);
        return Collections.emptyList();
    }

    private List<Article> filterArticlesForCategory(Source source, String category, List<Article> input) {
        if (!"24h".equals(source.id) || "Trang chủ".equals(category)) return input;
        List<Article> filtered = new ArrayList<>();
        if (input == null) return filtered;
        for (Article article : input) {
            if (article != null && ReaderFeedCategoryRouter.matchesArticle(source.id, category, article.url)) {
                filtered.add(article);
            }
        }
        return filtered;
    }

    private List<Article> mergeArticles(List<Article> first, List<Article> second) {
        LinkedHashMap<String, Article> merged = new LinkedHashMap<>();
        if (first != null) {
            for (Article article : first) addUniqueArticle(merged, article);
        }
        if (second != null) {
            for (Article article : second) addUniqueArticle(merged, article);
        }
        return new ArrayList<>(merged.values());
    }

    private void addUniqueArticle(LinkedHashMap<String, Article> merged, Article article) {
        if (article == null || TextUtils.isEmpty(article.url)) return;
        String key = canonicalArticleUrl(article.url);
        if (TextUtils.isEmpty(key)) key = article.url.trim();
        if (!merged.containsKey(key)) merged.put(key, article);
    }

    private static String htmlUrlFor(Source source, String category) {
        String id = source.id;
        if (source.jsPlugin) return source.homeUrl();
        if (source.customRss) return source.homeUrl();
        if ("24h".equals(id)) {
            // For a 24h tab with no specific fallback URL, return empty instead of
            // silently parsing the homepage and showing unrelated headlines.
            return ReaderFeedCategoryRouter.htmlPageUrl(id, category, source.homeUrl());
        }

        if ("vnexpress".equals(id)) {
            switch (category) {
                case "Thế giới": return "https://vnexpress.net/the-gioi";
                case "Thời sự": return "https://vnexpress.net/thoi-su";
                case "Kinh doanh": return "https://vnexpress.net/kinh-doanh";
                case "Giải trí": return "https://vnexpress.net/giai-tri";
                case "Thể thao": return "https://vnexpress.net/the-thao";
                case "Pháp luật": return "https://vnexpress.net/phap-luat";
                case "Giáo dục": return "https://vnexpress.net/giao-duc";
                case "Góc nhìn": return "https://vnexpress.net/goc-nhin";
                case "Bất động sản": return "https://vnexpress.net/bat-dong-san";
                case "Sức khỏe": return "https://vnexpress.net/suc-khoe";
                case "Đời sống": return "https://vnexpress.net/doi-song";
                case "Du lịch": return "https://vnexpress.net/du-lich";
                case "Khoa học công nghệ": return "https://vnexpress.net/khoa-hoc-cong-nghe";
                case "Ô tô xe máy": return "https://vnexpress.net/oto-xe-may";
                case "Ý kiến": return "https://vnexpress.net/y-kien";
                case "Tâm sự": return "https://vnexpress.net/tam-su";
                case "Thư giãn": return "https://vnexpress.net/thu-gian";
                case "Tin xem nhiều": return "https://vnexpress.net/tin-xem-nhieu";
                default: return "https://vnexpress.net/";
            }
        }
        if ("dantri".equals(id)) {
            switch (category) {
                case "Thời sự": return "https://dantri.com.vn/thoi-su.htm";
                case "Thế giới": return "https://dantri.com.vn/the-gioi.htm";
                case "Kinh doanh": return "https://dantri.com.vn/kinh-doanh.htm";
                case "Thể thao": return "https://dantri.com.vn/the-thao.htm";
                case "Công nghệ": return "https://dantri.com.vn/cong-nghe.htm";
                default: return "https://dantri.com.vn/";
            }
        }
        if ("tuoitre".equals(id)) {
            switch (category) {
                case "Thời sự": return "https://tuoitre.vn/thoi-su.htm";
                case "Thế giới": return "https://tuoitre.vn/the-gioi.htm";
                case "Công nghệ": return "https://tuoitre.vn/cong-nghe.htm";
                default: return "https://tuoitre.vn/";
            }
        }
        if ("thanhnien".equals(id)) {
            switch (category) {
                case "Thời sự": return "https://thanhnien.vn/thoi-su.htm";
                case "Công nghệ": return "https://thanhnien.vn/cong-nghe.htm";
                default: return "https://thanhnien.vn/";
            }
        }
        if ("vtv".equals(id)) {
            if ("Thời sự".equals(category)) return "https://vtv.vn/thoi-su.htm";
            return "https://vtv.vn/";
        }
        return source.homeUrl();
    }

    private static List<Article> parseHtmlFeed(String pageUrl, Source source) throws Exception {
        Document document = Jsoup.connect(pageUrl)
                .userAgent(userAgent())
                .referrer("https://www.google.com/")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.5")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(16000)
                .maxBodySize(6 * 1024 * 1024)
                .followRedirects(true)
                .get();

        Elements links = document.select(
                "article a[href],.item-news a[href],.item-news-common a[href],.news-item a[href]," +
                ".news-card a[href],.box-category-link-title a[href],.title-news a[href]," +
                ".news-title a[href],.story-title a[href],h1 a[href],h2 a[href],h3 a[href]");
        if (links.size() < 5) links = document.select("a[href]");

        LinkedHashMap<String, Article> unique = new LinkedHashMap<>();
        String pageKey = canonicalArticleUrl(pageUrl);
        String sourceHost = source.domain == null ? "" : source.domain.toLowerCase(Locale.ROOT);
        for (Element link : links) {
            if (unique.size() >= 80) break;
            String url = absolutizeUrl(link.absUrl("href"), pageUrl);
            if (!isWebUrl(url) || pageKey.equals(canonicalArticleUrl(url))) continue;
            if (!sameNewsSite(url, sourceHost)) continue;
            String path = Uri.parse(url).getPath();
            String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
            if (lowerPath.isEmpty() || "/".equals(lowerPath) || isNonArticlePath(lowerPath)) continue;

            String title = firstNonBlankNews(link.attr("title"), link.text());
            Element imageNode = link.selectFirst("img");
            if (title.isEmpty() && imageNode != null) title = firstNonBlankNews(
                    imageNode.attr("alt"), imageNode.attr("title"));
            if (title.length() < 12 || title.length() > 240) continue;
            String lowerTitle = title.toLowerCase(Locale.ROOT);
            if (lowerTitle.matches(".*(xem thêm|đọc tiếp|tin liên quan|video nổi bật|xem tất cả).*")) continue;

            Element card = link;
            Element bestCard = link;
            int bestLength = link.text().length();
            Element parent = link.parent();
            for (int depth = 0; parent != null && depth < 4; depth++, parent = parent.parent()) {
                String parentText = parent.text().trim();
                if (parentText.length() >= Math.max(40, title.length()) && parentText.length() <= 1400) {
                    bestCard = parent;
                    bestLength = parentText.length();
                } else if (parentText.length() > 1400) {
                    break;
                }
            }
            card = bestCard;
            if (card != null) {
                String combined = card.text().trim();
                if (combined.length() > bestLength) bestLength = combined.length();
            }

            String image = "";
            if (imageNode == null && card != null) imageNode = card.selectFirst("img");
            if (imageNode != null) {
                image = firstNonBlankNews(imageNode.absUrl("data-src"),
                        imageNode.absUrl("data-original"), imageNode.absUrl("data-lazy-src"),
                        imageNode.absUrl("src"));
                if (image.isEmpty()) {
                    String raw = firstNonBlankNews(imageNode.attr("data-src"), imageNode.attr("data-original"),
                            imageNode.attr("data-lazy-src"), imageNode.attr("src"));
                    image = absolutizeUrl(raw, pageUrl);
                }
            }

            String summary = "";
            String date = "";
            if (card != null) {
                summary = cleanText(card.text().replace(title, " "));
                Element dateNode = card.selectFirst("time,.time,.date,.publish-date,.time-update,.date-time");
                if (dateNode != null) date = cleanDate(dateNode.text());
            }
            if (summary.equalsIgnoreCase(title)) summary = "";
            if (summary.length() > 360) summary = summary.substring(0, 357) + "...";

            Article article = new Article();
            article.title = cleanText(title);
            article.url = url;
            article.summary = summary;
            article.imageUrl = isWebUrl(image) ? image : "";
            article.date = date;
            article.source = source.name;
            String key = canonicalArticleUrl(url);
            if (!unique.containsKey(key)) unique.put(key, article);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Article> parsePluginNewsSource(Source source, String category) throws Exception {
        ReaderPluginStore.SourceInfo info = ReaderPluginStore.find(this, "news", source.pluginId);
        String script = ReaderPluginStore.script(this, info);
        JSONArray args = new JSONArray().put(category).put(1);
        Object value;
        try {
            value = ReaderPluginRuntime.invoke(script, "articles", args);
        } catch (Exception first) {
            value = ReaderPluginRuntime.invoke(script, "popular", args);
        }
        JSONArray items = asNewsArray(value, "articles", "items", "results", "news", "data");
        List<Article> result = new ArrayList<>();
        for (int i = 0; i < items.length() && result.size() < 80; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            Article article = new Article();
            article.title = firstNonBlankNews(item.optString("title", ""), item.optString("headline", ""),
                    item.optString("name", ""));
            article.url = absolutizeUrl(firstNonBlankNews(item.optString("url", ""),
                    item.optString("link", ""), item.optString("href", "")), source.homeUrl());
            article.summary = cleanText(firstNonBlankNews(item.optString("summary", ""),
                    item.optString("description", ""), item.optString("excerpt", "")));
            article.imageUrl = absolutizeUrl(firstNonBlankNews(item.optString("imageUrl", ""),
                    item.optString("image", ""), item.optString("thumbnail", ""),
                    item.optString("cover", "")), source.homeUrl());
            article.date = cleanDate(firstNonBlankNews(item.optString("date", ""),
                    item.optString("published", ""), item.optString("publishedAt", "")));
            article.source = source.name;
            if (!article.title.isEmpty() && isWebUrl(article.url)) result.add(article);
        }
        if (result.isEmpty()) throw new java.io.IOException("Plugin báo không trả về bài nào. Kiểm tra articles(category,page).");
        return result;
    }

    private static JSONArray asNewsArray(Object value, String... wrappers) {
        if (value instanceof JSONArray) return (JSONArray) value;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : wrappers) {
                JSONArray array = object.optJSONArray(key);
                if (array != null) return array;
            }
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.startsWith("[") || raw.startsWith("{")) {
                try {
                    Object parsed = new org.json.JSONTokener(raw).nextValue();
                    if (parsed instanceof JSONArray) return (JSONArray) parsed;
                    if (parsed instanceof JSONObject) return asNewsArray(parsed, wrappers);
                } catch (Exception ignored) { }
            }
        }
        return new JSONArray();
    }

    private static boolean sameNewsSite(String url, String hostSuffix) {
        String host = hostOf(url).toLowerCase(Locale.ROOT);
        if (host.isEmpty() || hostSuffix == null || hostSuffix.isEmpty()) return true;
        return host.equals(hostSuffix) || host.endsWith("." + hostSuffix)
                || hostSuffix.endsWith("." + host);
    }

    private static boolean isNonArticlePath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png")
                || p.endsWith(".webp") || p.endsWith(".gif") || p.endsWith(".css")
                || p.endsWith(".js") || p.endsWith(".xml") || p.contains("/rss")
                || p.contains("/search") || p.contains("/tim-kiem") || p.contains("/tag/")
                || p.contains("/tags/") || p.contains("/author/") || p.contains("/tac-gia/")
                || p.contains("/dang-nhap") || p.contains("/lien-he") || p.contains("/contact")
                || p.contains("/gioi-thieu") || p.contains("/chinh-sach");
    }

    private static String canonicalArticleUrl(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return (uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT)) + path;
        } catch (Exception ignored) { return raw == null ? "" : raw; }
    }

    private static String absolutizeUrl(String raw, String baseUrl) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String value = raw.trim();
        if (value.startsWith("//")) return "https:" + value;
        if (isWebUrl(value)) return value;
        try { return new URL(new URL(baseUrl), value).toString(); }
        catch (Exception ignored) { return ""; }
    }

    private static String firstNonBlankNews(String... values) {
        if (values == null) return "";
        for (String value : values) if (value != null && !value.trim().isEmpty()
                && !"null".equalsIgnoreCase(value.trim())) return value.trim();
        return "";
    }

    private static List<Article> parseFeed(String feedUrl, String sourceName) throws Exception {
        HttpURLConnection conn = null;
        List<Article> result = new ArrayList<>();
        try {
            conn = (HttpURLConnection) new URL(feedUrl).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", userAgent());
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*");
            conn.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.5");
            try (InputStream input = conn.getInputStream()) {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(input, "UTF-8");
                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT && result.size() < 80) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                                            Article a = parseItem(parser);
                            a.url = absolutizeUrl(a.url, feedUrl);
                            a.imageUrl = absolutizeUrl(a.imageUrl, feedUrl);
                            if (!TextUtils.isEmpty(a.title) && !TextUtils.isEmpty(a.url)) {
                                a.source = sourceName;
                                result.add(a);
                            }
                        }
                    }
                    event = parser.next();
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    private static Article parseItem(XmlPullParser parser) throws Exception {
        Article a = new Article();
        int startDepth = parser.getDepth();
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) break;
            if (event == XmlPullParser.END_TAG && parser.getDepth() == startDepth
                    && ("item".equalsIgnoreCase(parser.getName()) || "entry".equalsIgnoreCase(parser.getName()))) break;
            if (event != XmlPullParser.START_TAG) continue;
            String name = parser.getName().toLowerCase(Locale.ROOT);
            String local = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
            if ("enclosure".equals(local) || "content".equals(local) || "thumbnail".equals(local) || "image".equals(local)) {
                String url = parser.getAttributeValue(null, "url");
                if (TextUtils.isEmpty(url)) url = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href");
                if (TextUtils.isEmpty(url)) url = parser.getAttributeValue(null, "href");
                if (TextUtils.isEmpty(a.imageUrl) && isWebUrl(url)) a.imageUrl = normalizeUrl(url);
            }
            if ("title".equals(local) || "link".equals(local) || "description".equals(local)
                    || "summary".equals(local) || "encoded".equals(local) || "pubdate".equals(local)
                    || "published".equals(local) || "updated".equals(local) || "date".equals(local)) {
                String value = "";
                if ("link".equals(local) && !TextUtils.isEmpty(parser.getAttributeValue(null, "href"))) {
                    value = parser.getAttributeValue(null, "href");
                    skipElement(parser);
                } else {
                    try { value = parser.nextText(); } catch (Exception ignored) { }
                }
                if ("title".equals(local) && TextUtils.isEmpty(a.title)) a.title = cleanText(value);
                else if ("link".equals(local) && TextUtils.isEmpty(a.url)) a.url = normalizeUrl(value.trim());
                else if (("description".equals(local) || "summary".equals(local) || "encoded".equals(local)) && TextUtils.isEmpty(a.summary)) {
                    a.summary = cleanText(value);
                    if (TextUtils.isEmpty(a.imageUrl)) a.imageUrl = extractImage(value);
                } else if (("pubdate".equals(local) || "published".equals(local) || "updated".equals(local) || "date".equals(local))
                        && TextUtils.isEmpty(a.date)) a.date = cleanDate(value);
            }
        }
        if (TextUtils.isEmpty(a.imageUrl)) a.imageUrl = "";
        return a;
    }

    private static void skipElement(XmlPullParser parser) throws Exception {
        int depth = parser.getDepth();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() == depth) return;
        }
    }

    private static String extractImage(String html) {
        if (TextUtils.isEmpty(html)) return "";
        Matcher m = IMG_SRC.matcher(html);
        if (!m.find()) return "";
        return normalizeUrl(m.group(1).replace("&amp;", "&"));
    }

    private static String cleanText(String raw) {
        if (raw == null) return "";
        String value = raw.replace("&nbsp;", " ").replace("&#39;", "'").replace("&amp;", "&");
        value = (Build.VERSION.SDK_INT >= 24 ? Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT) : Html.fromHtml(value)).toString();
        value = SPACE.matcher(TAGS.matcher(value).replaceAll(" ")).replaceAll(" ").trim();
        return value;
    }

    private static String cleanDate(String raw) {
        if (raw == null) return "";
        String date = raw.replaceAll("\\s+", " ").trim();
        return date.length() > 22 ? date.substring(0, 22) : date;
    }

    private static String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String value = url.trim();
        if (value.startsWith("//")) return "https:" + value;
        return value;
    }

    private static boolean isWebUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//"));
    }

    private static String userAgent() {
        return "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";
    }

    private static Source getSourceStatic(String id) {
        if ("vnexpress".equals(id)) return VNEXPRESS;
        for (Source s : CATALOG) if (s.id.equals(id)) return s;
        return VNEXPRESS;
    }

    private static Source makeSource(String id, String name, String description, String domain,
                                     LinkedHashMap<String, String> categories) {
        return new Source(id, name, description, domain, categories);
    }

    private static LinkedHashMap<String, String> cats(String... pairs) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackground(makeRound(primary ? ORANGE : SURFACE_2, 9));
        return button;
    }

    private GradientDrawable makeRound(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override public void onBackPressed() {
        if (mode == Mode.ARTICLE) {
            renderFeed(false);
            return;
        }
        if (mode == Mode.MANAGER) {
            renderFeed(true);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        ++requestToken;
        closeArticleWebView();
        io.shutdownNow();
        super.onDestroy();
    }

    private static final class Source {
        final String id, name, description, domain;
        final LinkedHashMap<String, String> categories;
        final boolean jsPlugin, customRss, sourceEnabled;
        final String pluginId;
        Source(String id, String name, String description, String domain, LinkedHashMap<String, String> categories) {
            this(id, name, description, domain, categories, false, false, true, "");
        }
        Source(String id, String name, String description, String domain,
               LinkedHashMap<String, String> categories, boolean jsPlugin,
               boolean customRss, boolean sourceEnabled, String pluginId) {
            this.id = id; this.name = name; this.description = description;
            this.domain = domain == null ? "" : domain;
            this.categories = categories;
            this.jsPlugin = jsPlugin; this.customRss = customRss;
            this.sourceEnabled = sourceEnabled; this.pluginId = pluginId == null ? "" : pluginId;
        }
        String homeUrl() {
            if (domain.startsWith("http://") || domain.startsWith("https://")) return domain;
            return "https://" + domain;
        }
    }

    private static final class Article {
        String title = "", url = "", summary = "", imageUrl = "", date = "", source = "";
    }
}
