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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    private static final Pattern IMG_SRC = Pattern.compile("(?is)<img[^>]+(?:src|data-src)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern TAGS = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final String READER_MODE_SCRIPT = """
            (function () {
              try {
                var adSelector = '[id^="ad-"],[id*="-ad-"],[id*="_ad_"],[id*="ads-"],[id*="ads_"],' +
                  '[class*="advert"],[class*="adsense"],[class*="ad-container"],[class*="adslot"],' +
                  '[class*="ad-slot"],[class*="banner-ad"],[class*="sponsor"],[class*="sponsored"],' +
                  '[data-ad],[data-ad-unit],[data-ad-slot],[aria-label*="quang cao"],[aria-label*="advertisement"]';
                function stripAds(root) {
                  if (!root || !root.querySelectorAll) return;
                  try { Array.prototype.forEach.call(root.querySelectorAll(adSelector), function (node) {
                    if (node && node.parentNode) node.parentNode.removeChild(node);
                  }); } catch (ignored) {}
                }
                stripAds(document);

                var css = [
                  'html,body{background:#101114!important;color:#f1f2f5!important;min-height:100%;}',
                  'body{margin:0!important;padding:18px 16px 42px!important;font-family:Roboto,Arial,sans-serif!important;font-size:18px!important;line-height:1.78!important;}',
                  'body *{color:#e9e9ed!important;border-color:#373840!important;text-shadow:none!important;box-shadow:none!important;}',
                  'body a{color:#ff9b45!important;text-decoration:none;}',
                  'body img,body video,body iframe,body svg,body canvas{max-width:100%!important;}',
                  'body img{height:auto!important;object-fit:contain;}',
                  'body > header,body > nav,body > aside,body > footer,[class*="advert"],[class*="adsense"],[class*="ad-container"],[class*="adslot"],[class*="ad-slot"],[class*="banner-ad"],[class*="sponsor"],[class*="sponsored"],[data-ad],[data-ad-unit],[data-ad-slot]{display:none!important;}',
                  '#nm7-reader-content{max-width:860px!important;margin:0 auto!important;padding:2px 0 38px!important;background:#101114!important;}',
                  '#nm7-reader-content h1{font-size:28px!important;line-height:1.35!important;font-weight:700!important;color:#fff!important;margin:0 0 12px!important;}',
                  '#nm7-reader-content .nm7-reader-meta{font-size:13px!important;color:#aeb1bb!important;margin:0 0 18px!important;}',
                  '#nm7-reader-content .nm7-reader-summary{font-size:19px!important;font-weight:600!important;color:#f5f5f7!important;}',
                  '#nm7-reader-content p,#nm7-reader-content li{font-size:18px!important;line-height:1.8!important;color:#e9e9ed!important;}',
                  '#nm7-reader-content figure{margin:18px 0!important;}',
                  '#nm7-reader-content figcaption{font-size:13px!important;color:#aeb1bb!important;}'
                ].join('');
                if (!document.getElementById('nm7-reader-style')) {
                  var style = document.createElement('style');
                  style.id = 'nm7-reader-style';
                  style.type = 'text/css';
                  style.appendChild(document.createTextNode(css));
                  (document.head || document.documentElement).appendChild(style);
                }

                var titleNode = document.querySelector('h1.title-detail,h1.title,[itemprop="headline"],.article-title,h1');
                var titleText = titleNode ? (titleNode.innerText || titleNode.textContent || '').trim() : '';
                var bodySelectors = [
                  '[itemprop="articleBody"]','.fck_detail','.dt-news__content',
                  '.singular-content','.article-content','.article__body','.article-body',
                  '.detail-content','.content-detail','.detail__content','#article-content',
                  '.article-content-body','article','main'
                ];
                var articleNode = null;
                for (var i = 0; i < bodySelectors.length; i++) {
                  var nodes = document.querySelectorAll(bodySelectors[i]);
                  for (var j = 0; j < nodes.length; j++) {
                    var node = nodes[j];
                    var text = (node.innerText || node.textContent || '').trim();
                    var paragraphs = node.querySelectorAll('p').length;
                    if (text.length >= 280 && paragraphs >= 2) {
                      articleNode = node;
                      break;
                    }
                  }
                  if (articleNode) break;
                }

                if (articleNode && !document.getElementById('nm7-reader-content')) {
                  var summaryNode = document.querySelector('.description,.sapo,.detail-sapo,[itemprop="description"],.article-summary');
                  var summaryText = summaryNode && !articleNode.contains(summaryNode)
                    ? (summaryNode.innerText || summaryNode.textContent || '').trim() : '';
                  var metaNode = document.querySelector('time,.date-time,.publish-date,[itemprop="datePublished"],.author');
                  var metaText = metaNode ? (metaNode.innerText || metaNode.textContent || '').trim() : '';
                  var clone = articleNode.cloneNode(true);
                  stripAds(clone);
                  Array.prototype.forEach.call(clone.querySelectorAll(
                    'script,style,noscript,nav,aside,form,button,[class*="related"],[id*="related"],' +
                    '[class*="recommend"],[id*="recommend"],[class*="most-viewed"],[class*="social-share"],' +
                    '[class*="comment"],[class*="advert"],[class*="adsense"],[class*="ad-container"],' +
                    '[class*="banner-ad"],[class*="sponsor"],[data-ad]'
                  ), function (node) {
                    if (node && node.parentNode) node.parentNode.removeChild(node);
                  });
                  Array.prototype.forEach.call(clone.querySelectorAll('img'), function (img) {
                    var src = img.getAttribute('src') || img.getAttribute('data-src') ||
                      img.getAttribute('data-original') || img.getAttribute('data-lazy-src');
                    if (src) img.setAttribute('src', src);
                    img.removeAttribute('srcset');
                    img.setAttribute('loading', 'eager');
                  });
                  Array.prototype.forEach.call(clone.querySelectorAll('iframe'), function (frame) {
                    var src = frame.getAttribute('src') || '';
                    if (!/youtube\.com|youtu\.be|player\.vimeo\.com/i.test(src)) {
                      if (frame.parentNode) frame.parentNode.removeChild(frame);
                    }
                  });
                  clone.removeAttribute('id');
                  clone.className = 'nm7-reader-body';
                  var shell = document.createElement('main');
                  shell.id = 'nm7-reader-content';
                  if (titleText) {
                    var heading = document.createElement('h1');
                    heading.textContent = titleText;
                    shell.appendChild(heading);
                  }
                  if (metaText) {
                    var meta = document.createElement('div');
                    meta.className = 'nm7-reader-meta';
                    meta.textContent = metaText;
                    shell.appendChild(meta);
                  }
                  if (summaryText && summaryText.length < 900) {
                    var summary = document.createElement('p');
                    summary.className = 'nm7-reader-summary';
                    summary.textContent = summaryText;
                    shell.appendChild(summary);
                  }
                  shell.appendChild(clone);
                  document.body.innerHTML = '';
                  document.body.appendChild(shell);
                  document.body.style.background = '#101114';
                  document.documentElement.style.background = '#101114';
                }
                stripAds(document);
              } catch (error) {
                try {
                  document.documentElement.style.background = '#101114';
                  document.body.style.background = '#101114';
                  document.body.style.color = '#f1f2f5';
                } catch (ignored) {}
              }
            })();
            """;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final List<Article> articles = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout toolbar;
    private HorizontalScrollView categoryScroller;
    private LinearLayout categoryRow;
    private FrameLayout content;
    private LinearLayout articleRows;
    private TextView status;
    private WebView articleWebView;
    private Mode mode = Mode.FEED;
    private String sourceId = "vnexpress";
    private String categoryName = "Trang chủ";
    private String currentArticleUrl;
    private String currentArticleTitle;
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
                            "Thể thao", "https://www.24h.com.vn/upload/rss/thethao.rss",
                            "Công nghệ", "https://www.24h.com.vn/upload/rss/congnghethongtin.rss")),
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
        if (refresh || articles.isEmpty()) loadFeed();
        else renderArticleRows();
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
        font.setOnClickListener(v -> {
            textScale = textScale >= 120 ? 100 : textScale + 10;
            renderArticleRows();
            toast("Cỡ chữ " + textScale + "%");
        });

        TextView manage = text("▤", 25, ORANGE, true);
        manage.setGravity(Gravity.CENTER);
        manage.setContentDescription("Quản lý nguồn đọc");
        toolbar.addView(manage, new LinearLayout.LayoutParams(dp(42), -1));
        manage.setOnClickListener(v -> renderManager());
        TextView refresh = text("↻", 25, ORANGE, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("Làm mới tin");
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(34), -1));
        refresh.setOnClickListener(v -> loadFeed());
    }

    private void buildCategories() {
        categoryRow.removeAllViews();
        Source source = getSource(sourceId);
        if (!source.categories.containsKey(categoryName)) categoryName = source.categories.keySet().iterator().next();
        for (String name : source.categories.keySet()) {
            TextView tab = text(name, 14, name.equals(categoryName) ? ORANGE : MUTED, name.equals(categoryName));
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(13), 0, dp(13), 0);
            tab.setSingleLine(true);
            categoryRow.addView(tab, new LinearLayout.LayoutParams(-2, -1));
            tab.setOnClickListener(v -> {
                categoryName = name;
                searchQuery = "";
                buildCategories();
                loadFeed();
            });
        }
    }

    private void showArticleList() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
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

    private void loadFeed() {
        if (mode != Mode.FEED) return;
        Source source = getSource(sourceId);
        String feed = source.categories.get(categoryName);
        if (feed == null) {
            categoryName = source.categories.keySet().iterator().next();
            feed = source.categories.get(categoryName);
        }
        final String feedUrl = feed;
        final int token = ++requestToken;
        showLoading("Đang tải " + source.name + "…");
        io.execute(() -> {
            List<Article> loaded;
            String error = null;
            try {
                loaded = parseFeed(feedUrl, source.name);
                if (loaded.isEmpty()) error = "Nguồn chưa trả về bài viết. Thử làm mới hoặc mở trang báo trực tiếp.";
            } catch (Exception e) {
                loaded = Collections.emptyList();
                error = "Không tải được nguồn tin. Kiểm tra kết nối hoặc thử nguồn khác.";
            }
            final List<Article> result = loaded;
            final String message = error;
            main.post(() -> {
                if (token != requestToken || mode != Mode.FEED) return;
                articles.clear();
                articles.addAll(result);
                if (message != null && articles.isEmpty()) {
                    showLoading(message + "\n" + source.name);
                } else {
                    renderArticleRows();
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
        articleRows.addView(status, new LinearLayout.LayoutParams(-1, dp(120)));
        TextView action = text("Mở trang " + getSource(sourceId).name, 14, ORANGE, true);
        action.setGravity(Gravity.CENTER);
        articleRows.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
        action.setOnClickListener(v -> openArticlePage(getSource(sourceId).homeUrl()));
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
            currentArticleTitle = article.title;
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
        TextView title = text(currentArticleTitle == null ? getSource(sourceId).name : currentArticleTitle,
                16, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView share = text("↗", 23, ORANGE, true);
        share.setGravity(Gravity.CENTER);
        toolbar.addView(share, new LinearLayout.LayoutParams(dp(42), -1));
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, currentArticleUrl);
            startActivity(Intent.createChooser(send, "Chia sẻ bài viết"));
        });

        content.removeAllViews();
        articleWebView = new WebView(this);
        articleWebView.setBackgroundColor(BG);
        WebSettings settings = articleWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(textScale);
        settings.setUserAgentString(userAgent());
        articleWebView.setWebChromeClient(new WebChromeClient());
        articleWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String newUrl) {
                if (newUrl == null) return true;
                if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                    currentArticleUrl = newUrl;
                    view.loadUrl(newUrl);
                    return true;
                }
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if (request != null && isBlockedAdRequest(request.getUrl(), request.isForMainFrame())) {
                    return new WebResourceResponse("text/plain", "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onPageFinished(WebView view, String finishedUrl) {
                super.onPageFinished(view, finishedUrl);
                if (view == articleWebView && mode == Mode.ARTICLE) {
                    view.evaluateJavascript(READER_MODE_SCRIPT, null);
                }
            }
        });
        content.addView(articleWebView, new FrameLayout.LayoutParams(-1, -1));
        articleWebView.loadUrl(url);
    }

    private static boolean isBlockedAdRequest(Uri uri, boolean mainFrame) {
        if (uri == null || mainFrame) return false;
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getEncodedPath() == null ? "" : uri.getEncodedPath().toLowerCase(Locale.ROOT);
        String[] adHosts = {
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "adservice.google.com", "googletagservices.com", "googletagmanager.com",
                "google-analytics.com", "analytics.google.com", "adnxs.com", "taboola.com",
                "outbrain.com", "criteo.com", "scorecardresearch.com", "quantserve.com",
                "casalemedia.com", "adform.net", "pubmatic.com", "rubiconproject.com",
                "smartadserver.com", "admicro.vn", "adnetwork.vn", "zedo.com",
                "moatads.com", "serving-sys.com", "yieldmo.com"
        };
        for (String domain : adHosts) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return path.contains("/ads/") || path.contains("/adserver/")
                || path.contains("/advertising/") || path.contains("/advertisement/")
                || path.contains("/quang-cao/") || path.contains("/quangcao/")
                || path.contains("/banner-ad/") || path.contains("/tracking/")
                || path.contains("/analytics/") || path.endsWith("/ad.js")
                || path.endsWith("/ads.js") || path.endsWith("/pixel.gif");
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
        TextView count = text("Đã cài: " + installed.size() + "  •  Đang bật: " + enabled.size(),
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
            sourceId = "vnexpress";
            categoryName = "Trang chủ";
            getPreferencesStore().edit().putString(KEY_SOURCE, sourceId).apply();
            renderManager();
        });

        if (!"Báo".equals(managerKind)) {
            TextView empty = text("Chưa có gói " + managerKind.toLowerCase(Locale.ROOT)
                    + " trong bản NM7 Reader này.\n\nDanh mục Báo đã có bộ đọc RSS tích hợp và có thể cài, bật hoặc gỡ từng nguồn.",
                    14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(32), dp(20), dp(32));
            body.addView(empty, new LinearLayout.LayoutParams(-1, dp(170)));
            return;
        }

        for (Source s : CATALOG) addSourceCard(body, s, installed, enabled);
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

    private List<Source> getEnabledSources() {
        List<Source> result = new ArrayList<>();
        result.add(VNEXPRESS);
        Set<String> installed = installedIds();
        Set<String> enabled = enabledIds();
        for (Source source : CATALOG) {
            if (installed.contains(source.id) && enabled.contains(source.id)) result.add(source);
        }
        return result;
    }

    private Source getSource(String id) {
        if (VNEXPRESS.id.equals(id)) return VNEXPRESS;
        for (Source source : CATALOG) if (source.id.equals(id)) {
            if (installedIds().contains(id) && enabledIds().contains(id)) return source;
            break;
        }
        return VNEXPRESS;
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
        Source(String id, String name, String description, String domain, LinkedHashMap<String, String> categories) {
            this.id = id; this.name = name; this.description = description; this.domain = domain;
            this.categories = categories;
        }
        String homeUrl() { return "https://" + domain; }
    }

    private static final class Article {
        String title = "", url = "", summary = "", imageUrl = "", date = "", source = "";
    }
}
