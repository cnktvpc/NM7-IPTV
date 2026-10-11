package vn.phuong.iptvplayer;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class LiveEventTest {
    private Channel channel(String name, String group, String url) {
        return new Channel(name, group, url, "", "", Collections.emptyMap());
    }

    @Test public void explicitLiveAndEventGroupsAreCollectedButOrdinaryChannelsAreNot() {
        Channel live = channel("Real Sociedad vs Dep. La Coruna", "LIVE", "https://one.example/live.m3u8");
        Channel event = channel("Rio Ave vs CD Nacional", "Sự kiện FPT PLAY", "https://two.example/live.m3u8");
        Channel television = channel("VTV1 HD", "VTV", "https://tv.example/vtv1.m3u8");
        Channel sportsChannel = channel("ON Sports HD", "Thể Thao", "https://tv.example/sport.m3u8");

        List<LiveEvent> events = LiveEvent.fromChannels(Arrays.asList(live, event, television, sportsChannel));

        assertEquals(2, events.size());
        assertEquals("Real Sociedad vs Dep. La Coruna", events.get(0).title());
        assertEquals("Rio Ave vs CD Nacional", events.get(1).title());
    }

    @Test public void duplicateMatchEntriesBecomeOneEventWithTwoServers() {
        Channel main = channel("Real Sociedad vs Dep. La Coruna - Server 1", "LIVE", "https://one.example/live.m3u8");
        Channel backup = channel("Real Sociedad vs Dep. La Coruna - Server 2", "LIVE", "https://two.example/live.m3u8");

        List<LiveEvent> events = LiveEvent.fromChannels(Arrays.asList(main, backup));

        assertEquals(1, events.size());
        assertEquals("Real Sociedad vs Dep. La Coruna", events.get(0).title());
        assertEquals(2, events.get(0).sources().size());
        assertEquals("https://one.example/live.m3u8", events.get(0).primary().url());
    }

    @Test public void nm7AlternateUrlMetadataAddsServerWithoutLeakingDirectiveIntoPlaybackOptions() {
        Channel channel = channel("Rio Ave vs CD Nacional", "LIVE", "https://one.example/live.m3u8");
        channel.options().add("#NM7-ALT-URL:https://two.example/live.m3u8");
        channel.options().add("#KODIPROP:inputstream.adaptive.manifest_type=hls");

        List<LiveEvent> events = LiveEvent.fromChannels(Collections.singletonList(channel));

        assertEquals(1, events.size());
        assertEquals(2, events.get(0).sources().size());
        Channel backup = events.get(0).sources().get(1);
        assertEquals("https://two.example/live.m3u8", backup.url());
        assertFalse(backup.options().stream().anyMatch(o -> o.toLowerCase().startsWith("#nm7-alt-url")));
        assertTrue(backup.options().contains("#KODIPROP:inputstream.adaptive.manifest_type=hls"));
    }

    @Test public void sportsGroupRequiresMatchupLikeNameUnlessExplicitlyMarkedLive() {
        assertFalse(LiveEvent.isLiveChannel(channel("ON Sports HD", "Thể Thao", "https://one.example/a.m3u8")));
        assertTrue(LiveEvent.isLiveChannel(channel("Club A vs Club B", "Thể Thao", "https://one.example/a.m3u8")));
        assertTrue(LiveEvent.isLiveChannel(channel("ON Sports HD", "LIVE", "https://one.example/a.m3u8")));
    }

    @Test public void selectedServerUrlCanBeRestored() {
        Channel one = channel("Club A vs Club B", "LIVE", "https://one.example/a.m3u8");
        Channel two = channel("Club A vs Club B", "LIVE", "https://two.example/a.m3u8");
        LiveEvent event = LiveEvent.fromChannels(Arrays.asList(one, two)).get(0).withPreferredUrl(two.url());

        assertEquals(1, event.selectedIndex());
        assertEquals(two.url(), event.primary().url());
        LiveEvent fresh = LiveEvent.fromChannels(Arrays.asList(one, two)).get(0);
        assertEquals(0, fresh.withPreferredUrl("https://unknown.example/a.m3u8").selectedIndex());
    }
}
