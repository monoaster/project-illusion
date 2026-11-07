package com.projectillusion.home;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * New for you (Project Illusion Home): what the apps say is new or worth watching, one channel per app.
 * New episodes come from Watch Next rows of type "new"; picks come from the apps' preview channels
 * (Prime, ZEE5, Crunchyroll, YouTube and Apple TV publish them on the BRAVIA). Shows already in
 * Continue watching are left out. No Android types, so the rules run on the JVM.
 */
final class FreshRows {
    /** TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW */
    static final int WATCH_NEXT_NEW = 2;
    static final long NEW_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;
    static final int MAX_APPS = 5, PER_APP = 5;
    /** Preview program types that are music (track, album, artist, playlist, station): not for this page. */
    private static final Set<Integer> MUSIC = new HashSet<>(java.util.Arrays.asList(7, 8, 9, 10, 11));

    static final class Item {
        String pkg = "", title = "", season = "", episode = "", intent = "", poster = "", desc = "", kind = "";
        long duration, time;
        String key() { return pkg + "|" + kind + "|" + title + "|" + season + "|" + episode; }
    }

    static final class Channel {
        String pkg = "";
        final List<Item> items = new ArrayList<>();
        int fresh() { int n = 0; for (Item i : items) if (i.kind.equals("new")) n++; return n; }
    }

    private FreshRows() { }

    /**
     * @param watchNext all readable Watch Next rows (only type "new" is used)
     * @param preview   preview channel rows, in the order the apps ranked them
     * @param watching  "package|title" (lower case) of everything in Continue watching
     */
    static List<Channel> build(List<WatchNextRows.Row> watchNext, List<WatchNextRows.Row> preview,
                               Set<String> watching, long now) {
        Map<String, Item> news = new HashMap<>();
        for (WatchNextRows.Row r : watchNext) {
            if (r == null || r.type != WATCH_NEXT_NEW) continue;
            Item i = item(r, "new");
            if (i == null || r.time <= 0 || now - r.time > NEW_MAX_AGE_MS) continue;
            i.time = r.time;
            Item old = news.get(i.key());
            if (old == null || old.time < i.time) news.put(i.key(), i);
        }
        Map<String, Channel> channels = new LinkedHashMap<>();
        List<Item> sorted = new ArrayList<>(news.values());
        sorted.sort(Comparator.comparingLong((Item i) -> i.time).reversed());
        for (Item i : sorted) add(channels, i);
        Set<String> seen = new HashSet<>();
        for (Item i : sorted) seen.add(same(i));
        for (WatchNextRows.Row r : preview) {
            if (r == null || MUSIC.contains(r.type)) continue;
            Item i = item(r, "pick");
            if (i == null || i.intent.isEmpty()) continue;
            if (watching.contains(same(i)) || !seen.add(same(i))) continue;
            add(channels, i);
        }
        List<Channel> out = new ArrayList<>(channels.values());
        out.removeIf(c -> c.items.isEmpty());
        // Apps with new episodes first, most first; the rest keep the order they came in.
        List<Channel> ordered = new ArrayList<>(out);
        ordered.sort((a, b) -> Integer.compare(b.fresh(), a.fresh()));
        while (ordered.size() > MAX_APPS) ordered.remove(ordered.size() - 1);
        return ordered;
    }

    private static void add(Map<String, Channel> channels, Item i) {
        Channel c = channels.get(i.pkg);
        if (c == null) { c = new Channel(); c.pkg = i.pkg; channels.put(i.pkg, c); }
        if (c.items.size() < PER_APP) c.items.add(i);
    }

    private static Item item(WatchNextRows.Row r, String kind) {
        String pkg = ContinueList.text(r.pkg), title = ContinueList.text(r.title);
        if (pkg.isEmpty() || title.isEmpty()) return null;
        Item i = new Item();
        i.pkg = pkg; i.title = title; i.kind = kind;
        i.season = ContinueList.text(r.season); i.episode = ContinueList.text(r.episode);
        i.intent = ContinueList.text(r.intent); i.poster = ContinueList.text(r.poster);
        i.desc = ContinueList.text(r.description);
        i.duration = Math.max(0, r.duration);
        return i;
    }

    static String same(Item i) { return i.pkg + "|" + i.title.toLowerCase(Locale.ROOT).trim(); }
}
