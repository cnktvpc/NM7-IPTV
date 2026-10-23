#!/usr/bin/env python3
"""Fail-closed regression guard for YouTube Back -> mini -> close behavior on Mobile 1.10.148."""
from pathlib import Path

read = lambda p: Path(p).read_text(encoding="utf-8")
root = Path("third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui")
play = read(str(root / "playback/PlaybackActivity.java"))
browse = read(str(root / "browse/BrowseActivity.java"))
search = read(str(root / "search/SearchActivity.java"))
patch = read("scripts/patch-mobile-v37.py")
back_patch = read("scripts/patch-mobile-v69-no-miniplayer.py")
include = read("scripts/smarttube-playback-mobile.java.inc")
application = read("app/src/main/java/vn/phuong/iptvplayer/MobileNm7Application.java")


def check(ok, message):
    if not ok:
        raise SystemExit("FAIL: " + message)
    print("PASS: " + message)


# Regression root cause: the old NM7 API-33 callback on PlaybackActivity invoked
# onBackPressed() directly while the Application separately installs native-back
# handling on Browse/Search. Keep PlaybackActivity on Android/SmartTube's native path.
check("Nm7BackApi" not in play and "OnBackInvokedDispatcher" not in play,
      "PlaybackActivity does not install a competing custom API-33 Back callback")
check("Nm7BackApi.register" not in patch and "Nm7BackApi.unregister" not in patch
      and "mNm7BackCallback" not in include,
      "shared Mobile patch chain no longer injects/registers the duplicate callback")

# First Back leaves the player alive as mini; the second Back closes it whether it
# reaches PlaybackActivity during the transition or the foreground BrowseActivity.
check("if (mNm7LeavingForMini) {\n            consumeNm7BrowseBack();\n            return;\n        }" in play,
      "a second Back arriving during the Activity transition consumes and closes mini")
check("mNm7LeavingForMini = true;" in play and "sNm7Mini = true;" in play
      and "startActivity(intent);" in play,
      "first Back keeps the YouTube session alive and returns to Browse mini-player")
check("consumeNm7BrowseBack()" in browse and "super.onBackPressed();" in browse
      and "consumeNm7BrowseBack()" in back_patch,
      "Browse's second Back consumes the mini close action, with normal Back fallback")
check("consumeNm7BrowseBack()" in search,
      "Search uses the same close path when the mini-player is active")
check("if (!isNm7MiniPlayerActive()) return false;" in include
      and "MobileMiniPlayer" in include and "stopForNm7Iptv();" in include,
      "mini close removes the floating surface and stops the real PlaybackActivity")
check("active.mNm7Stopped = true;" in include
      and "active.finishReally();" in include
      and "sNm7Active = null;" in include,
      "closing mini releases the playback owner instead of leaving audio running")

# The Application-level predictive callback belongs only to Browse/Search.
check("SMARTTUBE_BROWSE.equals(name) || (SMARTTUBE_PACKAGE + \"search.SearchActivity\").equals(name)"
      in application and "installSmartTubeBackHandling(activity);" in application,
      "Android 13+ Browse/Search Back bridge remains installed")
playback_branch = application[application.find('} else if (SMARTTUBE_PLAYBACK.equals(name)) {'):
                              application.find('} else if (name.startsWith(SMARTTUBE_PACKAGE)', application.find('} else if (SMARTTUBE_PLAYBACK.equals(name)) {'))]
check("installSmartTubeBackHandling(activity)" not in playback_branch,
      "PlaybackActivity deliberately uses native Back dispatch, not Application callback injection")

print("PASS: 9 YouTube Back-navigation regression guards")
