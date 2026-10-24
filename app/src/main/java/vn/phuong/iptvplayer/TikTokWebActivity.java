package vn.phuong.iptvplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens TikTok's real first-party mobile website inside the NM7 application.
 * TikTok does not expose its full For You interface as an official embeddable iframe,
 * so this Activity hosts the real TikTok origin instead of faking feed/swipe controls.
 */
public final class TikTokWebActivity extends Activity {
    private static final int REQUEST_FILE_CHOOSER = 7101;
    private static final int REQUEST_WEB_MEDIA_PERMISSION = 7102;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private ValueCallback<Uri[]> pendingFileChooser;
    private PermissionRequest pendingWebPermission;
    private String[] pendingWebResources = new String[0];
    private String[] pendingAndroidPermissions = new String[0];
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private int previousSystemUiVisibility;
    private int previousRequestedOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        // This Activity opts into explicit insets handling. Target SDK 36 uses
        // edge-to-edge by default; padding the content by real system insets prevents
        // TikTok's own top navigation from being drawn underneath the Android status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat systemBars =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        systemBars.setAppearanceLightStatusBars(false);
        systemBars.setAppearanceLightNavigationBars(false);

        buildContent();
        configureWebView();
        if (savedInstanceState != null && savedInstanceState.containsKey("tiktok_url")) {
            String restored = savedInstanceState.getString("tiktok_url", TikTokWebPolicy.START_URL);
            webView.loadUrl(TikTokWebPolicy.isWebUrl(restored) ? restored : TikTokWebPolicy.START_URL);
        } else {
            webView.loadUrl(TikTokWebPolicy.START_URL);
        }
    }

    private void buildContent() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipToPadding(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        root.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(28), dp(24), dp(28), dp(24));
        errorPanel.setBackgroundColor(Color.BLACK);
        errorPanel.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("Không tải được TikTok");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        errorPanel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(this);
        description.setText("Kiểm tra kết nối mạng rồi thử tải lại. Một số tính năng đăng nhập có thể bị giới hạn bởi chính sách trình duyệt nhúng của TikTok.");
        description.setTextColor(0xffc7c7c7);
        description.setTextSize(14);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(12);
        errorPanel.addView(description, descriptionParams);

        Button retry = new Button(this);
        retry.setText("Thử lại");
        retry.setOnClickListener(v -> {
            errorPanel.setVisibility(View.GONE);
            webView.loadUrl(TikTokWebPolicy.START_URL);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = dp(16);
        errorPanel.addView(retry, retryParams);

        root.addView(errorPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        // Do not let target=_blank create a second browser/application surface.
        // All standard links are redirected into this WebView by WebViewClient.
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        // TikTok is a mobile-first feed. Avoid wide desktop layout viewports and page
        // overview scaling, which made the original UI look stretched on phones.
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setTextZoom(100);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        // TikTok's mobile website sometimes treats Android WebView as an app-launch
        // handoff. Use the same mobile browser UA without Android's "; wv" marker or
        // the legacy "Version/4.0" token so the feed stays in the embedded web surface.
        String mobileUa = TikTokWebPolicy.normalizeMobileUserAgent(settings.getUserAgentString());
        if (!mobileUa.isEmpty()) settings.setUserAgentString(mobileUa);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                errorPanel.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(4);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setProgress(100);
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) showLoadError();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(view.getUrl())) showLoadError();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse response) {
                if (request != null && request.isForMainFrame()
                        && response != null && response.getStatusCode() >= 400) showLoadError();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Never bypass TLS validation for login or account cookies.
                handler.cancel();
                showLoadError();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(Math.max(0, Math.min(100, newProgress)));
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = callback;
                try {
                    Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.setType("*/*");
                    picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                    startActivityForResult(Intent.createChooser(picker, "Chọn ảnh hoặc video"), REQUEST_FILE_CHOOSER);
                    return true;
                } catch (RuntimeException error) {
                    pendingFileChooser = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(TikTokWebActivity.this, "Không mở được bộ chọn ảnh/video.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showWebFullscreen(view, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, callback);
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
                showWebFullscreen(view, requestedOrientation, callback);
            }

            @Override
            public void onHideCustomView() {
                hideWebFullscreen();
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> requestWebMediaPermission(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) clearPendingWebPermission();
            }
        });
    }

    /** Keep normal web and authentication pages inside this one NM7 Activity. */
    private boolean handleNavigation(String rawUrl) {
        // Only HTTP(S) navigations are ever allowed to continue in WebView. No
        // ACTION_VIEW / package-manager resolution is used here, so tiktok://,
        // intent://, market:// and other app schemes cannot launch external apps.
        if (TikTokWebPolicy.isWebUrl(rawUrl)) return false;

        if (rawUrl != null && rawUrl.regionMatches(true, 0, "intent:", 0, 7)) {
            try {
                Intent parsed = Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME);
                String fallback = parsed.getStringExtra("browser_fallback_url");
                if (TikTokWebPolicy.isWebUrl(fallback)) {
                    webView.loadUrl(fallback);
                    return true;
                }
            } catch (Exception ignored) { }
        }

        // Custom schemes should be consumed, never passed to Android's intent resolver.
        android.util.Log.i("NM7TikTok", "Blocked non-web navigation: "
                + (rawUrl == null ? "(null)" : Uri.parse(rawUrl).getScheme()));
        return true;
    }

    private void showWebFullscreen(View view, int requestedOrientation,
                                    WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        previousSystemUiVisibility = root.getSystemUiVisibility();
        previousRequestedOrientation = getRequestedOrientation();
        progress.setVisibility(View.GONE);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        root.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ViewCompat.requestApplyInsets(root);
        if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            setRequestedOrientation(requestedOrientation);
        }
    }

    private void hideWebFullscreen() {
        if (fullscreenView == null) return;
        View oldView = fullscreenView;
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenView = null;
        fullscreenCallback = null;
        root.removeView(oldView);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        root.setSystemUiVisibility(previousSystemUiVisibility);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
        controller.show(WindowInsetsCompat.Type.systemBars());
        ViewCompat.requestApplyInsets(root);
        setRequestedOrientation(previousRequestedOrientation);
        if (callback != null) callback.onCustomViewHidden();
    }

    private void requestWebMediaPermission(PermissionRequest request) {
        if (isFinishing() || isDestroyed()) {
            request.deny();
            return;
        }
        List<String> androidPermissions = new ArrayList<>();
        List<String> webResources = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                webResources.add(resource);
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    androidPermissions.add(Manifest.permission.CAMERA);
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                webResources.add(resource);
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    androidPermissions.add(Manifest.permission.RECORD_AUDIO);
                }
            }
        }
        if (webResources.isEmpty()) {
            request.deny();
            return;
        }
        if (androidPermissions.isEmpty()) {
            request.grant(webResources.toArray(new String[0]));
            return;
        }
        if (pendingWebPermission != null) pendingWebPermission.deny();
        pendingWebPermission = request;
        pendingWebResources = webResources.toArray(new String[0]);
        pendingAndroidPermissions = androidPermissions.toArray(new String[0]);
        requestPermissions(pendingAndroidPermissions, REQUEST_WEB_MEDIA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_WEB_MEDIA_PERMISSION || pendingWebPermission == null) return;
        boolean granted = true;
        for (int i = 0; i < pendingAndroidPermissions.length; i++) {
            if (i >= grantResults.length || grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        PermissionRequest request = pendingWebPermission;
        String[] resources = pendingWebResources;
        clearPendingWebPermission();
        if (granted) request.grant(resources);
        else request.deny();
    }

    private void clearPendingWebPermission() {
        pendingWebPermission = null;
        pendingWebResources = new String[0];
        pendingAndroidPermissions = new String[0];
    }

    private void showLoadError() {
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null && webView.getUrl() != null && TikTokWebPolicy.isWebUrl(webView.getUrl())) {
            outState.putString("tiktok_url", webView.getUrl());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE_CHOOSER || pendingFileChooser == null) return;
        Uri[] result = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        pendingFileChooser.onReceiveValue(result);
        pendingFileChooser = null;
    }

    @Override
    public void onBackPressed() {
        if (fullscreenView != null) {
            hideWebFullscreen();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (fullscreenView != null) hideWebFullscreen();
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (pendingWebPermission != null) {
            pendingWebPermission.deny();
            clearPendingWebPermission();
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            if (root != null) root.removeView(webView);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        webView.pauseTimers();
        super.onDestroy();
    }
}
