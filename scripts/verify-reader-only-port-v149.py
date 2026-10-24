#!/usr/bin/env python3
"""Regression gate: Reader-only additions on top of the exact 1.10.142 source build."""
from pathlib import Path
import subprocess

def read(p):
    return Path(p).read_text(encoding="utf-8")

gradle = read("app/build.gradle.kts")
tabs = read("app/src/main/java/vn/phuong/iptvplayer/HomeTabBar.java")
manifest = read("app/src/main/AndroidManifest.xml")
reader = read("app/src/main/java/vn/phuong/iptvplayer/NewsReaderActivity.java")
checks = [
    ("version is 1.10.150/code 166",
     'versionName = "1.10.150"' in gradle and "versionCode = 166" in gradle),
    ("Reader is selectable from the shared bottom navigation",
     "attachReader" in tabs and "R.drawable.nm7_nav_news" in tabs and "openNews(activity)" in tabs),
    ("original 4fc IPTV handoff method remains", 
     "PlayerActivity.cancelYoutubeHandoff();" in tabs and
     "MobileInlinePlayerProviderV2.resumeForIptvTab(activity);" in tabs),
    ("original YouTube restore/mini-player routing remains",
     "isNm7MiniPlayerActive" in tabs and "restoreNm7Player" in tabs and
     "PlayerActivity.prepareForYoutubeHandoff(activity);" in tabs),
    ("Reader activity registrations exist",
     'android:name=".NewsReaderActivity"' in manifest and
     'android:name=".ReaderPluginActivity"' in manifest),
    ("Reader tab reuses an existing activity",
     "Intent.FLAG_ACTIVITY_REORDER_TO_FRONT" in tabs and "NewsReaderActivity.class" in tabs),
    ("Reader caches feeds and rendered pages, restoring scroll",
     "feedCache" in reader and "feedCacheKey(source.id, feedUrl, selectedCategory)" in reader and
     "feedPages" in reader and "restoreFeedScrollPosition();" in reader and
     "rememberFeedScrollPosition();" in reader),
    ("Horizontal swipe reveals the neighboring page and animates both pages",
     "setTranslationX(followX)" in reader and "animate().translationX" in reader and
     "prepareSwipePreview(direction, this)" in reader and
     "stepCategoryBySwipe(direction, this, preview)" in reader and
     "updateCategorySelection()" in reader),
    ("Adjacent categories are prefetched without rebuilding the visible page",
     "prefetchAdjacentFeeds" in reader and "prefetchedFeeds" in reader),
    ("Page switches invalidate stale asynchronous feed responses",
     "Invalidate any request for the page being left" in reader and
     "A completed request for the page being swiped away" in reader),
    ("24h has dedicated EPL, World, Business and Entertainment categories",
     '"Ngoại Hạng Anh", "https://www.24h.com.vn/bong-da-ngoai-hang-anh-c149.html"' in reader and
     'case "Ngoại Hạng Anh": return "https://www.24h.com.vn/bong-da-ngoai-hang-anh-c149.html";' in
       read("app/src/main/java/vn/phuong/iptvplayer/ReaderFeedCategoryRouter.java")),
    ("IPTV tab does not restart a surviving ExoPlayer session",
     "Reader is a navigation overlay" in Path("app/src/main/java/vn/phuong/iptvplayer/MobileInlinePlayerProviderV2.java").read_text() and
     "player.getPlaybackState() != Player.STATE_IDLE" in Path("app/src/main/java/vn/phuong/iptvplayer/MobileInlinePlayerProviderV2.java").read_text()),
    ("built-in news providers and RSS/HTML fallback exist",
     'makeSource("vnexpress"' in reader and "loadArticlesWithFallback" in reader and "parseHtmlFeed" in reader),
]
for label, passed in checks:
    if not passed:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)

# Enforce that the core YouTube/SmartTube patches are byte-for-byte based on the pinned source.
forbidden = [
    "scripts/patch-mobile-v105.py", "scripts/patch-mobile-v37.py",
    "scripts/patch-mobile-v56.py", "scripts/patch-mobile-v58.py",
    "scripts/patch-mobile-v69-no-miniplayer.py", "scripts/patch-mobile-v75.py",
    "scripts/smarttube-playback-mobile.java.inc", "scripts/mobile-ui/java/Nm7FeedAdapter.java",
]
source = "4fc8b187db7e48591e1bcb11969436a56d27d06a"
p = subprocess.run(["git", "diff", "--exit-code", source, "HEAD", "--", *forbidden],
                   text=True, capture_output=True)
if p.returncode:
    print(p.stdout)
    print(p.stderr)
    raise SystemExit("FAIL: YouTube/SmartTube patch files differ from pinned baseline")
print("PASS: YouTube/SmartTube patch chain unchanged from pinned baseline")
