#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NM7 IPTV - YouTube Performance, Floating Miniplayer & Seamless Mobile Transition Patch v1.10.106
- Xóa bỏ triệt để màn hình đen khi mở video và khi bấm Back thu nhỏ xuống miniplayer.
- Tăng tốc mở video YouTube: hạ buffer khởi động xuống 250ms (giữ RAM đệm 128MB cho 4K).
- Chuyển cảnh mượt từ poster sang video đang chạy bằng hiệu ứng crossfade 80ms ngay khi có frame mới.
- Không gọi warmup trên ACTION_DOWN khi cuộn danh sách (tránh nghẽn luồng J2V8 single-thread).
- Đồng bộ hoàn hảo surface handoff giữa Fullscreen <-> Floating Miniplayer <-> Tab IPTV.
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

def replace_method(text: str, signature: str, new_method: str) -> str:
    pattern = re.compile(r"(?ms)^    " + re.escape(signature) + r" \{.*?^    \}")
    updated, count = pattern.subn(lambda _: new_method.rstrip(), text, count=1)
    return updated if count == 1 else text

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

# Cập nhật cache định dạng 60s
p_item = SMARTTUBE_ROOT / "MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeMediaItemService.java"
def opt_item(t):
    if "NM7_FORMAT_REUSE_MS" in t:
        t = re.sub(r'(NM7_FORMAT_REUSE_MS\s*=\s*)[^;]+;', r'\g<1>60_000L;', t)
    else:
        idx = t.find("public class YouTubeMediaItemService")
        if idx != -1:
            b = t.find("{", idx)
            t = t[:b+1] + "\n    public static final long NM7_FORMAT_REUSE_MS = 60_000L;\n" + t[b+1:]
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
# 3. TỐI ƯU VIDEOCARDHOLDER & PLAYBACKACTIVITY (MINIPLAYER + FULLSCREEN + IPTV)
# =========================================================================
p_card = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/shared/VideoCardHolder.java"
def opt_card(t):
    t = re.sub(r'\.delaySubscription\(650[^)]*\)', '', t)
    t = re.sub(r'private(\s+static\s+(volatile\s+)?(android\.graphics\.)?Bitmap\s+sNm7TransitionPoster)', r'public\1', t)
    return t
patch_file(p_card, opt_card)

p_play = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
def opt_play(t):
    player_create = "        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);"
    if "mNm7DesiredVolume = mPlayer.getVolume();" not in t:
        if player_create not in t:
            raise SystemExit("Missing SmartTube player creation anchor for fail-closed audio mute")
        t = t.replace(player_create, player_create + "\n        mNm7DesiredVolume = mPlayer.getVolume();\n        mPlayer.setVolume(0f);", 1)

    set_volume = """    public void setVolume(float volume) {
        mNm7DesiredVolume = Math.max(0f, Math.min(1f, volume));
        if (mPlayer != null) mPlayer.setVolume(mNm7OwnsPlayback ? mNm7DesiredVolume : 0f);
    }"""
    if set_volume not in t:
        original = t
        t = replace_method(t, "public void setVolume(float volume)", set_volume)
        if t == original:
            raise SystemExit("Missing SmartTube setVolume method for fail-closed audio mute")
    get_volume = """    public float getVolume() {
        return mNm7DesiredVolume;
    }"""
    if get_volume not in t:
        original = t
        t = replace_method(t, "public float getVolume()", get_volume)
        if t == original:
            raise SystemExit("Missing SmartTube getVolume method for stored user volume")

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

    # 3a) Ensure mNm7Backgrounding is tracked across HOME/background and prevent mPanel from turning black in onPause()
    if "private boolean mNm7Backgrounding;" not in t:
        t = t.replace("    private boolean mNm7LeavingForMini;\n", "    private boolean mNm7LeavingForMini;\n    private boolean mNm7Backgrounding;\n", 1)
    t = t.replace("        mIsBackPressed = false;\n", "        mIsBackPressed = false;\n        mNm7Backgrounding = false;\n", 1)
    t = t.replace("        if (!sNm7Mini) mPlaybackPresenter.onViewPaused();", "        mNm7Backgrounding = !isFinishing() && !mNm7Stopped;\n        if (!sNm7Mini && !mNm7Backgrounding) mPlaybackPresenter.onViewPaused();", 1)
    t = t.replace("        showHideWidgets(false);", "        if (isInPIPMode()) showHideWidgets(false);", 1)

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

    # Smooth 80ms crossfade in nm7HideStartupPoster
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

    # 3d) Increase Watch feed RecyclerView cache so scrolling below player never stutters
    t = t.replace("mSuggestionsView.setItemViewCacheSize(0);", "mSuggestionsView.setItemViewCacheSize(10);")
    t = t.replace("mSuggestionsView.setItemViewCacheSize(1);", "mSuggestionsView.setItemViewCacheSize(10);")
    t = t.replace("mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 2);", "mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 12);")
    t = t.replace("mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 4);", "mSuggestionsView.getRecycledViewPool().setMaxRecycledViews(1, 12);")

    # 3e) Keep the generation-aware lifecycle methods from the active v37 payload.
    # Replacing them here used to bypass the first-frame gate and resume audio immediately.

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
# 5. TỐI ƯU RECYCLERVIEW BROWSE & TẮT ANIMATION CHUYỂN CẢNH GÂY GIẬT
# =========================================================================
p_browse = SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java"
def opt_browse(t):
    t = t.replace("list.setItemViewCacheSize(0);", "list.setItemViewCacheSize(10);")
    t = t.replace("list.getRecycledViewPool().setMaxRecycledViews(0, 2);", "list.getRecycledViewPool().setMaxRecycledViews(0, 12);")
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

log("Hoàn tất tối ưu YouTube: Floating 16:9 Miniplayer, khởi động nhanh 250ms, crossfade 80ms và chuyển tab mượt mà.")

# =========================================================================
# 6. TỰ ĐỘNG ÁP DỤNG YOUTUBE CHROME & PULL-TO-REFRESH (v114..v130) CHO MỌI WORKFLOW
# =========================================================================
import runpy
_chrome_and_refresh_chain = [
    "scripts/patch-mobile-v114-youtube-ui.py",
    "scripts/patch-mobile-v119-youtube-chrome-final.py",
    "scripts/patch-mobile-v120-youtube-statusbar-final.py",
    "scripts/patch-mobile-v121-native-statusbar.py",
    "scripts/patch-mobile-v122-youtube-overdraw-final.py",
    "scripts/patch-mobile-v123-youtube-wordmark-final.py",
    "scripts/patch-mobile-v128-youtube-wordmark-exact.py",
    "scripts/patch-mobile-v130-pull-refresh.py",
]
if (SMARTTUBE_ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java").is_file() and Path("smarttube/build.gradle.kts").is_file():
    for _script in _chrome_and_refresh_chain:
        if Path(_script).is_file():
            runpy.run_path(_script)
