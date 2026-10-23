from pathlib import Path
def read(p): return Path(p).read_text(encoding="utf-8")
g=read("app/build.gradle.kts"); m=read("app/src/main/AndroidManifest.xml")
t=read("app/src/main/java/vn/phuong/iptvplayer/HomeTabBar.java")
n=read("app/src/main/java/vn/phuong/iptvplayer/NewsReaderActivity.java")
e=read("app/src/main/java/vn/phuong/iptvplayer/ReaderArticleExtractor.java")
r=read("app/src/main/java/vn/phuong/iptvplayer/ReaderPluginRuntime.java")
assert 'versionName = "1.10.149"' in g and "versionCode = 165" in g
assert 'implementation("org.jsoup:jsoup:1.23.2")' in g and 'implementation("org.mozilla:rhino:1.7.15.1")' in g
assert 'android:name=".NewsReaderActivity"' in m and 'android:name=".ReaderPluginActivity"' in m
assert "public static void attachReader(Activity activity)" in t and "openNews(activity)" in t
assert "ReaderArticleExtractor.fetchAndRender" in n and "setJavaScriptEnabled(false)" in n
assert "Jsoup.clean" in e and "ClassShutter" in r
assert "WindowInsets.Type.navigationBars()" not in t and "barParams.bottomMargin" not in t
print("PASS: Reader-only integration checks")