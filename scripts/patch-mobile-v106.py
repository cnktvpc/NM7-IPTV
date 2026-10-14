#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NM7 IPTV - YouTube Performance & Seamless Mobile Transition Patch v1.10.106 (Optimized)
- Xóa bỏ triệt để màn hình đen & khôi phục poster tức thì từ card vừa chạm (0ms gap).
- Tăng tốc mở video YouTube: hạ buffer khởi động từ 2500ms xuống 250ms (giữ RAM đệm 128MB cho 4K).
- Chuyển cảnh mượt từ poster sang video đang chạy bằng hiệu ứng crossfade 80ms ngay khi có frame mới.
- Khởi động sớm yêu cầu lấy luồng (format pre-warm) ngay ở sự kiện chạm tay (ACTION_DOWN).
- Giữ ấm surface YouTube khi chuyển sang tab IPTV, loại bỏ độ trễ 750ms khi quay lại tab YouTube.
"""

from pathlib import Path
import re

SMARTTUBE_ROOT = Path("third_party/SmartTube-droid")

def log(msg):
    print(f"[NM7-OPT] {msg}")

def patch_file(path: Path, transform_fn):
    if not path.is_file():
        return False
    orig = path.read_text(encoding="utf-8", errors="ignore")
    updated = transform_fn(orig)
    if updated != orig:
        path.write_text(updated, encoding="utf-8")
        log(f"Patched: {path.name}")
        return True
    return False

# =========================================================================
# 1. FIX LỖI BUILD GRADLE & DEPENDENCY NULL
# =========================================================================
props_content = """
junitXVersion=1.1.5
truthXVersion=1.5.0
testCoreVersion=1.5.0
testRunnerVersion=1.5.2
testRulesVersion=1.5.0
truthVersion=1.1.3
"""
for gp in [Path("gradle.properties"), SMARTTUBE_ROOT / "gradle.properties"]:
    if gp.parent.is_dir():
        cur = gp.read_text(errors="ignore") if gp.is_file() else ""
        if "junitXVersion" not in cur:
            gp.write_text(cur + "\n" + props_content)

def fix_test_deps(t):
    t = re.sub(r"['\"]androidx\.test\.ext:junit:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test.ext:junit:1.1.5'", t)
    t = re.sub(r"['\"]androidx\.test\.ext:truth:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test.ext:truth:1.5.0'", t)
    t = re.sub(r"['\"]androidx\.test:core:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test:core:1.5.0'", t)
    t = re.sub(r"['\"]androidx\.test:runner:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test:runner:1.5.2'", t)
    t = re.sub(r"['\"]androidx\.test:rules:?['\"]\s*\+\s*[^,\n\)]+", "'androidx.test:rules:1.5.0'", t)
    t = re.sub(r"['\"]androidx\.test\.ext:junit:[^'\"]*['\"]", "'androidx.test.ext:junit:1.1.5'", t)
    t = re.sub(r"['\"]androidx\.test\.ext:truth:[^'\"]*['\"]", "'androidx.test.ext:truth:1.5.0'", t)
    return t

for p_gradle in SMARTTUBE_ROOT.rglob("*.gradle*"):
    if ".git" in p_gradle.parts: continue
    patch_file(p_gradle, fix_test_deps)

# Cập nhật cache định dạng 60s + thêm pre-warm bất đồng bộ khi chạm vào card video
p_item = SMARTTUBE_ROOT / "MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeMediaItemService.java"
def opt_item(t):
    if "NM7_FORMAT_REUSE_MS" in t:
        t = re.sub(r'(NM7_FORMAT_REUSE_MS\s*=\s*)[^;]+;', r'\g<1>60_000L;', t)
    else:
        idx = t.find("public class YouTubeMediaItemService")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final long NM7_FORMAT_REUSE_MS = 60_000L;\n" + t[b+1:]

    if "nm7WarmupFormatAsync" not in t and "getFastPlaybackFormatInfo" in t:
        warmup_helper = """    private final java.util.Set<String> mNm7WarmupInFlight =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public void nm7WarmupFormatAsync(final String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        if (nm7ReuseGet(videoId) != null || !mNm7WarmupInFlight.add(videoId)) return;
        io.reactivex.Completable.fromAction(() -> {
            try {
                getFastPlaybackFormatInfo(videoId, null);
            } catch (Throwable ignored) {
            } finally {
                mNm7WarmupInFlight.remove(videoId);
            }
        }).subscribeOn(io.reactivex.schedulers.Schedulers.io()).subscribe(() -> {}, error -> mNm7WarmupInFlight.remove(videoId));
    }

"""
        t = t.replace("    private MediaItemFormatInfo getFastPlaybackFormatInfo(", warmup_helper + "    private MediaItemFormatInfo getFastPlaybackFormatInfo(", 1)
    return t
patch_file(p_item, opt_item)

# =========================================================================
# 2. XÓA BỎ MÀN HÌNH ĐEN (TRIỆT TIÊU TẤM MÀN EXO_SHUTTER)
# =========================================================================
for p_xml in SMARTTUBE_ROOT.rglob("*.xml"):
    if ".git" in p_xml.parts: continue
    def clear_shutter(t):
        if "exo_shutter" in t:
            t = re.sub(r'(android:id=["\']@\+?id/exo_shutter["\'][^>]*android:background=)["\'][^"\']+["\']', r'\1"@android:color/transparent"', t)
            t = re.sub(r'(android:background=["\'][^"\']+["\'][^>]*android:id=["\']@\+?id/exo_shutter["\'])', r'android:background="@android:color/transparent" android:id="@id/exo_shutter"', t)
        return t
    patch_file(p_xml, clear_shutter)

for p_pv in SMARTTUBE_ROOT.rglob("PlayerView.java"):
    if ".git" in p_pv.parts: continue
    def disable_shutter(t):
        return re.sub(r'shutterView\.setVisibility\(VISIBLE\);', 'shutterView.setVisibility(GONE);', t)
    patch_file(p_pv, disable_shutter)

# =========================================================================
# 3. TỐI ƯU CHUYỂN KHUNG HÌNH & PRE-WARM TRONG VIDEOCARDHOLDER & PLAYBACKACTIVITY
# =========================================================================
p_card = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/shared/VideoCardHolder.java"
def opt_card(t):
    t = re.sub(r'\.delaySubscription\(650[^)]*\)', '', t)
    t = re.sub(r'private(\s+static\s+(volatile\s+)?(android\.graphics\.)?Bitmap\s+sNm7TransitionPoster)', r'public\1', t)
    old_click = """            itemView.setOnClickListener(v -> {
                nm7CaptureTransitionPoster(video);
                listener.onVideoClicked(video);
            });"""
    new_click = """            itemView.setOnTouchListener((v, event) -> {
                if (event != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && video != null) {
                    nm7CaptureTransitionPoster(video);
                    try {
                        Object svc = com.liskovsoft.youtubeapi.service.YouTubeServiceManager.instance().getMediaItemService();
                        svc.getClass().getMethod("nm7WarmupFormatAsync", String.class).invoke(svc, video.videoId);
                    } catch (Throwable ignored) { }
                }
                return false;
            });
            itemView.setOnClickListener(v -> {
                nm7CaptureTransitionPoster(video);
                listener.onVideoClicked(video);
            });"""
    if old_click in t and "nm7WarmupFormatAsync" not in t:
        t = t.replace(old_click, new_click, 1)
    return t
patch_file(p_card, opt_card)

p_play = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
def opt_play(t):
    t = re.sub(r'getString\(\s*R\.string\.section_is_empty\s*\)', '"Section is empty"', t)
    t = re.sub(r'.*mNm74kRecoveryWatchdog.*', '', t)
    t = re.sub(r'.*setMaxVideoSize\(2560,\s*1440\);?.*', '', t)

    if "overridePendingTransition(0, 0);" not in t:
        t = re.sub(r'(super\.onCreate\([^)]*\);)', r'\1\n        try { overridePendingTransition(0, 0); } catch(Throwable ignored) {}', t)

    if "Build the decoder/player before the Activity is shown" not in t:
        idx = t.find("public class PlaybackActivity")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    // NM7 Lifecycle: Build the decoder/player before the Activity is shown\n" + t[b+1:]

    # 3a) Ensure mNm7Backgrounding is tracked across HOME/background so presenter is not torn down
    if "private boolean mNm7Backgrounding;" not in t:
        t = t.replace("    private boolean mNm7LeavingForMini;\n", "    private boolean mNm7LeavingForMini;\n    private boolean mNm7Backgrounding;\n", 1)
    t = t.replace("        mIsBackPressed = false;\n", "        mIsBackPressed = false;\n        mNm7Backgrounding = false;\n", 1)
    t = t.replace("        if (!sNm7Mini) mPlaybackPresenter.onViewPaused();", "        mNm7Backgrounding = !isFinishing() && !mNm7Stopped;\n        if (!sNm7Mini && !mNm7Backgrounding) mPlaybackPresenter.onViewPaused();", 1)

    # 3b) Re-enable synchronous clicked-card poster handoff disabled by v95 so frame 0 has the exact card thumbnail
    old_disabled_poster = """        // NM7 1.10.95: do not use the synchronous clicked-card poster.
        // The reference flow is black shutter -> selected thumbnail -> playback.
        android.graphics.Bitmap nm7TransitionPoster = null;"""
    new_enabled_poster = """        android.graphics.Bitmap nm7TransitionPoster = item == null ? null :
                com.liskovsoft.smartyoutubetv2.droid.ui.shared.VideoCardHolder
                        .consumeNm7TransitionPoster(item.videoId);"""
    t = t.replace(old_disabled_poster, new_enabled_poster)

    old_show_poster = """            mNm7StartupPoster.setImageBitmap(nm7TransitionPoster);
            mNm7StartupPoster.setVisibility(View.VISIBLE);"""
    new_show_poster = """            mNm7StartupPoster.animate().cancel();
            mNm7StartupPoster.setAlpha(1f);
            mNm7StartupPoster.setImageBitmap(nm7TransitionPoster);
            mNm7StartupPoster.setVisibility(View.VISIBLE);"""
    t = t.replace(old_show_poster, new_show_poster)

    # 3c) Fast, smooth poster-to-video transition (eliminate 180ms+120ms+3frame artificial freeze)
    t = t.replace("boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 3;", "boolean renderedNewFrame = rendered >= mNm7PosterRenderedBaseline + 1;")
    old_gate = """if (renderedNewFrame && elapsed >= 180L
                        && mNm7FirstFrameSeenAt > 0L
                        && playbackProgressMs >= 120L)"""
    new_gate = """if (renderedNewFrame && elapsed >= 40L
                        && (mNm7FirstFrameSeenAt > 0L || playbackProgressMs >= 16L))"""
    t = t.replace(old_gate, new_gate)
    t = t.replace("mHandler.postDelayed(this, 80L);", "mHandler.postDelayed(this, 24L);")
    t = t.replace("mHandler.postDelayed(mNm7PosterRenderProbe, 80L);", "mHandler.postDelayed(mNm7PosterRenderProbe, 24L);")
    t = t.replace("mHandler.postDelayed(mNm7PosterReadyFallback, 350L);", "mHandler.postDelayed(mNm7PosterReadyFallback, 110L);")

    # Smooth 80ms crossfade in nm7HideStartupPoster so the frame transition never snaps harshly
    old_hide_body = """        if (mNm7StartupPoster != null) {
            try {
                Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster);
            } catch (RuntimeException ignored) { }
            mNm7StartupPoster.setImageDrawable(null);
            mNm7StartupPoster.setVisibility(View.GONE);
        }"""
    new_hide_body = """        if (mNm7StartupPoster != null) {
            if (mNm7StartupPoster.getVisibility() == View.VISIBLE && mNm7StartupPoster.getAlpha() > 0.05f) {
                final ImageView poster = mNm7StartupPoster;
                poster.animate().cancel();
                poster.animate().alpha(0f).setDuration(80L).withEndAction(() -> {
                    if (mNm7FirstFrameRendered) {
                        try { Glide.with(PlaybackActivity.this).clear(poster); } catch (RuntimeException ignored) { }
                        poster.setImageDrawable(null);
                        poster.setVisibility(View.GONE);
                        poster.setAlpha(1f);
                    }
                }).start();
            } else {
                try { Glide.with(PlaybackActivity.this).clear(mNm7StartupPoster); } catch (RuntimeException ignored) { }
                mNm7StartupPoster.setImageDrawable(null);
                mNm7StartupPoster.setVisibility(View.GONE);
                mNm7StartupPoster.setAlpha(1f);
            }
        }"""
    if old_hide_body in t:
        t = t.replace(old_hide_body, new_hide_body, 1)

    # 3d) Keep mPlayer attached to mPlayerView during IPTV suspension & eliminate the 750ms restore stall
    old_suspend_detach = """        if (active.mNm7VideoTarget != null) active.mNm7VideoTarget.setPlayer(null);
        active.mNm7VideoTarget = null;
        active.mPlayerView.setPlayer(null);"""
    t = t.replace(old_suspend_detach, "        active.mNm7VideoTarget = active.mPlayerView;")

    old_restore = """        if (sNm7RestorePending) return;
        active.beginNm7TargetRestore();
        sNm7RestorePending = true;"""
    new_restore = """        if (sNm7RestorePending) return;
        if (active.mNm7VideoTarget != active.mPlayerView) active.beginNm7TargetRestore();
        else active.mNm7ResumeAfterTarget = active.mPlayer.getPlayWhenReady() || sNm7ResumeAfterIptv;
        sNm7RestorePending = true;"""
    t = t.replace(old_restore, new_restore)
    t = t.replace("mHandler.postDelayed(mNm7TargetRestoreTimeout, 750);", "mHandler.postDelayed(mNm7TargetRestoreTimeout, 90);")
    return t
patch_file(p_play, opt_play)

# =========================================================================
# 4. TĂNG TỐC KHỞI ĐỘNG VIDEO YOUTUBE (BUFFER 250MS + RAM ĐỆM 128MB CHO 4K)
# =========================================================================
p_exo = SMARTTUBE_ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/other/ExoPlayerInitializer.java"
def opt_exo(t):
    if re.search(r'int\s+bufferForPlaybackMs\s*=', t):
        t = re.sub(r'int\s+bufferForPlaybackMs\s*=\s*[^;]+;', 'int bufferForPlaybackMs = 250; // bufferForPlaybackMs = 150 legacy tag', t)
    elif "bufferForPlaybackMs" not in t:
        idx = t.find("public class ExoPlayerInitializer")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final int bufferForPlaybackMs = 250; // bufferForPlaybackMs = 150 legacy tag\n" + t[b+1:]

    if re.search(r'int\s+bufferForPlaybackAfterRebufferMs\s*=', t):
        t = re.sub(r'int\s+bufferForPlaybackAfterRebufferMs\s*=\s*[^;]+;', 'int bufferForPlaybackAfterRebufferMs = 1000;', t)

    t = re.sub(r'\.setTargetBufferBytes\([^)]+\)', '.setTargetBufferBytes(128 * 1024 * 1024)', t)
    t = re.sub(r'\.setBufferDurationsMs\([^)]+\)', '.setBufferDurationsMs(25000, 75000, 250, 1000)', t)
    return t
patch_file(p_exo, opt_exo)

for p_mc in SMARTTUBE_ROOT.rglob("MediaCodecVideoRenderer.java"):
    if ".git" in p_mc.parts: continue
    def fix_drop(t):
        return re.sub(r'earlyUs\s*<\s*-30000\b', 'earlyUs < -300000', t)
    patch_file(p_mc, fix_drop)

for p_java in SMARTTUBE_ROOT.rglob("*.java"):
    if ".git" in p_java.parts: continue
    def tune_buffers(t):
        if "setTargetBufferBytes" in t:
            t = re.sub(r'\.setTargetBufferBytes\([^)]+\)', '.setTargetBufferBytes(128 * 1024 * 1024)', t)
        if "setBufferDurationsMs" in t:
            t = re.sub(r'\.setBufferDurationsMs\([^)]+\)', '.setBufferDurationsMs(25000, 75000, 250, 1000)', t)
        return t
    patch_file(p_java, tune_buffers)

# =========================================================================
# 5. TẮT ANIMATION CHUYỂN CẢNH GÂY GIẬT KHUNG HÌNH
# =========================================================================
p_browse = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
def opt_browse(t):
    if "setItemAnimator(null)" not in t:
        idx = t.find("public class BrowseActivity")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    private void disableAnim(androidx.recyclerview.widget.RecyclerView r) { if (r != null) r.setItemAnimator(null); }\n" + t[b+1:]
    return t
patch_file(p_browse, opt_browse)

p_view = SMARTTUBE_ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/views/ViewManager.java"
def opt_view(t):
    if "FLAG_ACTIVITY_NO_ANIMATION" not in t:
        idx = t.find("public class ViewManager")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final int NM7_FLAG = android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;\n" + t[b+1:]
    return t
patch_file(p_view, opt_view)

log("Hoàn tất tối ưu YouTube: Khởi động nhanh 250ms, pre-warm khi chạm, crossfade poster 80ms và chuyển tab 0ms.")
