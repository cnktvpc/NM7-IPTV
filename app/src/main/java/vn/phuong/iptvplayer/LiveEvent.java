package vn.phuong.iptvplayer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A logical LIVE event built from the user's existing M3U playlist.
 * Several playlist entries and #NM7-ALT-URL lines may represent alternate servers
 * for the same match. We group them for display without changing the saved playlist.
 */
public final class LiveEvent {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SERVER_SUFFIX = Pattern.compile(
            "(?i)\\s*(?:[-|•:]\\s*)?(?:server|srv|nguon|nguồn|may chu|máy chủ)\\s*#?\\s*\\d+\\s*(?:\\([^)]*\\)|\\[[^]]*])?\\s*$");
    private static final Pattern SERVER_ROLE_SUFFIX = Pattern.compile(
            "(?i)\\s*(?:[-|•:]\\s*)?(?:backup|du phong|dự phòng|primary|main|chinh|chính)\\s*$");
    private static final Pattern LIVE_TAG = Pattern.compile(
            "(?i)(?:^\\s*\\[?live\\]?\\s*[:|–-]?\\s*|\\s*[|•–-]\\s*live\\s*$|\\s*[\\[(]\\s*live\\s*[)\\]]\\s*$)");

    private final String id;
    private final String title;
    private final String group;
    private final List<Channel> sources;
    private final int selectedIndex;

    private LiveEvent(String id, String title, String group, List<Channel> sources, int selectedIndex) {
        this.id = id;
        this.title = title;
        this.group = group;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.selectedIndex = Math.max(0, Math.min(selectedIndex, sources.size() - 1));
    }

    public String id() { return id; }
    public String title() { return title; }
    public String group() { return group; }
    public List<Channel> sources() { return sources; }
    public int selectedIndex() { return selectedIndex; }
    public Channel primary() { return sources.get(selectedIndex); }

    public LiveEvent withPreferredUrl(String url) {
        if (url == null || url.trim().isEmpty()) return this;
        for (int i = 0; i < sources.size(); i++) {
            if (url.equals(sources.get(i).url())) {
                return new LiveEvent(id, title, group, sources, i);
            }
        }
        return this;
    }

    public static List<LiveEvent> fromChannels(List<Channel> channels) {
        Map<String, Builder> grouped = new LinkedHashMap<>();
        if (channels == null) return Collections.emptyList();
        for (Channel channel : channels) {
            if (channel == null || !isLiveChannel(channel)) continue;
            String title = displayTitle(channel.name());
            String key = normalizeTitle(title);
            if (key.isEmpty()) key = "event-" + channel.identityKey();
            Builder builder = grouped.get(key);
            if (builder == null) {
                builder = new Builder(key, title, channel.group());
                grouped.put(key, builder);
            }
            for (Channel source : expandSources(channel, title)) {
                if (builder.seen.add(source.identityKey())) builder.sources.add(source);
            }
        }
        List<LiveEvent> result = new ArrayList<>();
        for (Builder b : grouped.values()) {
            if (!b.sources.isEmpty()) result.add(new LiveEvent(b.id, b.title, b.group, b.sources, 0));
        }
        return result;
    }

    /** A channel belongs to Live when its group explicitly says Live/Event/Sự kiện.
     * Sports channels outside those groups are only included if their name resembles a match,
     * which avoids treating ordinary sports TV channels as events.
     */
    public static boolean isLiveChannel(Channel channel) {
        String group = normalizeText(channel.group());
        String name = normalizeText(channel.name());
        if (containsAny(group, " live ", "event", "su kien", "sukien", "events")) return true;
        if (containsAny(name, " live ", "[live]", "(live)", "truc tiep")) return true;
        boolean sportsGroup = containsAny(" " + group + " ",
                " the thao ", " bong da ", " football ", " soccer ", " sports ", " sport ");
        boolean matchup = Pattern.compile("(?i)\\bvs?\\.?\\b|\\bversus\\b").matcher(name).find();
        return sportsGroup && matchup;
    }

    /** Strips transport/server suffixes, but leaves the actual event title intact. */
    public static String displayTitle(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = SERVER_SUFFIX.matcher(value).replaceFirst("");
        value = SERVER_ROLE_SUFFIX.matcher(value).replaceFirst("");
        value = LIVE_TAG.matcher(value).replaceAll("");
        value = value.replaceAll("(?i)\\s*\\((?:hd|fhd|uhd|4k|1080p|720p)\\)\\s*$", "");
        return value.trim().replaceAll("\\s{2,}", " ");
    }

    public static String normalizeTitle(String title) {
        String value = normalizeText(displayTitle(title));
        value = value.replaceAll("\\b(?:hd|fhd|uhd|4k|1080p|720p|server|srv|backup|primary|main)\\b", " ");
        return NON_WORD.matcher(value).replaceAll(" ").trim().replaceAll("\\s{2,}", " ");
    }

    private static String normalizeText(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT)
                .replace('đ', 'd').trim().replaceAll("\\s{2,}", " ");
    }

    private static boolean containsAny(String text, String... needles) {
        String value = " " + text + " ";
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static List<Channel> expandSources(Channel channel, String displayName) {
        List<Channel> result = new ArrayList<>();
        result.add(copyWithUrl(channel, channel.url(), displayName));
        Set<String> already = new LinkedHashSet<>();
        already.add(channel.url());
        for (String option : channel.options()) {
            if (option == null) continue;
            String lower = option.trim().toLowerCase(Locale.ROOT);
            String candidate = null;
            if (lower.startsWith("#nm7-alt-url:")) candidate = option.trim().substring("#nm7-alt-url:".length()).trim();
            else if (lower.startsWith("#nm7-alt-url=")) candidate = option.trim().substring("#nm7-alt-url=".length()).trim();
            if (candidate == null || candidate.isEmpty() || !M3uParser.isNetworkUrl(candidate)
                    || !already.add(candidate)) continue;
            result.add(copyWithUrl(channel, candidate, displayName));
        }
        return result;
    }

    private static Channel copyWithUrl(Channel source, String url, String title) {
        Channel copy = new Channel(title, source.group(), url, source.tvgLogo(), source.logoUrl(),
                source.iconUrl(), source.tvgId(), source.headers());
        copy.setOriginalExtInf(source.originalExtInf());
        for (String option : source.options()) {
            if (option == null || option.trim().toLowerCase(Locale.ROOT).startsWith("#nm7-alt-url")) continue;
            copy.options().add(option);
        }
        return copy;
    }

    private static final class Builder {
        final String id;
        final String title;
        final String group;
        final List<Channel> sources = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        Builder(String id, String title, String group) {
            this.id = id;
            this.title = title;
            this.group = group;
        }
    }
}
