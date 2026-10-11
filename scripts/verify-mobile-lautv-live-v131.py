#!/usr/bin/env python3
"""Fail-closed structural checks for NM7 Mobile 1.10.131 LauTV Live compatibility."""
from pathlib import Path
import re

app = Path("app/src/main/java/vn/phuong/iptvplayer")
main = (app / "MainActivity.java").read_text(encoding="utf-8")
channel = (app / "Channel.java").read_text(encoding="utf-8")
parser = (app / "M3uParser.java").read_text(encoding="utf-8")
player = (app / "PlayerActivity.java").read_text(encoding="utf-8")
xtream = (app / "XtreamM3uSource.java").read_text(encoding="utf-8")
layout = Path("app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
browse = Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/browse/BrowseActivity.java").read_text(encoding="utf-8")
browse_layout = Path("third_party/SmartTube-droid/smarttubedroid/src/main/res/layout/browse_activity.xml").read_text(encoding="utf-8")
upstream_gradle = Path("third_party/SmartTube-droid/smarttubedroid/build.gradle").read_text(encoding="utf-8")
module_gradle = Path("smarttube/build.gradle.kts").read_text(encoding="utf-8")

checks = [
    ("YouTube Home pull observer remains installed", len(re.findall(r"(?m)^\s*public\s+boolean\s+dispatchTouchEvent\s*\(", browse)) == 1 and "trackPullDownGesture" in browse),
    ("pull-to-refresh keeps the vertical gesture threshold", "density * 96f" in browse and "dy > Math.abs(dx) * 1.2f" in browse),
    ("pull-to-refresh still calls the existing presenter", "mBrowsePresenter.refresh(false);" in browse),
    ("pull-to-refresh wraps actual Browse content", 'android:id="@+id/browse_pull_refresh"' in browse_layout and 'android:id="@+id/browse_content"' in browse_layout),
    ("SwipeRefreshLayout dependency is declared for the SmartTube module", "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0" in upstream_gradle and 'implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")' in module_gradle),
    ("Xtream button exists and is wired", 'android:id="@+id/btnXtream"' in layout and 'findViewById(R.id.btnXtream).setOnClickListener(v->showXtreamLogin())' in main),
    ("Xtream builder encodes credentials and validates output", 'encode(cleanUser)' in xtream and 'encode(cleanPassword)' in xtream and 'cleanOutput.equals("m3u8")' in xtream and 'cleanOutput.equals("ts")' in xtream),
    ("M3U parser accepts backup/fallback metadata", 'case "backup-url"' in parser and 'case "fallback-url"' in parser and '"#NM7-ALT-URL:" + candidate' in parser),
    ("channel candidates are ordered and de-duplicated", "streamCandidates()" in channel and "LinkedHashSet<String> candidates" in channel),
    ("main list passes candidates to the player", 'EXTRA_CANDIDATES,new ArrayList<>(c.streamCandidates())' in main),
    ("player advances on fatal errors and repeated recovery attempts", 'tryAdvanceStreamCandidate("Nguồn phát lỗi; chuyển sang nguồn dự phòng")' in player and "attempt>=3&&tryAdvanceStreamCandidate(reason)" in player),
    ("player retains candidates after channel switching", "streamCandidates.addAll(c.streamCandidates())" in player and "EXTRA_CANDIDATES" in player),
    ("version is 1.10.131 / code 147", 'versionName = "1.10.131"' in gradle and "versionCode = 147" in gradle),
]
for label, ok in checks:
    if not ok:
        raise SystemExit("FAIL: " + label)
    print("PASS: " + label)
print(f"PASS: {len(checks)} NM7 LauTV Live compatibility checks")
