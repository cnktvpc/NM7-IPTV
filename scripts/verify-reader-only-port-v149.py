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
    ("version is 1.10.149/code 165",
     'versionName = "1.10.149"' in gradle and "versionCode = 165" in gradle),
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
