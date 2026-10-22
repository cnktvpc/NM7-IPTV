#!/usr/bin/env python3
"""Static regression guards for NM7 Mobile 1.10.145 native article reader."""
from pathlib import Path

read = lambda p: Path(p).read_text(encoding="utf-8")
reader = read("app/src/main/java/vn/phuong/iptvplayer/NewsReaderActivity.java")
extractor = read("app/src/main/java/vn/phuong/iptvplayer/ReaderArticleExtractor.java")
tabs = read("app/src/main/java/vn/phuong/iptvplayer/HomeTabBar.java")
gradle = read("app/build.gradle.kts")

checks = [
    ("candidate version is 1.10.145 / code 161",
     'versionName = "1.10.145"' in gradle and "versionCode = 161" in gradle),
    ("Reader applies status-bar and display-cutout top insets",
     "WindowInsets.Type.statusBars()" in reader
     and "WindowInsets.Type.displayCutout()" in reader
     and "view.setPadding(view.getPaddingLeft(), topInset" in reader),
    ("Reader reserves bottom navigation insets plus shared tab-row height",
     "readerBarHeight + bottomInset" in reader),
    ("shared navigation row sits above navigation/gesture inset",
     "barParams.bottomMargin = bottomInset" in tabs
     and "WindowInsets.Type.navigationBars()" in tabs),
    ("IPTV list reserves shared tab height and system inset",
     "channelListBaseBottom + listReserve + bottomInset" in tabs),
    ("article uses a native extraction pipeline, not an injected website rewrite",
     "ReaderArticleExtractor.fetchAndRender" in reader
     and "loadDataWithBaseURL(baseUrl, html, " in reader
     and "setJavaScriptEnabled(false)" in reader
     and "READER_MODE_SCRIPT" not in reader
     and "evaluateJavascript" not in reader),
    ("reader CSS guarantees dark background and readable high-contrast typography",
     "background:" in extractor and "#101114" in extractor
     and "#e9e9ed" in extractor and "line-height:" in extractor),
    ("extractor searches known Vietnamese and common article body selectors",
     '[itemprop=articleBody]' in extractor
     and '".fck_detail"' in extractor
     and '".dt-news__content"' in extractor
     and '".entry-content"' in extractor),
    ("extractor removes ad/recommendation/script blocks and sanitizes markup",
     "REMOVE_SELECTOR" in extractor and "clone.select(REMOVE_SELECTOR).remove()" in extractor
     and "Jsoup.clean" in extractor),
    ("failed fetch never returns the publisher website as a fallback",
     "ReaderArticleExtractor.fallback" in reader
     and "Never fall back to the publisher's full page" in reader),
    ("news source catalogue remains the existing fixed catalog",
     'makeSource("vnexpress"' in reader and 'makeSource("dantri"' in reader
     and 'makeSource("vtv"' in reader),
    ("comic/novel source manager opens a separate JS-plugin screen",
     "new Intent(NewsReaderActivity.this, ReaderPluginActivity.class)" in reader
     and "ReaderPluginActivity.EXTRA_TYPE" in reader),
]
for label, passed in checks:
    if not passed:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} native Reader checks")
