#!/usr/bin/env python3
"""Regression checks for Reader system-bar geometry, dark reader mode and ad filtering."""
from pathlib import Path

read = lambda p: Path(p).read_text(encoding="utf-8")
reader = read("app/src/main/java/vn/phuong/iptvplayer/NewsReaderActivity.java")
tabs = read("app/src/main/java/vn/phuong/iptvplayer/HomeTabBar.java")
gradle = read("app/build.gradle.kts")

checks = [
    ("candidate version is 1.10.144 / code 160",
     'versionName = "1.10.144"' in gradle and "versionCode = 160" in gradle),
    ("Reader applies status-bar and display-cutout top insets",
     "WindowInsets.Type.statusBars()" in reader
     and "WindowInsets.Type.displayCutout()" in reader
     and "view.setPadding(view.getPaddingLeft(), topInset" in reader),
    ("Reader reserves bottom navigation insets plus the shared tab-row height",
     "readerBarHeight + bottomInset" in reader),
    ("shared navigation row sits above system navigation/gesture area",
     "barParams.bottomMargin = bottomInset" in tabs
     and "WindowInsets.Type.navigationBars()" in tabs),
    ("IPTV list keeps extra scroll space for shared tab row and navigation inset",
     "channelListBaseBottom + listReserve + bottomInset" in tabs),
    ("article view injects a dark background and light body text",
     "READER_MODE_SCRIPT" in reader
     and "#101114" in reader
     and "#f1f2f5" in reader
     and "#e9e9ed" in reader),
    ("reader mode extracts common publisher article-body containers",
     '[itemprop="articleBody"]' in reader
     and ".fck_detail" in reader
     and ".dt-news__content" in reader),
    ("reader mode removes ad placements and common recommendation blocks",
     "stripAds(document)" in reader
     and "[class*=\\"advert\\"]" in reader
     and "[data-ad-slot]" in reader
     and "[class*=\\"related\\"]" in reader),
    ("ad/tracker hosts are blocked before WebView resources are loaded",
     "shouldInterceptRequest" in reader
     and "isBlockedAdRequest(request.getUrl(), request.isForMainFrame())" in reader
     and "doubleclick.net" in reader
     and "googlesyndication.com" in reader
     and "admicro.vn" in reader),
    ("main-frame article URL is not blocked by the subresource filter",
     "if (uri == null || mainFrame) return false" in reader),
    ("article WebView applies Reader mode only after page completion",
     "onPageFinished(WebView view, String finishedUrl)" in reader
     and "view.evaluateJavascript(READER_MODE_SCRIPT, null)" in reader),
]
for label, passed in checks:
    if not passed:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} Reader visual/ad-block checks")
