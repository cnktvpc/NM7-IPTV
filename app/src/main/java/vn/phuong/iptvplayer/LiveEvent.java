package vn.phuong.iptvplayer;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One logical sports fixture and its independent stream choices. */
public final class LiveEvent {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SERVER_SUFFIX = Pattern.compile(
            "(?i)\\s*(?:[-|•:]\\s*)?(?:server|srv|nguon|máy chủ|may chu)\\s*#?\\s*\\d+\\s*(?:\\([^)]*\\)|\\[[^]]*])?\\s*$");
    private static final Pattern SERVER_ROLE_SUFFIX = Pattern.compile(
            "(?i)\\s*(?:[-|•:]\\s*)?(?:backup|du phong|primary|main)\\s*$");
    private static final Pattern LIVE_TAG = Pattern.compile(
            "(?i)(?:^\\s*\\[?live\\]?\\s*[:|–-]?\\s*|\\s*[|•–-]\\s*live\\s*$|\\s*[\\[(]\\s*live\\s*[)\\]]\\s*$)");
    private static final Pattern TIME_DATE = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)\\s+(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?");
    private static final Pattern MATCH_SPLIT = Pattern.compile("(?i)\\s+(?:vs?\\.?|versus)\\s+");
    private static final ZoneId FEED_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long LIVE_MAX_AGE_MS = 180L * 60L * 1000L;
    private static final long PRE_KICKOFF_TOLERANCE_MS = 15L * 60L * 1000L;
    private static final long SOURCE_TIME_SKEW_MS = 3L * 60L * 60L * 1000L;

    private final String id;
    private final String title;
    private final String group;
    private final List<Channel> sources;
    private final List<String> sourceLabels;
    private final int selectedIndex;
    private final long kickoffMillis;
    private final String sport;

    private LiveEvent(String id, String title, String group, List<Channel> sources,
                      List<String> sourceLabels, int selectedIndex, long kickoffMillis, String sport) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.group = group == null ? "" : group;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.sourceLabels = Collections.unmodifiableList(new ArrayList<>(sourceLabels));
        this.selectedIndex = sources.isEmpty() ? 0 : Math.max(0, Math.min(selectedIndex, sources.size() - 1));
        this.kickoffMillis = kickoffMillis;
        this.sport = sport == null ? "Thể thao" : sport;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String group() { return group; }
    public List<Channel> sources() { return sources; }
    public List<String> sourceLabels() { return sourceLabels; }
    public String sourceLabel(int index) {
        if (index < 0 || index >= sourceLabels.size()) return "Server " + (index + 1);
        String value = sourceLabels.get(index);
        return value == null || value.trim().isEmpty() ? "Server " + (index + 1) : value;
    }
    public int selectedIndex() { return selectedIndex; }
    public Channel primary() { return sources.get(selectedIndex); }
    public long kickoffMillis() { return kickoffMillis; }
    public String sport() { return sport; }
    public int indexOfUrl(String url) {
        if (url == null) return -1;
        for (int i = 0; i < sources.size(); i++) if (url.equals(sources.get(i).url())) return i;
        return -1;
    }
    public boolean hasSourceUrl(String url) { return indexOfUrl(url) >= 0; }

    /** Restore an event whose sources were serialized into a player intent. */
    public static LiveEvent restored(String id, String title, String group, List<Channel> sources,
                                     List<String> labels, long kickoffMillis, String sport,
                                     String selectedUrl) {
        if (sources == null || sources.isEmpty()) return null;
        List<String> safeLabels = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            String label = labels != null && i < labels.size() ? labels.get(i) : "";
            safeLabels.add(label == null || label.trim().isEmpty() ? "Server " + (i + 1) : label);
        }
        LiveEvent event = new LiveEvent(id, title, group, sources, safeLabels, 0, kickoffMillis, sport);
        return event.withPreferredUrl(selectedUrl);
    }

    public LiveEvent withPreferredUrl(String url) {
        int index = indexOfUrl(url);
        return index < 0 ? this : new LiveEvent(id, title, group, sources, sourceLabels, index, kickoffMillis, sport);
    }

    /** Legacy helper retained for non-feed M3U regression tests only. */
    public static List<LiveEvent> fromChannels(List<Channel> channels) {
        Map<String, LegacyBuilder> grouped = new LinkedHashMap<>();
        if (channels == null) return Collections.emptyList();
        for (Channel channel : channels) {
            if (channel == null || !isLiveChannel(channel)) continue;
            String title = displayTitle(channel.name());
            String key = normalizeTitle(title);
            if (key.isEmpty()) key = "event-" + channel.identityKey();
            LegacyBuilder builder = grouped.get(key);
            if (builder == null) {
                builder = new LegacyBuilder(key, title, channel.group());
                grouped.put(key, builder);
            }
            for (Channel source : expandSources(channel, title)) {
                if (builder.seen.add(source.identityKey())) {
                    builder.sources.add(source);
                    builder.labels.add(legacySourceLabel(source, builder.sources.size()));
                }
            }
        }
        List<LiveEvent> result = new ArrayList<>();
        for (LegacyBuilder b : grouped.values()) {
            if (!b.sources.isEmpty()) result.add(new LiveEvent(b.id, b.title, b.group,
                    b.sources, b.labels, 0, 0L, "Thể thao"));
        }
        return result;
    }

    /**
     * Builds Live cards specifically from the dedicated sports feed. A fixture is
     * displayed only when at least one source marks it green and its scheduled start
     * falls within the last 180 minutes (with a small pre-kickoff tolerance).
     * Every source for that same fixture is then retained as a server option.
     */
    public static List<LiveEvent> fromDedicatedFeed(List<Channel> channels, long nowMillis) {
        if (channels == null || channels.isEmpty()) return Collections.emptyList();
        List<FeedEntry> entries = new ArrayList<>();
        for (Channel channel : channels) {
            if (channel == null || !M3uParser.isNetworkUrl(channel.url())) continue;
            FeedEntry entry = parseFeedEntry(channel, nowMillis);
            if (entry != null) entries.add(entry);
        }

        List<FixtureBuilder> fixtures = new ArrayList<>();
        for (FeedEntry entry : entries) {
            FixtureBuilder found = null;
            for (FixtureBuilder candidate : fixtures) {
                if (sameFixture(candidate, entry)) { found = candidate; break; }
            }
            if (found == null) {
                found = new FixtureBuilder(entry);
                fixtures.add(found);
            } else found.add(entry);
        }

        List<LiveEvent> live = new ArrayList<>();
        for (FixtureBuilder fixture : fixtures) {
            long activeKickoff = Long.MAX_VALUE;
            for (FeedEntry entry : fixture.entries) {
                if (entry.markedLive && isFresh(entry.kickoffMillis, nowMillis)) {
                    activeKickoff = Math.min(activeKickoff, entry.kickoffMillis);
                }
            }
            if (activeKickoff == Long.MAX_VALUE) continue;

            List<Channel> sources = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            FeedEntry representative = null;
            for (FeedEntry entry : fixture.entries) {
                if (!seenUrls.add(entry.channel.url())) continue;
                if (representative == null || (!representative.hasLogo() && entry.hasLogo())
                        || (entry.markedLive && !representative.markedLive)) representative = entry;
                String name = fixture.title;
                Channel source = copyWithUrl(entry.channel, entry.channel.url(), name);
                sources.add(source);
                labels.add(feedSourceLabel(entry.channel, sources.size()));
            }
            if (sources.isEmpty()) continue;
            if (representative != null) {
                // Put a verified live source first, followed by alternatives.
                int liveIndex = -1;
                for (int i = 0; i < fixture.entries.size(); i++) {
                    FeedEntry entry = fixture.entries.get(i);
                    if (entry.markedLive && isFresh(entry.kickoffMillis, nowMillis)) {
                        liveIndex = indexOfSourceUrl(sources, entry.channel.url());
                        if (liveIndex >= 0) break;
                    }
                }
                if (liveIndex > 0) {
                    Collections.swap(sources, 0, liveIndex);
                    Collections.swap(labels, 0, liveIndex);
                    representative = entryByUrl(fixture.entries, sources.get(0).url());
                }
            }
            String dateKey = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(activeKickoff), FEED_ZONE).toLocalDate().toString();
            String stableKey = fixture.homeKey + "_vs_" + fixture.awayKey;
            String id = dateKey + "|" + stableKey;
            String sport = fixture.sport;
            String displayGroup = sport;
            live.add(new LiveEvent(id, fixture.title, displayGroup, sources, labels, 0, activeKickoff, sport));
        }
        live.sort(Comparator.comparingLong(LiveEvent::kickoffMillis).thenComparing(LiveEvent::title));
        return Collections.unmodifiableList(live);
    }

    private static FeedEntry parseFeedEntry(Channel channel, long nowMillis) {
        String raw = channel.name().trim();
        Matcher schedule = TIME_DATE.matcher(raw);
        if (!schedule.find()) return null; // cannot reliably apply the stale-event cutoff
        long kickoff = parseKickoff(schedule, nowMillis);
        if (kickoff <= 0L) return null;
        String afterSchedule = raw.substring(schedule.end()).trim();
        afterSchedule = removeLeadingDecorations(afterSchedule);
        String clean = stripSourceSuffix(afterSchedule);
        Matcher match = MATCH_SPLIT.matcher(clean);
        if (!match.find()) return null;
        String home = clean.substring(0, match.start()).trim();
        String away = clean.substring(match.end()).trim();
        if (home.isEmpty() || away.isEmpty()) return null;
        String homeKey = normalizeTeam(home);
        String awayKey = normalizeTeam(away);
        if (homeKey.isEmpty() || awayKey.isEmpty()) return null;
        boolean markedLive = raw.contains("🟢");
        String sport = raw.contains("🏀") || normalizeText(raw).contains("basketball")
                ? "Bóng rổ" : (raw.contains("🏒") ? "Khúc côn cầu" : "Bóng đá");
        String title = clean.replaceAll("\\s+", " ").trim();
        return new FeedEntry(channel, title, home, away, homeKey, awayKey, kickoff, markedLive, sport);
    }

    private static long parseKickoff(Matcher schedule, long nowMillis) {
        try {
            int hour = Integer.parseInt(schedule.group(1));
            int minute = Integer.parseInt(schedule.group(2));
            int day = Integer.parseInt(schedule.group(3));
            int month = Integer.parseInt(schedule.group(4));
            LocalDate nowDate = java.time.Instant.ofEpochMilli(nowMillis).atZone(FEED_ZONE).toLocalDate();
            int year;
            if (schedule.group(5) != null) {
                year = Integer.parseInt(schedule.group(5));
                if (year < 100) year += year < 70 ? 2000 : 1900;
            } else {
                // Choose the nearest year to handle year-end fixture lists without
                // incorrectly aging a December fixture by twelve months in January.
                year = nowDate.getYear();
            }
            LocalDateTime local = LocalDateTime.of(year, month, day, hour, minute);
            if (schedule.group(5) == null) {
                LocalDateTime best = local;
                long bestDelta = Math.abs(java.time.Duration.between(nowDate.atStartOfDay(), local).toMillis());
                for (int candidateYear : new int[]{year - 1, year + 1}) {
                    try {
                        LocalDateTime candidate = LocalDateTime.of(candidateYear, month, day, hour, minute);
                        long delta = Math.abs(java.time.Duration.between(nowDate.atStartOfDay(), candidate).toMillis());
                        if (delta < bestDelta) { best = candidate; bestDelta = delta; }
                    } catch (DateTimeParseException ignored) { }
                }
                local = best;
            }
            return local.atZone(FEED_ZONE).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static boolean isFresh(long kickoff, long now) {
        long age = now - kickoff;
        return age >= -PRE_KICKOFF_TOLERANCE_MS && age <= LIVE_MAX_AGE_MS;
    }

    private static boolean sameFixture(FixtureBuilder fixture, FeedEntry entry) {
        if (Math.abs(fixture.kickoffMillis - entry.kickoffMillis) > SOURCE_TIME_SKEW_MS) return false;
        double directHome = teamSimilarity(fixture.homeKey, entry.homeKey);
        double directAway = teamSimilarity(fixture.awayKey, entry.awayKey);
        double reverseHome = teamSimilarity(fixture.homeKey, entry.awayKey);
        double reverseAway = teamSimilarity(fixture.awayKey, entry.homeKey);
        return (directHome >= 0.68 && directAway >= 0.68)
                || (reverseHome >= 0.68 && reverseAway >= 0.68);
    }

    private static double teamSimilarity(String one, String two) {
        if (one.equals(two)) return 1.0;
        Set<String> a = new LinkedHashSet<>(Arrays.asList(one.split(" ")));
        Set<String> b = new LinkedHashSet<>(Arrays.asList(two.split(" ")));
        a.remove(""); b.remove("");
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> common = new LinkedHashSet<>(a); common.retainAll(b);
        double overlap = (double) common.size() / (a.size() + b.size() - common.size());
        // Also forgive harmless source-specific extra words such as "sporting club".
        double containment = (double) common.size() / Math.min(a.size(), b.size());
        return Math.max(overlap, containment * 0.82);
    }

    private static String normalizeTeam(String value) {
        String normalized = normalizeText(value);
        normalized = NON_WORD.matcher(normalized).replaceAll(" ").trim().replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("(?i)\\b(?:fc|cf|sc|afc|clb|club|ca|rcd|ud|cd|ac|us|ss|jk|f\\.?c\\.?|s\\.?c\\.?)\\b", " ");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceFirst("^thanh pho\\s+", "");
        if (normalized.equals("st louis city")) normalized = "st louis";
        if (normalized.equals("sport internacional")) normalized = "internacional";
        if (normalized.equals("santos sp")) normalized = "santos";
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("stade rennais", "rennes");
        aliases.put("los angeles galaxy", "la galaxy");
        aliases.put("st louis city", "st louis city");
        aliases.put("atletico mineiro", "atletico mineiro");
        aliases.put("rennais", "rennes");
        aliases.put("sc freiburg", "freiburg");
        aliases.put("freiburg im breisgau", "freiburg");
        aliases.put("real betis balompie", "real betis");
        aliases.put("betis balompie", "real betis");
        aliases.put("u s sassuolo calcio", "sassuolo");
        aliases.put("sassuolo calcio", "sassuolo");
        aliases.put("kocaelispor", "kocaelispor");
        aliases.put("besiktas jk", "besiktas");
        aliases.put("besiktas", "besiktas");
        aliases.put("sport club internacional", "internacional");
        aliases.put("gremio foot ball porto alegrense", "gremio");
        aliases.put("atletico mineiro mg", "atletico mineiro");
        aliases.put("santos fc sp", "santos");
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (normalized.equals(alias.getKey())) return alias.getValue();
        }
        // Country/state suffixes sometimes leak from broadcast catalog names.
        normalized = normalized.replaceAll("_(?:mg|sp|rj)\\b", " ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    private static String removeLeadingDecorations(String value) {
        String result = value == null ? "" : value.trim();
        while (!result.isEmpty()) {
            int cp = result.codePointAt(0);
            if (Character.isLetterOrDigit(cp)) break;
            result = result.substring(Character.charCount(cp)).trim();
        }
        return result;
    }

    private static String stripSourceSuffix(String value) {
        String result = value == null ? "" : value.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            String next = result.replaceFirst("(?i)\\s*\\[[^]]*\\]\\s*$", "").trim();
            if (!next.equals(result)) { result = next; changed = true; continue; }
            next = result.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
            if (!next.equals(result)) { result = next; changed = true; }
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    private static String feedSourceLabel(Channel channel, int index) {
        List<String> extras = new ArrayList<>();
        String raw = channel.name();
        Matcher parenthetical = Pattern.compile("\\(([^)]{1,50})\\)").matcher(raw);
        while (parenthetical.find()) {
            String value = parenthetical.group(1).trim();
            String lower = normalizeText(value);
            if (!value.isEmpty() && !lower.matches("18\\+|live|truc tiep|hd|fhd|uhd|4k")
                    && !extras.contains(value)) extras.add(value);
        }
        Matcher transport = Pattern.compile("(?i)\\[(flv|hls(?:\\s*\\d+)?|m3u8|rtmp|dash|mpd)\\]").matcher(raw);
        while (transport.find()) {
            String value = transport.group(1).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!extras.contains(value)) extras.add(value);
        }
        String provider = channel.group().trim();
        if (provider.equalsIgnoreCase("Chưa phân nhóm")) provider = "";
        StringBuilder label = new StringBuilder();
        if (!provider.isEmpty()) label.append(provider);
        for (String extra : extras) {
            if (label.length() > 0) label.append(" • ");
            label.append(extra);
        }
        return label.length() == 0 ? "Server " + index : label.toString();
    }

    private static String legacySourceLabel(Channel source, int index) {
        String value = source.group();
        if (value.isEmpty() || value.equalsIgnoreCase("Chưa phân nhóm")) return "Server " + index;
        return value + " • Server " + index;
    }

    private static int indexOfSourceUrl(List<Channel> sources, String url) {
        for (int i = 0; i < sources.size(); i++) if (sources.get(i).url().equals(url)) return i;
        return -1;
    }

    private static FeedEntry entryByUrl(List<FeedEntry> entries, String url) {
        for (FeedEntry entry : entries) if (entry.channel.url().equals(url)) return entry;
        return null;
    }

    private static String normalizeText(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).replace('đ', 'd')
                .trim().replaceAll("\\s{2,}", " ");
    }

    /** Old M3U recognition retained for compatibility; the actual Live screen does not use it. */
    public static boolean isLiveChannel(Channel channel) {
        if (channel == null) return false;
        String group = normalizeText(channel.group());
        String name = normalizeText(channel.name());
        if (group.contains("live") || group.contains("event") || group.contains("su kien") || group.contains("sukien")) return true;
        if (name.contains("live") || name.contains("truc tiep")) return true;
        boolean sportsGroup = group.contains("the thao") || group.contains("bong da")
                || group.contains("football") || group.contains("soccer") || group.contains("sports") || group.contains("sport");
        return sportsGroup && MATCH_SPLIT.matcher(" " + name + " ").find();
    }

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
            if (candidate == null || candidate.isEmpty() || !M3uParser.isNetworkUrl(candidate) || !already.add(candidate)) continue;
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

    private static final class FeedEntry {
        final Channel channel;
        final String title, home, away, homeKey, awayKey, sport;
        final long kickoffMillis;
        final boolean markedLive;
        FeedEntry(Channel channel, String title, String home, String away,
                  String homeKey, String awayKey, long kickoffMillis, boolean markedLive, String sport) {
            this.channel = channel; this.title = title; this.home = home; this.away = away;
            this.homeKey = homeKey; this.awayKey = awayKey; this.kickoffMillis = kickoffMillis;
            this.markedLive = markedLive; this.sport = sport;
        }
        boolean hasLogo() { return !channel.effectiveLogoUrl().isEmpty(); }
    }

    private static final class FixtureBuilder {
        final List<FeedEntry> entries = new ArrayList<>();
        final String homeKey, awayKey, title, sport;
        long kickoffMillis;
        FixtureBuilder(FeedEntry first) {
            entries.add(first); homeKey = first.homeKey; awayKey = first.awayKey;
            title = first.title; sport = first.sport; kickoffMillis = first.kickoffMillis;
        }
        void add(FeedEntry entry) {
            entries.add(entry);
            if (entry.markedLive) kickoffMillis = Math.min(kickoffMillis, entry.kickoffMillis);
        }
    }

    private static final class LegacyBuilder {
        final String id, title, group;
        final List<Channel> sources = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        LegacyBuilder(String id, String title, String group) { this.id = id; this.title = title; this.group = group; }
    }
}
