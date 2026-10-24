package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;

import com.tiktok.open.sdk.auth.AuthApi;
import com.tiktok.open.sdk.auth.AuthRequest;
import com.tiktok.open.sdk.auth.AuthResponse;
import com.tiktok.open.sdk.auth.utils.PKCEUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Official TikTok integration inside NM7:
 * - Login Kit via the SDK's Chrome Custom Tab flow (does not require the TikTok app installed).
 * - A native vertical pager for the authorized user's recent public videos returned by Display API.
 * - Each page uses TikTok's official individual-post player, not a fake For You interface.
 *
 * TikTok OpenSDK does not provide the complete For You application feed to third-party apps.
 */
public final class TikTokIntegrationActivity extends Activity {
    private static final String PREFS = "nm7_tiktok_oauth";
    private static final String KEY_VERIFIER = "pkce_verifier";
    private static final String KEY_STATE = "oauth_state";
    private static final String SCOPE = "user.info.basic,video.list";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient();
    private final List<VideoItem> videos = new ArrayList<>();
    private LinearLayout root;
    private TextView status;
    private TextView profile;
    private Button connectButton;
    private Button reloadButton;
    private ProgressBar progress;
    private ViewPager2 pager;
    private VideoAdapter adapter;
    private String sessionToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 15, 15));
        getWindow().setNavigationBarColor(Color.rgb(15, 15, 15));
        buildUi();
        sessionToken = TikTokSessionStore.read(this);
        updateConfigurationUi();
        handleAuthResponse(getIntent());
        if (sessionToken != null && !sessionToken.isEmpty()) loadVideos();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAuthResponse(intent);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 15, 15));
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView title = new TextView(this);
        title.setText("TikTok trong NM7");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Tích hợp chính thức bằng TikTok Login Kit và Display API. Tab Video của tôi hiển thị video công khai gần đây của tài khoản đã kết nối; đây không phải feed For You của TikTok.");
        subtitle.setTextColor(0xffc6c6c6);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(6);
        root.addView(subtitle, subLp);

        profile = new TextView(this);
        profile.setTextColor(0xffeeeeee);
        profile.setTextSize(14);
        profile.setText("Chưa kết nối tài khoản TikTok.");
        LinearLayout.LayoutParams profileLp = matchWrap();
        profileLp.topMargin = dp(8);
        root.addView(profile, profileLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        connectButton = new Button(this);
        connectButton.setText("Kết nối TikTok");
        connectButton.setOnClickListener(v -> authorize());
        buttons.addView(connectButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        reloadButton = new Button(this);
        reloadButton.setText("Tải video");
        reloadButton.setOnClickListener(v -> loadVideos());
        buttons.addView(reloadButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams buttonsLp = matchWrap();
        buttonsLp.topMargin = dp(4);
        root.addView(buttons, buttonsLp);

        status = new TextView(this);
        status.setTextColor(0xffd5d5d5);
        status.setTextSize(12);
        status.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.topMargin = dp(2);
        root.addView(status, statusLp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = matchWrap();
        progressLp.topMargin = dp(4);
        root.addView(progress, progressLp);

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        adapter = new VideoAdapter();
        pager.setAdapter(adapter);
        LinearLayout.LayoutParams pagerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        pagerLp.topMargin = dp(8);
        root.addView(pager, pagerLp);

        TextView disclaimer = new TextView(this);
        disclaimer.setText("TikTok giới hạn quyền API theo phê duyệt ứng dụng. NM7 không lấy mật khẩu TikTok; token phiên được mã hóa bằng Android Keystore.");
        disclaimer.setTextColor(0xff888888);
        disclaimer.setTextSize(11);
        LinearLayout.LayoutParams disclaimerLp = matchWrap();
        disclaimerLp.topMargin = dp(4);
        root.addView(disclaimer, disclaimerLp);

        setContentView(root);
    }

    private void updateConfigurationUi() {
        String clientKey = BuildConfig.TIKTOK_CLIENT_KEY;
        String apiBase = BuildConfig.TIKTOK_API_BASE_URL;
        boolean hasClientKey = clientKey != null && !clientKey.trim().isEmpty();
        boolean hasApiBase = apiBase != null && !apiBase.trim().isEmpty();
        connectButton.setEnabled(true);
        if (!hasClientKey) {
            status.setText("Chưa cấu hình TIKTOK_CLIENT_KEY. Hãy đăng ký ứng dụng NM7 trong TikTok Developer Portal rồi build với -PtiktokClientKey=<client_key>.");
        } else if (!hasApiBase) {
            status.setText("Client Key đã có, nhưng chưa cấu hình TIKTOK_API_BASE_URL cho backend đổi OAuth code an toàn và gọi Display API. Không đặt Client Secret trong APK.");
        } else {
            status.setText("OpenSDK sẵn sàng. Kết nối tài khoản để xem video công khai gần đây.");
        }
    }

    private void authorize() {
        String clientKey = BuildConfig.TIKTOK_CLIENT_KEY == null ? "" : BuildConfig.TIKTOK_CLIENT_KEY.trim();
        if (clientKey.isEmpty()) {
            showMessage("Cần cấu hình TikTok Developer",
                    "NM7 đã tích hợp TikTok OpenSDK chính thức, nhưng cần Client Key thuộc ứng dụng đã đăng ký và được TikTok cấp quyền. Client Secret không được nhúng vào APK.");
            return;
        }
        if (BuildConfig.TIKTOK_API_BASE_URL == null || BuildConfig.TIKTOK_API_BASE_URL.trim().isEmpty()) {
            showMessage("Thiếu backend OAuth",
                    "Để không lộ Client Secret, NM7 cần một Cloudflare Worker thực hiện đổi authorization code và gọi Display API. Hãy triển khai workers/tiktok-open-auth rồi build với -PtiktokApiBaseUrl=https://<worker-domain>.");
            return;
        }
        String verifier = PKCEUtils.INSTANCE.generateCodeVerifier();
        String state = UUID.randomUUID().toString();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_VERIFIER, verifier)
                .putString(KEY_STATE, state)
                .apply();
        AuthRequest request = new AuthRequest(
                clientKey,
                SCOPE,
                BuildConfig.TIKTOK_REDIRECT_URI,
                verifier,
                false,
                state,
                "vi-VN"
        );
        boolean started;
        try {
            started = new AuthApi(this).authorize(request, AuthApi.AuthMethod.ChromeTab);
        } catch (Exception error) {
            started = false;
            status.setText("Không khởi chạy được Login Kit: " + safe(error.getMessage()));
        }
        if (!started) {
            status.setText("TikTok Login Kit không khởi chạy được. Kiểm tra Client Key, Redirect URI và cấu hình ứng dụng TikTok Developer.");
        } else {
            status.setText("Đã mở bước xác thực OAuth an toàn. Hoàn tất xác thực rồi quay lại NM7; không cần cài TikTok để dùng Chrome Tab.");
        }
    }

    private void handleAuthResponse(@Nullable Intent intent) {
        if (intent == null || !intent.hasExtra("tiktok_callback")) {
            // The SDK callback may arrive as an HTTPS app-link URI without extras.
            if (intent == null || intent.getData() == null
                    || !intent.getData().toString().startsWith(BuildConfig.TIKTOK_REDIRECT_URI)) return;
        }
        AuthResponse response;
        try {
            response = new AuthApi(this).getAuthResponseFromIntent(intent, BuildConfig.TIKTOK_REDIRECT_URI);
        } catch (Exception error) {
            return;
        }
        if (response == null) return;
        String savedState = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATE, "");
        String savedVerifier = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_VERIFIER, "");
        if (savedState == null || savedState.isEmpty()
                || response.getState() == null || !savedState.equals(response.getState())) {
            status.setText("Từ chối callback OAuth vì state không khớp.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_STATE).apply();
        if (response.getErrorCode() != 0 || response.getAuthCode() == null || response.getAuthCode().isEmpty()) {
            status.setText("TikTok Login Kit trả về lỗi: "
                    + response.getErrorCode() + " " + safe(response.getErrorMsg()));
            return;
        }
        exchangeCode(response.getAuthCode(), savedVerifier, response.getState());
    }

    private void exchangeCode(String code, String verifier, String state) {
        JSONObject body = new JSONObject();
        try {
            body.put("code", code);
            body.put("code_verifier", verifier);
            body.put("state", state);
            body.put("redirect_uri", BuildConfig.TIKTOK_REDIRECT_URI);
        } catch (Exception error) {
            status.setText("Không tạo được yêu cầu đổi OAuth code.");
            return;
        }
        String url = trimSlash(BuildConfig.TIKTOK_API_BASE_URL) + "/api/tiktok/mobile/exchange";
        setLoading(true, "Đang hoàn tất kết nối tài khoản TikTok…");
        Request request = new Request.Builder().url(url).post(RequestBody.create(body.toString(), JSON)).build();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException error) {
                runOnUiThread(() -> {
                    setLoading(false, "Không liên lạc được backend OAuth: " + safe(error.getMessage()));
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String text = response.body() == null ? "" : response.body().string();
                JSONObject json;
                try { json = new JSONObject(text); }
                catch (Exception parseError) {
                    runOnUiThread(() -> setLoading(false, "Backend OAuth trả phản hồi không hợp lệ (HTTP " + response.code() + ")."));
                    return;
                }
                if (!response.isSuccessful() || !json.optBoolean("ok", false)) {
                    String errorText = json.optString("error", "HTTP " + response.code());
                    runOnUiThread(() -> setLoading(false, "Không hoàn tất được OAuth: " + errorText));
                    return;
                }
                String token = json.optString("session_token", "");
                JSONObject user = json.optJSONObject("profile");
                try {
                    TikTokSessionStore.save(TikTokIntegrationActivity.this, token);
                } catch (Exception error) {
                    runOnUiThread(() -> setLoading(false, "Không lưu được token phiên bằng Android Keystore."));
                    return;
                }
                sessionToken = token;
                String displayName = user == null ? "" : user.optString("display_name", "");
                runOnUiThread(() -> {
                    profile.setText(displayName.isEmpty() ? "Đã kết nối tài khoản TikTok." : "Đã kết nối: @" + displayName);
                    setLoading(false, "Đăng nhập thành công. Đang tải video công khai gần đây…");
                    loadVideos();
                });
            }
        });
    }

    private void loadVideos() {
        String token = sessionToken;
        if (token == null || token.isEmpty()) {
            status.setText("Hãy kết nối tài khoản TikTok trước để lấy video công khai của chính tài khoản đó.");
            return;
        }
        String base = BuildConfig.TIKTOK_API_BASE_URL == null ? "" : BuildConfig.TIKTOK_API_BASE_URL.trim();
        if (base.isEmpty()) {
            status.setText("Chưa cấu hình backend Display API.");
            return;
        }
        setLoading(true, "Đang tải video công khai…");
        Request request = new Request.Builder()
                .url(trimSlash(base) + "/api/tiktok/mobile/videos")
                .header("Authorization", "Bearer " + token)
                .get().build();
        http.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException error) {
                runOnUiThread(() -> setLoading(false, "Không tải được video: " + safe(error.getMessage())));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String text = response.body() == null ? "" : response.body().string();
                try {
                    JSONObject json = new JSONObject(text);
                    if (!response.isSuccessful() || !json.optBoolean("ok", false)) {
                        String message = json.optString("error", "HTTP " + response.code());
                        runOnUiThread(() -> setLoading(false, "Display API: " + message));
                        return;
                    }
                    String refreshed = json.optString("session_token", "");
                    if (!refreshed.isEmpty() && !refreshed.equals(token)) {
                        TikTokSessionStore.save(TikTokIntegrationActivity.this, refreshed);
                        sessionToken = refreshed;
                    }
                    JSONArray array = json.optJSONArray("videos");
                    List<VideoItem> parsed = new ArrayList<>();
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.optJSONObject(i);
                            if (item == null) continue;
                            String id = item.optString("id", "");
                            if (id.isEmpty()) continue;
                            parsed.add(new VideoItem(
                                    id,
                                    item.optString("title", ""),
                                    item.optString("description", ""),
                                    item.optString("share_url", ""),
                                    item.optString("embed_link", "")
                            ));
                        }
                    }
                    runOnUiThread(() -> {
                        videos.clear();
                        videos.addAll(parsed);
                        adapter.notifyDataSetChanged();
                        pager.setCurrentItem(0, false);
                        profile.setText(json.optJSONObject("profile") == null
                                ? "Video công khai gần đây"
                                : "@" + json.optJSONObject("profile").optString("display_name", "TikTok"));
                        setLoading(false, videos.isEmpty()
                                ? "Tài khoản không có video công khai được Display API trả về. Kiểm tra quyền video.list đã được TikTok phê duyệt."
                                : "Đã tải " + videos.size() + " video. Vuốt dọc để chuyển video.");
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> setLoading(false, "Phản hồi Display API không hợp lệ."));
                }
            }
        });
    }

    private void setLoading(boolean loading, String message) {
        runOnUiThread(() -> {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            status.setText(message);
            connectButton.setEnabled(!loading);
            reloadButton.setEnabled(!loading);
        });
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String trimSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Không rõ nguyên nhân" : value;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class VideoAdapter extends RecyclerView.Adapter<VideoHolder> {
        @NonNull @Override public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(TikTokIntegrationActivity.this);
            frame.setBackgroundColor(Color.BLACK);
            return new VideoHolder(frame);
        }

        @Override public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
            VideoItem item = videos.get(position);
            holder.bind(item);
        }

        @Override public void onViewRecycled(@NonNull VideoHolder holder) {
            holder.destroy();
            super.onViewRecycled(holder);
        }

        @Override public int getItemCount() { return videos.size(); }
    }

    private final class VideoHolder extends RecyclerView.ViewHolder {
        private final FrameLayout frame;
        private WebView player;
        private float downX;
        private float downY;

        VideoHolder(FrameLayout itemView) {
            super(itemView);
            frame = itemView;
        }

        void bind(VideoItem video) {
            destroy();
            frame.removeAllViews();
            player = new WebView(TikTokIntegrationActivity.this);
            WebSettings settings = player.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            player.setBackgroundColor(Color.BLACK);
            player.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    Uri uri = Uri.parse(url);
                    String host = uri.getHost();
                    // Keep player navigation in NM7 and only allow TikTok-owned web pages.
                    if (host != null && (host.equals("tiktok.com") || host.endsWith(".tiktok.com"))) return false;
                    return true;
                }
            });
            player.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dy > dp(12) && dy > dx * 1.2f && view.getParent() != null) {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                return false;
            });
            frame.addView(player, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            String url = video.embedLink;
            if (url == null || url.isEmpty() || !TikTokWebPolicy.isTikTokUrl(url)) {
                url = "https://www.tiktok.com/player/v1/" + Uri.encode(video.id)
                        + "?controls=1&description=1&music_info=1&autoplay=1";
            }
            player.loadUrl(url);

            TextView caption = new TextView(TikTokIntegrationActivity.this);
            caption.setText(TextUtils.isEmpty(video.title) ? video.description : video.title);
            caption.setTextColor(Color.WHITE);
            caption.setTextSize(14);
            caption.setMaxLines(3);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            caption.setPadding(dp(12), dp(8), dp(12), dp(12));
            caption.setBackgroundColor(0x66000000);
            FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            frame.addView(caption, captionLp);
        }

        void destroy() {
            if (player != null) {
                player.stopLoading();
                player.setWebChromeClient(null);
                player.setWebViewClient(null);
                if (player.getParent() instanceof ViewGroup) ((ViewGroup) player.getParent()).removeView(player);
                player.removeAllViews();
                player.destroy();
                player = null;
            }
        }
    }

    private static final class VideoItem {
        final String id;
        final String title;
        final String description;
        final String shareUrl;
        final String embedLink;

        VideoItem(String id, String title, String description, String shareUrl, String embedLink) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.shareUrl = shareUrl;
            this.embedLink = embedLink;
        }
    }

    @Override
    protected void onDestroy() {
        if (pager != null) pager.setAdapter(null);
        super.onDestroy();
    }
}
