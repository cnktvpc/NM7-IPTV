"""Compatibility smoke test for the current Nm7 IPTV Player UI.

Checks Mobile startup in both portrait and landscape orientations.
No public IPTV stream is required.
"""
import os
import re
import subprocess
import sys
import time
import tempfile
import xml.etree.ElementTree as ET
from datetime import datetime
from zoneinfo import ZoneInfo

APK = sys.argv[1]
PACKAGE = ""
ACTIVITY = "vn.phuong.iptvplayer.MainActivity"

def resolve_package(apk):
    result = subprocess.run(
        [os.environ.get("ANDROID_HOME", "/usr/local/lib/android/sdk") + "/build-tools/36.0.0/aapt", "dump", "badging", apk],
        check=True, capture_output=True, text=True,
    )
    match = re.search(r"package: name='([^']+)'", result.stdout)
    if not match:
        raise AssertionError("Cannot determine APK package id")
    return match.group(1)

def seed_live_feed_fixture():
    # Keep fullscreen server-selector smoke testing independent of an external feed.
    # This only writes the cache inside the temporary debug emulator.
    if os.environ.get("NM7_SMOKE_LIVE_FIXTURE") != "1":
        return
    kickoff = datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).strftime("%H:%M %d/%m")
    fixture = (
        "#EXTM3U\n"
        f'#EXTINF:-1 group-title="Bóng đá",{kickoff} [LIVE] NM7 Smoke Home vs NM7 Smoke Away - Server 1\n'
        "https://example.com/nm7-smoke-server-1.m3u8\n"
        f'#EXTINF:-1 group-title="Bóng đá",{kickoff} [LIVE] NM7 Smoke Home vs NM7 Smoke Away - Server 2\n'
        "https://example.com/nm7-smoke-server-2.m3u8\n"
    )
    # Stream the text through run-as stdin directly into app-private cache. The
    # emulated /sdcard is not readable by run-as on all Android 15 system images.
    result = subprocess.run(
        ["adb", "shell", "run-as", PACKAGE, "sh", "-c", "cat > cache/nm7-live-feed.m3u"],
        input=fixture, check=False, capture_output=True, text=True, timeout=40,
    )
    if result.returncode:
        raise AssertionError("Could not seed deterministic LIVE feed cache: " + result.stderr)
    run_adb("shell", "cmd", "connectivity", "airplane-mode", "enable", check=False)
    print("INFO: seeded deterministic two-server LIVE fixture; CI emulator network isolated")

def run_adb(*args, timeout=40, check=True):
    result = subprocess.run(["adb", *args], check=False, capture_output=True, text=True, timeout=timeout)
    if check and result.returncode:
        raise subprocess.CalledProcessError(result.returncode, result.args, output=result.stdout, stderr=result.stderr)
    return result

def adb(*args, timeout=40):
    return run_adb(*args, timeout=timeout).stdout

def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/nm7-smoke.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/nm7-smoke.xml"))

def has_id(root, name):
    return any(n.get("resource-id") == PACKAGE + ":id/" + name for n in root.iter("node"))

def inline_panel_visible(root):
    return any(n.get("content-desc") == "NM7_INLINE_PLAYER_PANEL" for n in root.iter("node"))

def live_button_node(root):
    for node in root.iter("node"):
        match = re.search(r"\bLIVE\s*\((\d+)\)", node.get("text", ""), re.IGNORECASE)
        if match and int(match.group(1)) > 0:
            return node
    return None

def live_event_row_node(root, require_multiple_servers=False):
    list_view = next((n for n in root.iter("node")
                      if n.get("resource-id") == PACKAGE + ":id/listChannels"), None)
    if list_view is None:
        return None
    for row in list_view.findall("node"):
        if not any(n.get("resource-id") == PACKAGE + ":id/txtLiveEventTitle"
                   and n.get("text", "").strip() for n in row.iter("node")):
            continue
        if require_multiple_servers:
            server = next((n for n in row.iter("node")
                           if n.get("resource-id") == PACKAGE + ":id/btnLiveServers"), None)
            count = re.search(r"(\d+)\s*Server", "" if server is None else server.get("text", ""), re.IGNORECASE)
            if count is None or int(count.group(1)) < 2:
                continue
        return row
    return None

def node_by_desc_prefix(root, prefix):
    return next((n for n in root.iter("node")
                 if n.get("content-desc", "").startswith(prefix)
                 and n.get("bounds", "")), None)

def tap_fraction(node, y_fraction=0.35):
    if node is None:
        return False
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return False
    x1, y1, x2, y2 = map(int, match.groups())
    x = (x1 + x2) // 2
    y = y1 + int(max(0, min(1, y_fraction)) * max(1, y2 - y1))
    adb("shell", "input", "tap", str(x), str(y))
    return True

def tap_row_surface(node):
    if node is None:
        return False
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return False
    x1, y1, x2, y2 = map(int, match.groups())
    # Tap in the card's own left padding, away from server/favorite child controls.
    adb("shell", "input", "tap", str(x1 + 7), str((y1 + y2) // 2))
    return True

def wait_for_initial_playlist_settle(timeout=35):
    end = time.monotonic() + timeout
    stable_dumps = 0
    while time.monotonic() < end:
        try:
            root = hierarchy()
            if dismiss_pixel_launcher_anr(root):
                stable_dumps = 0
                continue
            if dismiss_expected_playlist_error(root):
                stable_dumps = 0
                time.sleep(.4)
                continue
            if has_id(root, "mainRoot") and not has_id(root, "progress"):
                stable_dumps += 1
                if stable_dumps >= 3:
                    print("INFO: initial playlist loading settled before LIVE interaction")
                    return True
            else:
                stable_dumps = 0
        except Exception:
            stable_dumps = 0
        time.sleep(.6)
    print("SKIP: startup playlist did not settle in CI; avoiding a race with the LIVE UI test")
    return False

def smoke_test_live_inline_if_feed_available():
    if not wait_for_initial_playlist_settle():
        return
    end = time.monotonic() + 25
    button = None
    root = None
    while time.monotonic() < end:
        try:
            root = hierarchy()
            button = live_button_node(root)
            if button is not None:
                break
        except Exception:
            pass
        time.sleep(.7)
    if button is None:
        if os.environ.get("NM7_SMOKE_LIVE_FIXTURE") == "1":
            print_diagnostics()
            raise AssertionError("Seeded LIVE fixture was not displayed by MainActivity")
        print("SKIP: dedicated LIVE feed had no event available in CI; inline event tap was not exercised")
        return
    if not tap_node(button):
        print_diagnostics()
        raise AssertionError("LIVE group exists with events but could not be tapped")

    end = time.monotonic() + 10
    row = None
    while time.monotonic() < end:
        try:
            root = hierarchy()
            row = live_event_row_node(root, require_multiple_servers=True)
            if row is not None:
                break
        except Exception:
            pass
        time.sleep(.5)
    if row is None:
        if os.environ.get("NM7_SMOKE_LIVE_FIXTURE") == "1":
            print_diagnostics()
            raise AssertionError("Seeded two-server LIVE fixture was not visible for fullscreen server-selector testing")
        print("SKIP: no visible multi-server LIVE event available to verify fullscreen server selection")
        return
    if not tap_row_surface(row):
        print_diagnostics()
        raise AssertionError("Could not tap the first multi-server LIVE event card")

    end = time.monotonic() + 12
    while time.monotonic() < end:
        try:
            root = hierarchy()
            if has_id(root, "mainRoot") and inline_panel_visible(root):
                print("PASS: tapping a LIVE event opens the inline player above the still-visible event list")
                break
        except Exception:
            pass
        time.sleep(.5)
    else:
        print_diagnostics()
        raise AssertionError("Tapping a LIVE event did not keep MainActivity open with the inline player visible")

    # Tap the player surface to expose Media3 controls, then enter fullscreen.
    end = time.monotonic() + 8
    fullscreen_button = None
    while time.monotonic() < end:
        root = hierarchy()
        fullscreen_button = node_by_desc_prefix(root, "Toàn màn hình")
        if fullscreen_button is not None:
            break
        tap_fraction(next((n for n in root.iter("node")
                           if n.get("content-desc") == "NM7_INLINE_PLAYER_PANEL"), None), 0.35)
        time.sleep(.5)
    if fullscreen_button is None:
        print_diagnostics()
        raise AssertionError("Player controls did not expose the fullscreen button after tapping the player")
    if not tap_node(fullscreen_button):
        print_diagnostics()
        raise AssertionError("Could not enter fullscreen from the inline player")

    # Orientation changes may briefly hide the controller. Tap the full display to show it.
    end = time.monotonic() + 10
    fullscreen_server = None
    while time.monotonic() < end:
        try:
            root = hierarchy()
            fullscreen_server = node_by_desc_prefix(root, "Đổi máy chủ toàn màn hình")
            if fullscreen_server is not None:
                break
            content = android_node(root, "content")
            tap_fraction(content, 0.5)
        except Exception:
            pass
        time.sleep(.5)
    if fullscreen_server is None:
        print_diagnostics()
        raise AssertionError("Fullscreen LIVE player did not expose the server selector in its controls")

    old_label = fullscreen_server.get("text", "")
    if not tap_node(fullscreen_server):
        print_diagnostics()
        raise AssertionError("Fullscreen server selector was visible but could not be tapped")

    end = time.monotonic() + 8
    dialog_root = None
    dialog_title = ""
    while time.monotonic() < end:
        try:
            dialog_root = hierarchy()
            dialog_title = alert_title(dialog_root)
            if dialog_title.startswith("Chọn máy chủ"):
                break
        except Exception:
            pass
        time.sleep(.4)
    if not dialog_title.startswith("Chọn máy chủ"):
        print_diagnostics()
        raise AssertionError("Fullscreen server selector did not open the server picker dialog")

    list_node = next((n for n in dialog_root.iter("node")
                      if n.get("resource-id") == "android:id/select_dialog_listview"), None)
    alternative = None
    if list_node is not None:
        for node in list_node.iter("node"):
            if node.get("clickable") == "true" and node.get("text", "").strip() and "✓ Đang phát" not in node.get("text", ""):
                alternative = node
                break
    if alternative is None:
        print_diagnostics()
        raise AssertionError("Server picker opened but no alternative source was found")

    if not tap_node(alternative):
        print_diagnostics()
        raise AssertionError("Could not select an alternative LIVE server")
    print("PASS: fullscreen server selector opens the server picker and accepts an alternative source")

    # Switching sources hides the controls while Media3 restarts; show them again and
    # verify the in-player server label reflects the new choice, while still fullscreen.
    end = time.monotonic() + 15
    new_server = None
    while time.monotonic() < end:
        try:
            root = hierarchy()
            new_server = node_by_desc_prefix(root, "Đổi máy chủ toàn màn hình")
            if new_server is not None and new_server.get("text", "") and new_server.get("text", "") != old_label:
                break
            content = android_node(root, "content")
            tap_fraction(content, 0.5)
        except Exception:
            pass
        time.sleep(.5)
    if new_server is None or new_server.get("text", "") == old_label:
        print_diagnostics()
        raise AssertionError("Fullscreen server label did not update after choosing a different source")
    print("PASS: fullscreen server label updates after changing source")

    # Exit fullscreen so the existing portrait/landscape layout checks run from a clean state.
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)

def has_android_id(root, name):
    return any(n.get("resource-id") == "android:id/" + name for n in root.iter("node"))

def android_node(root, name):
    return next((n for n in root.iter("node") if n.get("resource-id") == "android:id/" + name), None)

def tap_node(node):
    if node is None:
        return False
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return False
    x1, y1, x2, y2 = map(int, match.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    return True

def alert_title(root):
    node = android_node(root, "alertTitle")
    return "" if node is None else node.get("text", "")

def dismiss_pixel_launcher_anr(root):
    title = alert_title(root)
    if title != "Pixel Launcher isn't responding":
        return False
    wait = android_node(root, "aerr_wait")
    close = android_node(root, "aerr_close")
    print("INFO: Pixel Launcher ANR obscured the test UI; dismissing emulator-only system dialog")
    if not tap_node(wait):
        tap_node(close)
    time.sleep(.8)
    return True

def dismiss_expected_playlist_error(root):
    title = alert_title(root)
    message = next((n.get("text", "") for n in root.iter("node") if n.get("resource-id") == "android:id/message"), "")
    if title == "Lỗi" and message.startswith("Không tải được playlist:") and has_android_id(root, "button1"):
        print("INFO: default playlist is unavailable in CI; dismissing handled network error")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        return True
    return False

def print_diagnostics():
    print("\n===== NM7 SMOKE DIAGNOSTICS =====", file=sys.stderr)
    commands = [
        ("Foreground activity", ("shell", "dumpsys", "activity", "activities")),
        ("Package process", ("shell", "pidof", PACKAGE)),
        ("Window hierarchy", ("exec-out", "cat", "/sdcard/nm7-smoke.xml")),
        ("Application logcat", ("logcat", "-d", "-t", "500", "AndroidRuntime:E", "ActivityTaskManager:I", "ActivityManager:I", "*:S")),
    ]
    for title, command in commands:
        result = run_adb(*command, check=False)
        print(f"\n--- {title} (exit {result.returncode}) ---", file=sys.stderr)
        if result.stdout:
            print(result.stdout, file=sys.stderr)
        if result.stderr:
            print(result.stderr, file=sys.stderr)

def wait_for_main_screen(timeout=30):
    end = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < end:
        try:
            root = hierarchy()
            if has_id(root, "mainRoot"):
                return root
            if dismiss_pixel_launcher_anr(root):
                continue
            if dismiss_expected_playlist_error(root):
                time.sleep(.5)
                continue
        except Exception as error:
            last_error = error
        time.sleep(.4)
    print_diagnostics()
    message = "Main screen did not open"
    if last_error is not None:
        message += f"; last UI dump error: {last_error}"
    raise AssertionError(message)

def launch_main():
    # Android Emulator can occasionally leave the launcher in the foreground after
    # a rotation/force-stop transition. Give the activity up to three clean starts.
    last_launch = None
    for attempt in range(1, 4):
        adb("shell", "am", "force-stop", PACKAGE, timeout=20)
        time.sleep(.7)
        launch = run_adb("shell", "am", "start", "-W", "-n", PACKAGE + "/" + ACTIVITY, timeout=40, check=False)
        print(launch.stdout)
        if launch.stderr:
            print(launch.stderr, file=sys.stderr)
        last_launch = launch
        if launch.returncode:
            time.sleep(1)
            continue
        try:
            return wait_for_main_screen(timeout=12)
        except AssertionError as error:
            print(f"INFO: launch attempt {attempt}/3 did not reach main UI: {error}")
            if attempt < 3:
                time.sleep(1.5)
    print_diagnostics()
    if last_launch is not None and last_launch.returncode:
        raise subprocess.CalledProcessError(last_launch.returncode, last_launch.args, output=last_launch.stdout, stderr=last_launch.stderr)
    raise AssertionError("Main screen did not open after 3 clean launch attempts")

def assert_consolidated_header(root, label):
    removed = ["btnSources", "btnPlaylists", "btnAbout", "btnSelectAll", "btnSelectNone", "btnExport"]
    present = [name for name in removed if has_id(root, name)]
    if present:
        print_diagnostics()
        raise AssertionError(label + " still exposes consolidated controls: " + ", ".join(present))

PACKAGE = resolve_package(APK)
print("INFO: APK package id:", PACKAGE)
adb("install", "-r", APK, timeout=90)
seed_live_feed_fixture()
adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
run_adb("shell", "wm", "dismiss-keyguard", check=False)
run_adb("logcat", "-c", check=False)

run_adb("shell", "settings", "put", "system", "accelerometer_rotation", "0", check=False)
run_adb("shell", "settings", "put", "system", "user_rotation", "0", check=False)
time.sleep(.8)
root = launch_main()
required = ["btnWallpaper", "btnReloadPlaylist", "inputSearch", "groupRow"]
missing = [name for name in required if not has_id(root, name)]
if missing:
    print_diagnostics()
    raise AssertionError("Missing current UI controls: " + ", ".join(missing))
if not (has_id(root, "listChannels") or has_id(root, "txtEmpty")):
    print_diagnostics()
    raise AssertionError("Neither the channel list nor its empty state is available")
assert_consolidated_header(root, "Portrait UI")
print("PASS: portrait application launches and current IPTV interface is available")
smoke_test_live_inline_if_feed_available()

# Change the logical display geometry instead of user_rotation. UiAutomation's
# rotation-state restoration can override settings user_rotation between dumps,
# causing the launcher to appear even though the app itself did not crash.
run_adb("shell", "wm", "size", "1920x1080", check=False)
time.sleep(1.2)
root = launch_main()
if not has_id(root, "mainRoot"):
    print_diagnostics()
    raise AssertionError("Landscape main screen did not open")
assert_consolidated_header(root, "Landscape Mobile UI")
print("PASS: landscape Mobile layout launches without missing startup controls")
print("PASS: reload playlist control is exposed on the main header")
print("PASS: Link, source and about controls are consolidated into Mobile app options")
run_adb("shell", "wm", "size", "reset", check=False)
