package com.projectillusion.home;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class FreshRowsTest {
    private static final long NOW = 1794050000000L, DAY = 24L * 3600_000;

    private static WatchNextRows.Row row(String pkg, String title, int type, long time) {
        WatchNextRows.Row r = new WatchNextRows.Row();
        r.pkg = pkg; r.title = title; r.type = type; r.time = time; r.intent = "intent:" + title; r.duration = 2_880_000;
        return r;
    }

    @Test public void newEpisodesFirstThenPicksPerApp() {
        List<WatchNextRows.Row> wn = Arrays.asList(
                row("in.startv.hotstar", "Tulsa King", 2, NOW - DAY),
                row("in.startv.hotstar", "Georgie & Mandy", 2, NOW - 2 * DAY),
                row("com.netflix.ninja", "Rick and Morty", 0, NOW));          // a continue row: not new
        List<WatchNextRows.Row> pv = Arrays.asList(
                row("com.amazon.amazonvideo.livingroom", "Steal", 1, 0),
                row("in.startv.hotstar", "Captain", 0, 0));
        List<FreshRows.Channel> out = FreshRows.build(wn, pv, Collections.emptySet(), NOW);
        assertEquals(2, out.size());
        assertEquals("in.startv.hotstar", out.get(0).pkg);
        assertEquals(2, out.get(0).fresh());
        assertEquals("Tulsa King", out.get(0).items.get(0).title);
        assertEquals("pick", out.get(0).items.get(2).kind);
        assertEquals("com.amazon.amazonvideo.livingroom", out.get(1).pkg);
    }

    @Test public void dropsOldNewRowsMusicDuplicatesAndWhatYouAreWatching() {
        List<WatchNextRows.Row> wn = Collections.singletonList(row("a", "Old", 2, NOW - 30 * DAY));
        WatchNextRows.Row song = row("b", "A song", 7, 0);
        WatchNextRows.Row noLink = row("b", "No link", 1, 0); noLink.intent = "";
        List<WatchNextRows.Row> pv = Arrays.asList(song, noLink, row("b", "Reacher", 1, 0), row("b", "reacher", 1, 0),
                row("b", "Jathi Ratnalu", 0, 0));
        List<FreshRows.Channel> out = FreshRows.build(wn, pv, new HashSet<>(Collections.singletonList("b|jathi ratnalu")), NOW);
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).items.size());
        assertEquals("Reacher", out.get(0).items.get(0).title);
    }

    @Test public void capsAppsAndItems() {
        List<WatchNextRows.Row> pv = new ArrayList<>();
        for (int a = 0; a < 8; a++) for (int i = 0; i < 9; i++) pv.add(row("app" + a, "Show " + a + "-" + i, 1, 0));
        List<FreshRows.Channel> out = FreshRows.build(Collections.emptyList(), pv, Collections.emptySet(), NOW);
        assertEquals(FreshRows.MAX_APPS, out.size());
        for (FreshRows.Channel c : out) assertEquals(FreshRows.PER_APP, c.items.size());
    }
}
