package vn.phuong.iptvplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Single Mobile navigation bar, overlaid at the bottom of NM7, player, and SmartTube Browse. */
public final class HomeTabBar {
    private static final int TAG_KEY = R.id.mainRoot;
    private static final String BAR_TAG = "nm7_home_tab_bar";
    private static final int BG = Color.rgb(23, 23, 28);
    private static final int SELECTED = Color.rgb(255, 122, 0);
    private static final int UNSELECTED = Color.rgb(135, 137, 145);
    private static final String BROWSE = "com.liskovsoft.smartyoutubetv2.droid.ui.browse.BrowseActivity";

    private HomeTabBar() {}

    public static void attach(Activity activity, boolean youtubeSelected) {
        attachInternal(activity, youtubeSelected ? 0 : 1);
    }

    /** Attach the same navigation bar with the in-app news reader selected. */
    public static void attachReader(Activity activity) {
        attachInternal(activity, 2);
    }

    private static void attachInternal(Activity activity, int selectedTab) {
        boolean youtubeSelected = selectedTab == 0;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        FrameLayout host = (FrameLayout) content;
        if (host.getTag(TAG_KEY) != null) return;
        host.setTag(TAG_KEY, Boolean.TRUE);

        LinearLayout bar = new LinearLayout(activity);
        bar.setTag(BAR_TAG);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(new ColorDrawable(youtubeSelected ? Color.WHITE : BG));
        bar.setElevation(dp(activity, 8));
        bar.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        addItem(activity, bar, R.drawable.nm7_nav_youtube, selectedTab == 0, "YouTube", () -> {
            if (selectedTab != 0) openBrowse(activity);
        });

        addItem(activity, bar, R.drawable.nm7_nav_iptv, selectedTab == 1, "IPTV", () -> {
            if (selectedTab != 1) openIptv(activity);
        });

        addItem(activity, bar, R.drawable.nm7_nav_news, selectedTab == 2, "Đọc báo", () -> {
            if (selectedTab != 2) openNews(activity);
        });

        // Preserve the existing settings destination from source commit 4fc8b187.
        addItem(activity, bar, R.drawable.nm7_nav_settings, selectedTab == 3, "Tùy chọn",
                () -> openAppSettings(activity));

        final int barHeight = dp(activity, 64);
        final int listReserve = dp(activity, 76);
        final View mainRoot = activity instanceof MainActivity
                ? activity.findViewById(R.id.mainRoot) : null;
        final int mainRootBaseBottom = mainRoot == null ? 0 : mainRoot.getPaddingBottom();
        final android.widget.ListView channelList = activity instanceof MainActivity
                ? activity.findViewById(R.id.listChannels) instanceof android.widget.ListView
                    ? (android.widget.ListView) activity.findViewById(R.id.listChannels) : null
                : null;
        final int channelListBaseBottom = channelList == null ? 0 : channelList.getPaddingBottom();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barHeight, Gravity.BOTTOM);
        host.addView(bar, lp);
        // Only layout/inset handling is extended for Reader; the original YouTube/IPTV
        // activity-transition methods below remain from the exact 4fc8b187 source.
        bar.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            ViewGroup.LayoutParams raw = view.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams barParams = (FrameLayout.LayoutParams) raw;
                if (barParams.bottomMargin != bottomInset) {
                    barParams.bottomMargin = bottomInset;
                    view.setLayoutParams(barParams);
                }
            }
            if (mainRoot != null) {
                mainRoot.setPadding(mainRoot.getPaddingLeft(), mainRoot.getPaddingTop(),
                        mainRoot.getPaddingRight(), mainRootBaseBottom + barHeight + bottomInset);
            }
            if (channelList != null) {
                channelList.setClipToPadding(false);
                channelList.setPadding(channelList.getPaddingLeft(), channelList.getPaddingTop(),
                        channelList.getPaddingRight(), channelListBaseBottom + listReserve + bottomInset);
            }
            return insets;
        });
        bar.requestApplyInsets();
    }

    public static void setVisible(Activity activity, boolean visible) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) content;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (BAR_TAG.equals(child.getTag())) {
                child.setVisibility(visible ? View.VISIBLE : View.GONE);
                return;
            }
        }
    }

    private static void openBrowseSection(Activity activity, String method) {
        try {
            activity.getClass().getMethod(method).invoke(activity);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Navigation", "Browse section unavailable", error);
        }
    }

    private static void startWithoutAnimation(Activity activity, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    private static void openIptv(Activity activity) {
        // Switching tabs does not end YouTube. It remains paused/backgrounded until
        // an IPTV player actually reaches READY; the existing handoff logic then
        // temporarily suspends YouTube. If IPTV has no active player, YouTube remains
        // available in the background for instant return to the previous session.
        PlayerActivity.cancelYoutubeHandoff();
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);
        MobileNm7Application.markTabSwitch();
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
        // Explicitly request IPTV restoration after the Activity transition. This also
        // covers the case where MainActivity is already alive and only reordered to front.
        // The click originates from SmartTube Browse/Playback, not MainActivity.
        // Resolve the already-running MainActivity inside the IPTV provider.
        MobileInlinePlayerProviderV2.resumeForIptvTab(activity);
        activity.getWindow().getDecorView().post(
                () -> MobileInlinePlayerProviderV2.resumeForIptvTab(activity));
    }

    private static void openNews(Activity activity) {
        Intent intent = new Intent(activity, NewsReaderActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
    }

    private static void openAppSettings(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showSettingsFromNavigation();
            return;
        }
        // Settings are hosted by the existing MainActivity. Return to that host with
        // the same no-animation handoff used by the existing IPTV navigation.
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_IPTV);
        MobileNm7Application.markTabSwitch();
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_APP_SETTINGS, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startWithoutAnimation(activity, intent);
    }

    private static void openBrowse(Activity activity) {
        SharedPlaybackSession.setTab(activity, SharedPlaybackSession.TAB_YOUTUBE);
        MobileNm7Application.markTabSwitch();
        // If a YouTube playback session already exists, return directly to its real
        // PlaybackActivity. This keeps the actual video surface visible; do not send the
        // user to Browse while the decoder is still playing invisibly in the background.
        // If no YouTube session exists, open Browse and leave IPTV untouched until a
        // YouTube video actually reaches READY.
        try {
            Class<?> playback = Class.forName(
                    "com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity");
            boolean active = (Boolean) playback.getMethod("isNm7SessionActive").invoke(null);
            if (active) {
                MobileInlinePlayerProviderV2.releaseForYoutube();
                SmartTubeRuntime.initialize(activity.getApplicationContext());
                boolean isMini = false;
                try {
                    isMini = Boolean.TRUE.equals(playback.getMethod("isNm7MiniPlayerActive").invoke(null));
                } catch (ReflectiveOperationException ignored) {}
                if (!isMini) {
                    playback.getMethod("restoreNm7Player").invoke(null);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("NM7Navigation", "Existing YouTube session restore failed", error);
        }

        PlayerActivity.prepareForYoutubeHandoff(activity);
        SmartTubeRuntime.initialize(activity.getApplicationContext());
        try {
            Class<?> browse = Class.forName(BROWSE);
            Intent intent = new Intent(activity, browse);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startWithoutAnimation(activity, intent);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            Intent fallback = new Intent(activity, SmartTubeHomeActivity.class);
            fallback.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startWithoutAnimation(activity, fallback);
        }
    }

    private static void addItem(Activity activity, LinearLayout bar, int iconRes, boolean selected,
                                 String title, Runnable action) {
        LinearLayout item = new LinearLayout(activity);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> action.run());

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        boolean light = activity.getClass().getName().equals(BROWSE);
        icon.setColorFilter(light ? (selected ? Color.BLACK : 0xff606060) : (selected ? SELECTED : UNSELECTED));
        icon.setContentDescription(null);
        int size = dp(activity, 30);
        item.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView label = new TextView(activity);
        label.setText(title);
        item.setContentDescription(title);
        label.setTextSize(11);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(light ? (selected ? Color.BLACK : 0xff606060) : (selected ? Color.WHITE : UNSELECTED));
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        item.addView(label, new LinearLayout.LayoutParams(-2, dp(activity, 18)));

        bar.addView(item, new LinearLayout.LayoutParams(0, dp(activity, 56), 1f));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
