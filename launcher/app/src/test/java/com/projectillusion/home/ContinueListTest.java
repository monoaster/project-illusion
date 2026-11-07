package com.projectillusion.home;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContinueListTest {
    private ContinueList.Entry entry(String title, long time) {
        ContinueList.Entry e = new ContinueList.Entry(); e.pkg = "tv.app"; e.title = title; e.time = time; return e;
    }
    @Test public void capsOrdersAndDedupesByLinkOrTitle() {
        ContinueList list = new ContinueList();
        for (int i = 0; i < 30; i++) list.put(entry("Title " + i, i));
        assertEquals(15, list.entries.size()); assertEquals("Title 29", list.entries.get(0).title);
        ContinueList.Entry e = entry("Title 29", 100); e.position = 10000; list.put(e);
        assertEquals(15, list.entries.size()); assertEquals(10000, list.entries.get(0).position);
        e.link = "https://video/1"; list.put(e); e.title = "Renamed"; e.time = 101; list.put(e);
        assertEquals(15, list.entries.size()); assertEquals("Renamed", list.entries.get(0).title);
        list.remove(e.key()); assertEquals(14, list.entries.size());
    }
    @Test public void duplicateJsonKeepsTheMostRecentWatch() {
        ContinueList list = ContinueList.read("[{\"pkg\":\"a\",\"title\":\"b\",\"time\":20},"
                + "{\"pkg\":\"a\",\"title\":\"b\",\"time\":10}]");
        assertEquals(1, list.entries.size()); assertEquals(20, list.entries.get(0).time);
    }
    @Test public void completionRequiresDurationAndNinetyFivePercent() {
        ContinueList list = new ContinueList(); ContinueList.Entry e = entry("Friends", 1);
        e.position = 999999; list.put(e); assertEquals(1, list.entries.size());
        e.duration = 1000000; e.position = 949999; list.put(e); assertEquals(1, list.entries.size());
        e.position = 950000; list.put(e); assertTrue(list.entries.isEmpty());
        e.position = 1000000; assertTrue(e.finished());
    }
    @Test public void remainingTimeOnlyWhenKnown() {
        ContinueList.Entry e = entry("Friends", 1);
        assertEquals("Netflix", e.detail("Netflix"));
        e.duration = 180000; e.position = 20000; assertEquals("3 min left · Netflix", e.detail("Netflix"));
        e.season = "3"; e.episode = "12"; assertEquals("S3 E12 · 3 min left · Netflix", e.detail("Netflix"));
        e.position = 179999; assertEquals("S3 E12 · 1 min left · Netflix", e.detail("Netflix"));
        e.duration = Long.MAX_VALUE; e.position = 0;
        assertFalse(e.detail("Netflix").contains("-"));
    }
    @Test public void metadataTeardownDoesNotEraseKnownDurationOrEpisode() {
        ContinueList.Entry e = entry("Video", 1);
        e.playback(20000, 180000, "3", "12");
        e.playback(30000, -1, "", null);
        assertEquals(180000, e.duration); assertEquals(30000, e.position);
        assertEquals("S3 E12 · 3 min left · App", e.detail("App"));
    }
    @Test public void roundTripEscapesUnicodeAndAllFields() {
        ContinueList list = new ContinueList(); ContinueList.Entry e = entry("Blue \"Eyes\"\nతెలుగు", 123);
        e.link = "https://watch?v=abc&t=3"; e.art = "/file/art.jpg"; e.video = "NbyHNASFi6U";
        e.position = 20000; e.duration = 210000; e.season = "2"; e.episode = "1"; list.put(e);
        ContinueList copy = ContinueList.read(list.json()); assertEquals(list.json(), copy.json());
        e.title = "Changed"; assertNotEquals(e.title, list.entries.get(0).title);
    }
    @Test public void badFilesAndRowsAreHarmlessAndBounded() {
        assertTrue(ContinueList.read("broken").entries.isEmpty());
        assertTrue(ContinueList.read(null).entries.isEmpty());
        assertTrue(ContinueList.read("[{}").entries.isEmpty());
        assertTrue(ContinueList.read("[{},null,5]").entries.isEmpty());
        assertTrue(ContinueList.read(" ".repeat(128 * 1024 + 1)).entries.isEmpty());
        assertEquals(1024, ContinueList.text("x".repeat(2048)).length());
        ContinueList list = ContinueList.read("[{\"pkg\":\"a\",\"title\":\"b\",\"position\":-3}]");
        assertEquals(0, list.entries.get(0).position);
    }

    @Test
    public void appCardsSayWhatTheyKnow() {
        ContinueList.Entry n = ContinueList.appCard("com.netflix.ninja", "Netflix");
        n.position = 19 * 60000 + 5000;
        org.junit.Assert.assertEquals("Netflix", n.title);
        org.junit.Assert.assertEquals("com.netflix.ninja|Netflix", n.key());
        org.junit.Assert.assertEquals("19 min in · Pick up where you left off", n.detail("Netflix"));
        ContinueList.Entry p = ContinueList.appCard("com.amazon.amazonvideo.livingroom", "Prime Video");
        org.junit.Assert.assertEquals("Pick up where you left off", p.detail("Prime Video"));   // Prime reports no position
        ContinueList list = new ContinueList();
        p.time = 5;
        list.put(p);
        ContinueList back = ContinueList.read(list.json());
        org.junit.Assert.assertEquals(1, back.entries.size());
        org.junit.Assert.assertTrue(back.entries.get(0).appCard);
    }

    @Test
    public void youTubeSearchPageGivesTheFirstVideo() {
        String page = "x \"videoRenderer\":{\"videoId\":\"NbyHNASFi6U\",\"thumbnail\" y \"videoRenderer\":{\"videoId\":\"TEj-eBEsaPY\"";
        org.junit.Assert.assertEquals("NbyHNASFi6U", YouTubeLookup.firstVideoId(page));
        org.junit.Assert.assertEquals("", YouTubeLookup.firstVideoId("no results"));
        org.junit.Assert.assertEquals("", YouTubeLookup.firstVideoId(null));
    }
}
