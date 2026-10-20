#!/usr/bin/env python3
"""Fail the build if the active SmartTube patch chain bypasses YouTube/IPTV handoff guards."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise SystemExit(f"Missing handoff source: {relative}")
    return path.read_text(encoding="utf-8")


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise SystemExit(f"Missing P0 handoff guard: {label}")


shared = read("app/src/main/java/vn/phuong/iptvplayer/SharedPlaybackSession.java")
require(shared, "private static long tabSwitchGeneration;", "independent tab generation")
require(shared, "private static long handoffGeneration;", "independent audio generation")
require(shared, "private static long visualGeneration;", "independent visual generation")
require(shared, "visualTabSwitchGeneration == tabSwitchGeneration", "stale visual callback invalidation")
require(shared, "audioTabSwitchGeneration == tabSwitchGeneration", "stale audio callback invalidation")
require(shared, "completeAudioHandoff(Context context, long id, String owner)", "audio transfer commit")
require(shared, "completeVisualHandoff(Context context, long id, String type)", "visual transfer commit")

app = read("app/src/main/java/vn/phuong/iptvplayer/MobileNm7Application.java")
require(app, 'getMethod("suspendForNm7Iptv", long.class)', "generation-bound IPTV suspend")
require(app, "if (!Boolean.TRUE.equals(result)) return false;", "fail-closed IPTV suspend result")
require(app, "if (!PlayerActivity.releaseForYoutube()) return false;", "fail-closed fullscreen IPTV release")
require(app, "if (!MobileInlinePlayerProviderV2.releaseForYoutube()) return false;", "fail-closed inline IPTV release")

mini = read("app/src/main/java/vn/phuong/iptvplayer/MobileMiniPlayer.java")
require(mini, 'getMethod("attachNm7MiniPlayer", View.class, View.class, long.class)', "ID-bound mini attach")
require(mini, "currentTabSwitchGeneration", "captured tab generation")

v37 = read("scripts/patch-mobile-v37.py")
require(v37, 'nm7ClaimYoutubeAudio("player_ready")', "YouTube READY audio ownership")
require(v37, "if (mNm7TargetRestoreWaiting)", "keep restore playback muted until frame")
if "getMethod(\"pauseIptvForYoutube\").invoke(null)" in v37:
    raise SystemExit("Legacy unversioned IPTV audio handoff remains in v37")

v106 = read("scripts/patch-mobile-v106.py")
require(v106, "mNm7DesiredVolume = mPlayer.getVolume();", "preserve user volume before initial mute")
require(v106, "mPlayer.setVolume(0f);", "mute YouTube before ownership transfer")
require(v106, "mNm7OwnsPlayback ? mNm7DesiredVolume : 0f", "mute until audio owner commit")
for obsolete in (
    'replace_method(t, "public static void attachNm7MiniPlayer(',
    'replace_method(t, "private void completeNm7RestoreOnResume()',
    'replace_method(t, "public static void suspendForNm7Iptv()'):
    if obsolete in v106:
        raise SystemExit("v106 replaces a generation-aware lifecycle method")

v59 = read("scripts/patch-mobile-v59.py")
require(v59, "owner.mNm7RestoreFailed", "manual retry returns through frame gate")
require(v59, 'owner.beginNm7TargetRestore(target, "user_retry", true)', "manual retry creates a fresh visual generation")

include = read("scripts/smarttube-playback-mobile.java.inc")
require(include, "private boolean mNm7RestoreFailed;", "restore failure state is declared")
require(include, "private boolean mNm7ResumeAfterTarget;", "resume intent state is declared")
if "sNm7ResumeAfterTarget" in include:
    raise SystemExit("P0 handoff guard uses the wrong resume-intent field")
require(include, "isCurrentVisualHandoff", "first-frame callback checks active visual ID")
require(include, "onRenderedFirstFrame()", "first-frame observation")
require(include, "nm7TargetSurfaceReady(expectedTarget)", "destination surface readiness")
require(include, "expectedPlayer.setPlayWhenReady(false);", "fail-closed playback stop")
require(include, "rendered_buffers=", "timeout decoder diagnostics")
require(include, "public static boolean suspendForNm7Iptv(long handoffId)", "generation-bound YouTube suspend")
frame_gate_start = include.index("private void armNm7FirstFrameResume(final long generation)")
frame_gate_end = include.index("private void cancelNm7TargetRestore(boolean resume)", frame_gate_start)
if "sNm7Mini" in include[frame_gate_start:frame_gate_end]:
    raise SystemExit("First-frame callback rejects the active mini-player target")

generated_path = ROOT / "third_party/SmartTube-droid/smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui/playback/PlaybackActivity.java"
if not generated_path.is_file():
    raise SystemExit("SmartTube checkout missing; run the pinned v37/v106 patch chain before verification")
generated = generated_path.read_text(encoding="utf-8")
require(generated, "attachNm7MiniPlayer(android.view.View previousSurface, android.view.View nextSurface,", "generated ID-bound mini attach")
require(generated, "suspendForNm7Iptv(long handoffId)", "generated ID-bound suspend")
require(generated, 'nm7ClaimYoutubeAudio("player_ready")', "generated READY audio transfer")
require(generated, "if (mNm7TargetRestoreWaiting)", "generated silent frame restore")
require(generated, "mNm7DesiredVolume = mPlayer.getVolume();", "generated initial mute")
require(generated, "onRenderedFirstFrame()", "generated first-frame gate")
if 'getMethod("attachNm7MiniPlayer", View.class, View.class)' in generated:
    raise SystemExit("Generated SmartTube still uses the stale two-argument mini attach")

print("YouTube/IPTV P0 handoff guards verified against source and generated SmartTube patch output")

