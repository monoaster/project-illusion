package com.projectillusion.home;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which of the system's Watch Next rows (what Google TV's own Continue watching row shows) to use,
 * and how they join the launcher's own history. No Android types, so the rules run on the JVM.
 *
 * Measured on the BRAVIA 4K VH21: Netflix, Prime Video, JioHotstar, Crunchyroll, aha and OTTplay
 * publish rows with title, season, episode, position, duration and a resume link. Rows linger for
 * years (aha's oldest were from 2023), apps add "new episode" promotions, and Netflix's resume
 * links expire after about a day, so rows are read fresh each time and never stored.
 */
final class WatchNextRows {
    /** TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_*: 0 continue, 1 next episode, 2 new, 3 watchlist. */
    static final int CONTINUE = 0, NEXT = 1;
    /** Rows not touched for this long are left out. */
    static final long MAX_AGE_MS = 120L * 24 * 60 * 60 * 1000;
    /** The wall shows at most this many: 5 rows of 5, no scrolling. */
    static final int WALL_MAX = 25;

    /** One provider row, as read. Missing numbers are -1. */
    static final class Row {
        String pkg = "", title = "", season = "", episode = "", intent = "", poster = "";
        /** Preview rows only: the app's one-line description. For preview rows, type is the program type. */
        String description = "";
        long position = -1, duration = -1, time = -1;
        int type = -1;
    }

    private WatchNextRows() { }

    /** Usable rows as entries, newest first: continue and next-episode rows that are named, recent and unfinished. */
    static List<ContinueList.Entry> pick(List<Row> rows, long now) {
        Map<String, ContinueList.Entry> newest = new HashMap<>();
        for (Row r : rows) {
            if (r == null) continue;
            if (r.type != CONTINUE && r.type != NEXT) continue;
            String pkg = ContinueList.text(r.pkg), title = ContinueList.text(r.title);
            if (pkg.isEmpty() || title.isEmpty() || r.time <= 0 || now - r.time > MAX_AGE_MS) continue;
            ContinueList.Entry e = new ContinueList.Entry();
            e.pkg = pkg; e.title = title;
            e.season = ContinueList.text(r.season); e.episode = ContinueList.text(r.episode);
            e.position = Math.max(0, r.position); e.duration = Math.max(0, r.duration);
            e.time = r.time;
            e.intent = ContinueList.text(r.intent); e.poster = ContinueList.text(r.poster);
            if (e.finished()) continue;
            String k = same(e);
            ContinueList.Entry old = newest.get(k);
            if (old == null || old.time < e.time) newest.put(k, e);
        }
        List<ContinueList.Entry> out = new ArrayList<>(newest.values());
        out.sort(Comparator.comparingLong((ContinueList.Entry e) -> e.time).reversed());
        return out;
    }

    /**
     * Watch Next rows plus the launcher's own history (YouTube and anything opened from Home), newest
     * first. A Watch Next row wins over a history entry with the same title, and an app that has
     * Watch Next rows no longer needs its untitled app card.
     */
    static List<ContinueList.Entry> merge(List<ContinueList.Entry> watchNext, List<ContinueList.Entry> local,
                                          Predicate<ContinueList.Entry> dismissed, int max) {
        List<ContinueList.Entry> out = new ArrayList<>();
        Set<String> apps = new HashSet<>(), titles = new HashSet<>();
        for (ContinueList.Entry e : watchNext) {
            apps.add(e.pkg);
            if (dismissed.test(e) || !titles.add(same(e))) continue;
            out.add(e.copy());
        }
        for (ContinueList.Entry e : local) {
            if (e.appCard && apps.contains(e.pkg)) continue;
            if (!titles.add(same(e))) continue;
            out.add(e.copy());
        }
        out.sort(Comparator.comparingLong((ContinueList.Entry e) -> e.time).reversed());
        while (out.size() > max) out.remove(out.size() - 1);
        return out;
    }

    private static String same(ContinueList.Entry e) {
        return e.pkg + "|" + e.title.toLowerCase(Locale.ROOT).trim();
    }
}
