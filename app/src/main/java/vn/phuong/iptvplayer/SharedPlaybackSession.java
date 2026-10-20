package vn.phuong.iptvplayer;

import android.content.Context;
import android.os.Bundle;

/**
 * NM7 Mobile 1.10.26 shared playback owner.
 * Single playback-session owner for Mobile.
 *
 * Only one playback engine is allowed to be alive at a time. IPTV state is retained
 * here while SmartTube owns the foreground player, then restored when IPTV returns.
 */
public final class SharedPlaybackSession {
    public static final String TAB_IPTV = "iptv";
    public static final String TAB_YOUTUBE = "youtube";

    private static final String PREFS = "nm7_shared_playback";
    private static final String KEY_TAB = "tab";
    private static final String KEY_YOUTUBE_BACKGROUND = "youtube_background";
    private static final String KEY_NAME = "name";
    private static final String KEY_URL = "url";
    private static final String KEY_MIME = "mime";
    private static final String KEY_POSITION = "position";
    private static final String KEY_PLAYING = "playing";
    private static final String KEY_HEADERS = "headers";
    private static final String KEY_OPTIONS = "options";

    // Playback ownership is process-local: no player or callback survives process death.
    private static long tabSwitchGeneration;
    private static long handoffGeneration;
    private static long visualGeneration;
    private static long audioTabSwitchGeneration;
    private static long visualTabSwitchGeneration;
    private static long tabSwitchStartedAtMs;
    private static long audioHandoffStartedAtMs;
    private static long visualHandoffStartedAtMs;
    private static String audioOwner = TAB_IPTV;
    private static String audioHandoffFromOwner = TAB_IPTV;
    private static String pendingAudioOwner = "";
    private static String audioHandoffType = "";
    private static String visualHandoffType = "";

    private SharedPlaybackSession() {}

    /** Start a tab transition and invalidate callbacks from the previous transition. */
    public static synchronized long beginTabSwitch(Context context, String tab, String type) {
        if (!TAB_IPTV.equals(tab) && !TAB_YOUTUBE.equals(tab)) return -1L;
        String previousTab = tab(context);
        long id = ++tabSwitchGeneration;
        tabSwitchStartedAtMs = android.os.SystemClock.elapsedRealtime();
        pendingAudioOwner = "";
        prefs(context).edit().putString(KEY_TAB, tab).apply();
        logHandoff("handoff_start", id, type == null ? "tab_switch" : type, audioOwner, tab,
                "kind=tab previous_tab=" + previousTab);
        return id;
    }

    /** Start a YouTube view/surface transition without changing the selected tab. */
    public static synchronized long beginVisualHandoff(Context context, String type) {
        if (!TAB_YOUTUBE.equals(tab(context))) return -1L;
        long id = ++visualGeneration;
        visualTabSwitchGeneration = tabSwitchGeneration;
        visualHandoffStartedAtMs = android.os.SystemClock.elapsedRealtime();
        visualHandoffType = type == null ? "youtube_view" : type;
        logHandoff("handoff_start", id, visualHandoffType, audioOwner, TAB_YOUTUBE,
                "kind=visual");
        return id;
    }

    /** Begin the audio-owner transfer only after the destination player is ready to take over. */
    public static synchronized long beginAudioHandoff(Context context, String owner, String type) {
        if ((!TAB_IPTV.equals(owner) && !TAB_YOUTUBE.equals(owner)) || !owner.equals(tab(context))) {
            return -1L;
        }
        long id = ++handoffGeneration;
        audioTabSwitchGeneration = tabSwitchGeneration;
        audioHandoffStartedAtMs = android.os.SystemClock.elapsedRealtime();
        audioHandoffType = type == null ? "audio_owner" : type;
        audioHandoffFromOwner = audioOwner;
        pendingAudioOwner = owner;
        logHandoff("handoff_start", id, audioHandoffType, audioOwner, owner,
                "kind=audio");
        return id;
    }

    public static synchronized long currentHandoffGeneration() {
        return handoffGeneration;
    }

    public static synchronized long currentTabSwitchGeneration() {
        return tabSwitchGeneration;
    }

    public static synchronized String audioOwner() {
        return audioOwner;
    }

    public static synchronized boolean isCurrentTabHandoff(Context context, long id, String targetTab) {
        return id > 0L && id == tabSwitchGeneration && targetTab.equals(tab(context));
    }

    public static synchronized boolean completeTabSwitch(Context context, long id, String targetTab) {
        if (!isCurrentTabHandoff(context, id, targetTab)) return false;
        logHandoff("handoff_completed", id, "tab_switch", audioOwner, targetTab,
                "kind=tab duration_ms=" + (android.os.SystemClock.elapsedRealtime() - tabSwitchStartedAtMs));
        return true;
    }

    public static synchronized boolean isCurrentVisualHandoff(Context context, long id) {
        return id > 0L && id == visualGeneration && visualTabSwitchGeneration == tabSwitchGeneration
                && TAB_YOUTUBE.equals(tab(context));
    }

    public static synchronized boolean isCurrentAudioHandoff(Context context, long id, String targetOwner) {
        return id > 0L && id == handoffGeneration && audioTabSwitchGeneration == tabSwitchGeneration
                && targetOwner.equals(tab(context))
                && targetOwner.equals(pendingAudioOwner);
    }

    public static synchronized boolean completeAudioHandoff(Context context, long id, String owner) {
        if (!isCurrentAudioHandoff(context, id, owner)) return false;
        audioOwner = owner;
        pendingAudioOwner = "";
        logHandoff("handoff_completed", id, audioHandoffType, audioHandoffFromOwner, owner,
                "kind=audio duration_ms=" + (android.os.SystemClock.elapsedRealtime() - audioHandoffStartedAtMs));
        return true;
    }

    public static synchronized boolean completeVisualHandoff(Context context, long id, String type) {
        if (!isCurrentVisualHandoff(context, id) || !pendingAudioOwner.isEmpty()) return false;
        logHandoff("handoff_completed", id, type == null ? visualHandoffType : type, audioOwner, TAB_YOUTUBE,
                "kind=visual duration_ms=" + (android.os.SystemClock.elapsedRealtime() - visualHandoffStartedAtMs));
        return true;
    }

    private static void logHandoff(String event, long id, String type, String from, String to,
                                   String detail) {
        android.util.Log.i("NM7Playback", event + " handoff_id=" + id + " type=" + type
                + " from=" + from + " to=" + to + " " + detail);
    }

    public static synchronized void setTab(Context context, String tab) {
        prefs(context).edit().putString(KEY_TAB, tab).apply();
    }

    public static synchronized String tab(Context context) {
        return prefs(context).getString(KEY_TAB, TAB_IPTV);
    }

    /** True only while the current process is intentionally keeping YouTube alive behind HOME. */
    public static synchronized void setYoutubeBackground(Context context, boolean active) {
        prefs(context).edit()
                .putBoolean(KEY_YOUTUBE_BACKGROUND, active)
                .apply();
    }

    public static synchronized boolean isYoutubeBackground(Context context) {
        return prefs(context).getBoolean(KEY_YOUTUBE_BACKGROUND, false);
    }

    /** Clear transient YouTube-background state when a new app process is created. */
    public static synchronized void clearTransientState(Context context) {
        // No Activity/player survives process death. A persisted YouTube tab otherwise
        // makes the inline IPTV provider skip its first attach on the launcher screen.
        prefs(context).edit().putBoolean(KEY_YOUTUBE_BACKGROUND, false)
                .putString(KEY_TAB, TAB_IPTV).apply();
        tabSwitchGeneration = 0L;
        handoffGeneration = 0L;
        visualGeneration = 0L;
        audioTabSwitchGeneration = 0L;
        visualTabSwitchGeneration = 0L;
        tabSwitchStartedAtMs = 0L;
        audioHandoffStartedAtMs = 0L;
        visualHandoffStartedAtMs = 0L;
        audioOwner = TAB_IPTV;
        audioHandoffFromOwner = TAB_IPTV;
        pendingAudioOwner = "";
        audioHandoffType = "";
        visualHandoffType = "";
    }

    public static synchronized void saveIptv(Context context, String name, String url,
                                              String mime, Bundle headers, java.util.ArrayList<String> options,
                                              long position, boolean playing) {
        android.content.SharedPreferences.Editor e = prefs(context).edit()
                .putString(KEY_NAME, name == null ? "" : name)
                .putString(KEY_URL, url == null ? "" : url)
                .putString(KEY_MIME, mime == null ? "" : mime)
                .putLong(KEY_POSITION, position)
                .putBoolean(KEY_PLAYING, playing);
        e.putString(KEY_HEADERS, encodeBundle(headers));
        e.putString(KEY_OPTIONS, options == null ? "" : android.text.TextUtils.join("\u001f", options));
        e.apply();
    }

    public static synchronized State loadIptv(Context context) {
        android.content.SharedPreferences p = prefs(context);
        String url = p.getString(KEY_URL, "");
        if (url == null || url.isEmpty()) return null;
        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        String rawOptions = p.getString(KEY_OPTIONS, "");
        if (rawOptions != null && !rawOptions.isEmpty()) {
            for (String item : rawOptions.split("\\u001f", -1)) if (!item.isEmpty()) options.add(item);
        }
        return new State(p.getString(KEY_NAME, "IPTV"), url, p.getString(KEY_MIME, ""),
                decodeBundle(p.getString(KEY_HEADERS, "")), options,
                p.getLong(KEY_POSITION, 0L), p.getBoolean(KEY_PLAYING, true));
    }

    public static synchronized void clearIptv(Context context) {
        prefs(context).edit().remove(KEY_NAME).remove(KEY_URL).remove(KEY_MIME)
                .remove(KEY_POSITION).remove(KEY_PLAYING).remove(KEY_HEADERS).remove(KEY_OPTIONS).apply();
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String encodeBundle(Bundle bundle) {
        if (bundle == null || bundle.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String key : bundle.keySet()) {
            String value = bundle.getString(key);
            if (value == null) continue;
            if (out.length() > 0) out.append("\u001e");
            out.append(android.util.Base64.encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP));
            out.append(':');
            out.append(android.util.Base64.encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP));
        }
        return out.toString();
    }

    private static Bundle decodeBundle(String raw) {
        Bundle out = new Bundle();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("\\u001e")) {
            int split = pair.indexOf(':');
            if (split <= 0) continue;
            try {
                String key = new String(android.util.Base64.decode(pair.substring(0, split), android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
                String value = new String(android.util.Base64.decode(pair.substring(split + 1), android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
                out.putString(key, value);
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    public static final class State {
        public final String name, url, mime;
        public final Bundle headers;
        public final java.util.ArrayList<String> options;
        public final long position;
        public final boolean playing;

        State(String name, String url, String mime, Bundle headers, java.util.ArrayList<String> options,
              long position, boolean playing) {
            this.name = name;
            this.url = url;
            this.mime = mime;
            this.headers = headers;
            this.options = options;
            this.position = position;
            this.playing = playing;
        }
    }
}
