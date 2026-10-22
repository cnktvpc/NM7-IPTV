#!/usr/bin/env python3
"""Static guards for user-installed comic/novel JS reader sources."""
from pathlib import Path

def read(p):
    return Path(p).read_text(encoding="utf-8")

runtime = read("app/src/main/java/vn/phuong/iptvplayer/ReaderPluginRuntime.java")
store = read("app/src/main/java/vn/phuong/iptvplayer/ReaderPluginStore.java")
activity = read("app/src/main/java/vn/phuong/iptvplayer/ReaderPluginActivity.java")
manifest = read("app/src/main/AndroidManifest.xml")
gradle = read("app/build.gradle.kts")
news = read("app/src/main/java/vn/phuong/iptvplayer/NewsReaderActivity.java")

checks = [
    ("Rhino JavaScript runtime dependency is pinned", 'org.mozilla:rhino:1.7.15.1' in gradle),
    ("Jsoup DOM parser/sanitizer dependency is pinned", 'org.jsoup:jsoup:1.23.2' in gradle),
    ("plugin source model has novel and comic source types", '"comic".equals(type)' in runtime and '"novel".equals(type)' in runtime),
    ("plugins can be imported by HTTPS URL or local document picker", "installRemote(address)" in activity and "ACTION_OPEN_DOCUMENT" in activity),
    ("plugin scripts are size-limited and Java package access is hidden", "MAX_SCRIPT_BYTES" in runtime and "setClassShutter" in runtime and "visibleToScripts" in runtime),
    ("plugin runtime exposes controlled HTTP and DOM helpers", 'http.put("get"' in runtime and 'dom.put("text"' in runtime and 'dom.put("select"' in runtime),
    ("comic reader renders ordered image pages", "comicReaderHtml" in activity and 'type.equals("comic") ? "pages" : "chapter"' in activity),
    ("novel reader renders sanitized chapter HTML or text", "textReaderHtml" in activity and "Safelist.relaxed()" in activity),
    ("source manager supports install, enable/disable and remove", "ReaderPluginStore.install" in activity and "ReaderPluginStore.setEnabled" in activity and "ReaderPluginStore.remove" in activity),
    ("plugin activity is registered in Android manifest", 'android:name=".ReaderPluginActivity"' in manifest),
    ("news list catalog is not extended by comic/novel plugin definitions", "private static final List<Source> CATALOG" in news and "ReaderPluginStore.install" not in news),
]
for label, passed in checks:
    if not passed:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} Reader plugin checks")
