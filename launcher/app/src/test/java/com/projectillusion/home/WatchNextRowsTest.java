package com.projectillusion.home;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Rows shaped like the ones read from the BRAVIA 4K VH21 on 2026-11-07. */
public class WatchNextRowsTest {
    private static final long NOW = 1794050000000L;   // 2026-11-07
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static WatchNextRows.Row row(String pkg, String title, int type, long pos, long dur, long time) {
        WatchNextRows.Row r = new WatchNextRows.Row();
        r.pkg = pkg; r.title = title; r.type = type; r.position = pos; r.duration = dur; r.time = time;
        r.intent = "https://example/" + title; r.poster = "https://img/" + title;
        return r;
    }

    @Test public void keepsContinueAndNextNewestFirst() {
        List<ContinueList.Entry> out = WatchNextRows.pick(Arrays.asList(
                row("com.netflix.ninja", "Rick and Morty", 0, 997000, 1366000, NOW - 3600_000),
                row("com.crunchyroll.crunchyroid", "Solo Leveling", 1, 45000, 1420004, NOW - 2 * DAY),
                row("com.amazon.amazonvideo.livingroom", "Jathi Ratnalu", 0, 6721000, 8607000, NOW - 1000)),
                NOW);
        assertEquals(3, out.size());
        assertEquals("Jathi Ratnalu", out.get(0).title);
        assertEquals("Rick and Morty", out.get(1).title);
        assertEquals("https://example/Rick and Morty", out.get(1).intent);
        assertEquals("https://img/Rick and Morty", out.get(1).poster);
    }

    @Test public void dropsNewPromotionsWatchlistUnnamedOldAndFinished() {
        WatchNextRows.Row noTime = row("a", "No date", 0, 1, 10, -1);
        List<ContinueList.Entry> out = WatchNextRows.pick(Arrays.asList(
                row("in.startv.hotstar", "Tulsa King", 2, 0, 0, NOW - DAY),             // "new" promotion
                row("x", "Watchlist", 3, 0, 0, NOW - DAY),
                row("x", "", 0, 10, 100, NOW - DAY),                                   // no title
                row("ahaflix.tv", "Nenu Student Sir", 0, 262083, 7542708, 1689280751770L), // 2023
                row("ahaflix.tv", "DJ Tillu", 0, 7_200_000, 7_306_458, NOW - DAY),       // 98.5% watched
                noTime, null),
                NOW);
        assertTrue(out.isEmpty());
    }

    @Test public void unknownDurationIsNotFinished() {
        List<ContinueList.Entry> out = WatchNextRows.pick(Collections.singletonList(
                row("p", "Steal", 1, -1, 2754000, NOW - DAY)), NOW);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).position);
    }

    @Test public void sameTitleTwiceKeepsTheNewest() {
        List<ContinueList.Entry> out = WatchNextRows.pick(Arrays.asList(
                row("p", "Reacher", 0, 100, 1000, NOW - 5 * DAY),
                row("p", "reacher ", 0, 600, 1000, NOW - DAY)), NOW);
        assertEquals(1, out.size());
        assertEquals(600, out.get(0).position);
    }

    @Test public void mergeJoinsHistoryDropsCoveredAppCardsAndDuplicates() {
        List<ContinueList.Entry> wn = WatchNextRows.pick(Arrays.asList(
                row("com.netflix.ninja", "Rick and Morty", 0, 997000, 1366000, NOW - 3600_000)), NOW);
        List<ContinueList.Entry> local = new ArrayList<>();
        ContinueList.Entry card = ContinueList.appCard("com.netflix.ninja", "Netflix"); card.time = NOW; local.add(card);
        ContinueList.Entry dup = new ContinueList.Entry(); dup.pkg = "com.netflix.ninja"; dup.title = "Rick and Morty"; dup.time = NOW; local.add(dup);
        ContinueList.Entry yt = new ContinueList.Entry(); yt.pkg = "com.google.android.youtube.tv"; yt.title = "Blue Eyes"; yt.time = NOW - 10; local.add(yt);
        ContinueList.Entry prime = ContinueList.appCard("com.amazon.amazonvideo.livingroom", "Prime Video"); prime.time = NOW - 20; local.add(prime);

        List<ContinueList.Entry> out = WatchNextRows.merge(wn, local, e -> false, 15);
        assertEquals(3, out.size());
        assertEquals("Blue Eyes", out.get(0).title);
        assertEquals("Prime Video", out.get(1).title);          // no Prime rows, so its app card stays
        assertEquals("Rick and Morty", out.get(2).title);
        assertFalse(out.get(2).intent.isEmpty());               // the Watch Next row won over history
    }

    @Test public void mergeSkipsDismissedAndCaps() {
        List<WatchNextRows.Row> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) rows.add(row("p", "Show " + i, 0, 10, 100, NOW - i * 1000L));
        List<ContinueList.Entry> wn = WatchNextRows.pick(rows, NOW);
        List<ContinueList.Entry> out = WatchNextRows.merge(wn, Collections.emptyList(),
                e -> e.title.equals("Show 0"), WatchNextRows.WALL_MAX);
        assertEquals(25, out.size());
        assertEquals("Show 1", out.get(0).title);
    }

    @Test public void copyCarriesLinkAndPoster() {
        ContinueList.Entry e = new ContinueList.Entry(); e.intent = "i"; e.poster = "p";
        ContinueList.Entry c = e.copy();
        assertEquals("i", c.intent); assertEquals("p", c.poster);
    }
}
