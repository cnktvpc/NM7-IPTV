package vn.phuong.iptvplayer;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * YouTube-style docked miniplayer bar, sitting right above the bottom navigation bar.
 * Supports tap-to-expand, horizontal swipe-to-dismiss, play/pause toggle, and close.
 */
public final class MobileMiniPlayer {
    private static FrameLayout host;
    private static View surface;
    private static final String PLAYER = "com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity";

    private MobileMiniPlayer() { }

    public static void attach(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Class<?> bridge = Class.forName(PLAYER);
            if (!Boolean.TRUE.equals(bridge.getMethod("isNm7MiniPlayerActive").invoke(null))) {
                remove();
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (!(root instanceof FrameLayout)) return;
            if (host != null && host.getParent() == root) return;

            float d = activity.getResources().getDisplayMetrics().density;
            final FrameLayout next = new FrameLayout(activity);

            // YouTube-style floating dock pill: rounded corners, dark theme surface
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(0xFF212128);
            cardBg.setCornerRadius(12 * d);
            next.setBackground(cardBg);
            next.setElevation(8 * d);
            next.setClipToOutline(true);

            int barHeight = (int) (60 * d);
            int bottomMargin = (int) (68 * d); // Just above 64dp bottom navigation
            int horizontalMargin = (int) (8 * d);

            FrameLayout.LayoutParams box = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, barHeight, Gravity.BOTTOM);
            box.bottomMargin = bottomMargin;
            box.leftMargin = horizontalMargin;
            box.rightMargin = horizontalMargin;

            // Horizontal content container: Video (Left) + Text (Center) + Controls (Right)
            LinearLayout contentLayout = new LinearLayout(activity);
            contentLayout.setOrientation(LinearLayout.HORIZONTAL);
            contentLayout.setGravity(Gravity.CENTER_VERTICAL);
            next.addView(contentLayout, new FrameLayout.LayoutParams(-1, -1));

            // 1. Left: 16:9 Video surface box
            int videoWidth = (int) (96 * d);
            FrameLayout videoBox = new FrameLayout(activity);
            videoBox.setBackgroundColor(0xFF000000);
            View video = createPlayerView(activity);
            video.setClickable(false);
            video.setFocusable(false);
            videoBox.addView(video, new FrameLayout.LayoutParams(-1, -1));

            // Small buffering indicator inside video box
            ProgressBar buffering = new ProgressBar(activity);
            buffering.setIndeterminate(true);
            buffering.setVisibility(View.GONE);
            videoBox.addView(buffering, new FrameLayout.LayoutParams((int) (24 * d), (int) (24 * d), Gravity.CENTER));

            contentLayout.addView(videoBox, new LinearLayout.LayoutParams(videoWidth, ViewGroup.LayoutParams.MATCH_PARENT));

            // 2. Center: Video Title & Channel / Subtitle
            LinearLayout textLayout = new LinearLayout(activity);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setGravity(Gravity.CENTER_VERTICAL);
            textLayout.setPadding((int) (10 * d), 0, (int) (4 * d), 0);

            TextView titleView = new TextView(activity);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(13);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setText("YouTube");
            textLayout.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

            TextView authorView = new TextView(activity);
            authorView.setTextColor(0xFFB0B0B0);
            authorView.setTextSize(11);
            authorView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            authorView.setSingleLine(true);
            authorView.setEllipsize(TextUtils.TruncateAt.END);
            authorView.setText("");
            textLayout.addView(authorView, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            contentLayout.addView(textLayout, textLp);

            // 3. Right: Play/Pause button and Close button
            ImageButton playPause = new ImageButton(activity);
            playPause.setBackgroundColor(0x00000000);
            playPause.setColorFilter(Color.WHITE);
            playPause.setImageResource(android.R.drawable.ic_media_pause);
            playPause.setContentDescription("Tạm dừng");
            contentLayout.addView(playPause, new LinearLayout.LayoutParams((int) (44 * d), (int) (44 * d)));

            ImageButton close = new ImageButton(activity);
            close.setBackgroundColor(0x00000000);
            close.setColorFilter(Color.WHITE);
            close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            close.setContentDescription("Đóng");
            contentLayout.addView(close, new LinearLayout.LayoutParams((int) (44 * d), (int) (44 * d)));

            // 4. Bottom edge: thin YouTube red playback progress bar
            ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(false);
            progress.setMax(1000);
            progress.setProgress(0);
            progress.setProgressTintList(ColorStateList.valueOf(0xFFFF0033));
            FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(-1, (int) (2.5f * d), Gravity.BOTTOM);
            next.addView(progress, progressLp);

            // Tap Shield for restoring fullscreen or horizontal swipe-to-dismiss
            View tapShield = new View(activity);
            tapShield.setClickable(true);
            tapShield.setFocusable(true);
            FrameLayout.LayoutParams shieldLp = new FrameLayout.LayoutParams(
                    videoWidth + (int)(2000 * d), ViewGroup.LayoutParams.MATCH_PARENT);
            // Place shield over video + text, leaving controls clickable
            LinearLayout.LayoutParams textShieldLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            
            tapShield.setOnClickListener(v -> {
                try {
                    bridge.getMethod("restoreNm7Player").invoke(null);
                } catch (ReflectiveOperationException error) {
                    android.util.Log.e("NM7Playback", "Restore mini", error);
                }
            });

            // Swipe horizontal to dismiss, tap to expand
            tapShield.setOnTouchListener(new View.OnTouchListener() {
                float startRawX, startRawY;
                float startTranslationX;
                boolean isDragging;
                final float slop = ViewConfiguration.get(activity).getScaledTouchSlop();

                @Override public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startRawX = event.getRawX();
                            startRawY = event.getRawY();
                            startTranslationX = next.getTranslationX();
                            isDragging = false;
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - startRawX;
                            float dy = event.getRawY() - startRawY;
                            if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                next.setTranslationX(startTranslationX + dx);
                                float alpha = Math.max(0.2f, 1.0f - (Math.abs(dx) / (root.getWidth() * 0.6f)));
                                next.setAlpha(alpha);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (isDragging) {
                                float totalDx = event.getRawX() - startRawX;
                                if (Math.abs(totalDx) > 90 * d) {
                                    // Dismiss miniplayer smoothly
                                    float targetX = totalDx > 0 ? root.getWidth() : -root.getWidth();
                                    next.animate().translationX(targetX).alpha(0f).setDuration(180)
                                            .withEndAction(MobileNm7Application::stopYoutubeForIptv)
                                            .start();
                                } else {
                                    next.animate().translationX(0f).alpha(1f).setDuration(150).start();
                                }
                            } else {
                                v.performClick();
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            next.animate().translationX(0f).alpha(1f).setDuration(150).start();
                            return true;

                        default:
                            return false;
                    }
                }
            });

            // Put tap shield over text area and video area
            FrameLayout touchContainer = new FrameLayout(activity);
            touchContainer.addView(tapShield, new FrameLayout.LayoutParams(-1, -1));
            // Add right above the content layout but behind playPause & close
            FrameLayout.LayoutParams touchLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            touchLp.rightMargin = (int) (88 * d); // Do not cover play/pause & close buttons
            next.addView(touchContainer, touchLp);

            // Controls listeners
            playPause.setOnClickListener(v -> {
                try {
                    bridge.getMethod("toggleNm7Playback").invoke(null);
                } catch (ReflectiveOperationException error) {
                    android.util.Log.e("NM7Playback", "Toggle mini playback", error);
                }
            });

            close.setOnClickListener(v -> MobileNm7Application.stopYoutubeForIptv());

            // Polling runnable to keep UI in sync
            Runnable refreshControl = new Runnable() {
                @Override public void run() {
                    if (host != next || !next.isAttachedToWindow()) return;
                    try {
                        int state = ((Number) bridge.getMethod("getNm7PlaybackState").invoke(null)).intValue();
                        buffering.setVisibility(state == 2 ? View.VISIBLE : View.GONE);

                        boolean playing = state != -1 && Boolean.TRUE.equals(bridge.getMethod("isNm7Playing").invoke(null));
                        playPause.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                        playPause.setContentDescription(playing ? "Tạm dừng" : "Phát");

                        // Update metadata
                        try {
                            String title = (String) bridge.getMethod("getNm7VideoTitle").invoke(null);
                            if (!TextUtils.isEmpty(title) && !title.equals(titleView.getText())) {
                                titleView.setText(title);
                            }
                            String author = (String) bridge.getMethod("getNm7VideoAuthor").invoke(null);
                            if (!TextUtils.isEmpty(author) && !author.equals(authorView.getText())) {
                                authorView.setText(author);
                            }
                            long pos = ((Number) bridge.getMethod("getNm7VideoPosition").invoke(null)).longValue();
                            long dur = ((Number) bridge.getMethod("getNm7VideoDuration").invoke(null)).longValue();
                            if (dur > 0 && pos >= 0) {
                                progress.setProgress((int) (pos * 1000 / dur));
                            }
                        } catch (Throwable ignored) { }

                    } catch (ReflectiveOperationException ignored) { }
                    next.postDelayed(this, 500);
                }
            };

            root.addView(next, box);

            final FrameLayout previousHost = host;
            final View previousSurface = surface;
            host = next;
            surface = video;
            next.post(refreshControl);

            // Switch video target view once surface is posted
            video.post(() -> {
                if (host != next || surface != video
                        || activity.isFinishing() || activity.isDestroyed()) return;
                try {
                    bridge.getMethod("attachNm7MiniPlayer", View.class, View.class)
                            .invoke(null, previousSurface, video);
                    if (previousHost != null && previousHost.getParent() instanceof ViewGroup) {
                        ((ViewGroup) previousHost.getParent()).removeView(previousHost);
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    android.util.Log.e("NM7Playback", "Switch mini PlayerView", error);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.e("NM7Playback", "Attach mini", error);
        }
    }

    private static View createPlayerView(Activity activity) throws ReflectiveOperationException {
        return android.view.LayoutInflater.from(activity).inflate(R.layout.nm7_mini_player, null, false);
    }

    public static View surfaceView() {
        return surface;
    }

    public static boolean containsPoint(float rawX, float rawY) {
        if (host == null || !host.isShown()) return false;
        android.graphics.Rect bounds = new android.graphics.Rect();
        return host.getGlobalVisibleRect(bounds) && bounds.contains((int) rawX, (int) rawY);
    }

    public static void remove() {
        if (host != null && host.getParent() instanceof ViewGroup) {
            ((ViewGroup) host.getParent()).removeView(host);
        }
        host = null;
        surface = null;
    }
}
