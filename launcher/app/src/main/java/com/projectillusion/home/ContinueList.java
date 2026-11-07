package com.projectillusion.home;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small, bounded watch history. No Android types so the rules run on the JVM too. */
final class ContinueList {
    static final int MAX = 15;
    static final class Entry {
        String pkg = "", title = "", link = "", art = "", video = "", season = "", episode = "";
        long position, duration, time;
        /** An app card: the app played something it didn't name (Netflix, Prime). OK opens the app. */
        boolean appCard;
        /** From the system's Watch Next table (WatchNextRows), never saved: the app's own resume link and poster. */
        String intent = "", poster = "";
        String key() { return pkg + "|" + (link.isEmpty() ? title : link); }
        Entry copy() {
            Entry e = new Entry();
            e.pkg = pkg; e.title = title; e.link = link; e.art = art; e.video = video;
            e.season = season; e.episode = episode;
            e.position = position; e.duration = duration; e.time = time; e.appCard = appCard;
            e.intent = intent; e.poster = poster;
            return e;
        }
        void playback(long position, long duration, String season, String episode) {
            this.position = Math.max(0, position);
            if (duration > 0) this.duration = duration;
            if (!text(season).isEmpty()) this.season = text(season);
            if (!text(episode).isEmpty()) this.episode = text(episode);
        }
        boolean finished() { return duration > 0 && position >= duration * .95; }
        String detail(String app) {
            List<String> bits = new ArrayList<>();
            if (appCard) {
                // The app never said what it was; say what we know: where you were, and where to go.
                if (position >= 60000) bits.add(position / 60000 + " min in");
                bits.add("Pick up where you left off");
                return String.join(" · ", bits);
            }
            if (!season.isEmpty() && !episode.isEmpty()) bits.add("S" + season + " E" + episode);
            if (duration > position && duration > 0) {
                long remaining = duration - position;
                bits.add((remaining / 60000 + (remaining % 60000 == 0 ? 0 : 1)) + " min left");
            }
            bits.add(app);
            return String.join(" · ", bits);
        }
    }
    final List<Entry> entries = new ArrayList<>();
    void put(Entry entry) {
        if (entry.pkg.isEmpty() || entry.title.isEmpty()) return;
        for (Entry e : entries) if (e.key().equals(entry.key()) && e.time > entry.time) return;
        entries.removeIf(e -> e.key().equals(entry.key()));
        if (!entry.finished()) entries.add(entry.copy());
        entries.sort(Comparator.comparingLong((Entry e) -> e.time).reversed());
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
    }
    void remove(String key) { entries.removeIf(e -> e.key().equals(key)); }
    /** The app card for a package: one per app, titled with the app's name. */
    static Entry appCard(String pkg, String appName) {
        Entry e = new Entry();
        e.pkg = pkg; e.title = text(appName).isEmpty() ? pkg : text(appName); e.appCard = true;
        return e;
    }

    static String text(String s) { return s == null ? "" : s.substring(0, Math.min(1024, s.length())).trim(); }
    String json() {
        JSONArray out = new JSONArray();
        for (Entry e : entries) {
            try {
                JSONObject j = new JSONObject();
                j.put("pkg", e.pkg); j.put("title", e.title); j.put("link", e.link); j.put("art", e.art);
                j.put("video", e.video); j.put("season", e.season); j.put("episode", e.episode);
                j.put("position", e.position); j.put("duration", e.duration); j.put("time", e.time);
                if (e.appCard) j.put("app", true);
                out.put(j);
            } catch (Exception ignored) { }
        }
        return out.toString();
    }
    static ContinueList read(String raw) {
        ContinueList list = new ContinueList();
        if (raw == null || raw.length() > 128 * 1024) return list;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < Math.min(array.length(), 100); i++) {
                JSONObject j = array.optJSONObject(i);
                if (j == null) continue;
                Entry e = new Entry();
                e.pkg = text(j.optString("pkg", "")); e.title = text(j.optString("title", ""));
                e.link = text(j.optString("link", "")); e.art = text(j.optString("art", ""));
                e.video = text(j.optString("video", "")); e.season = text(j.optString("season", ""));
                e.episode = text(j.optString("episode", ""));
                e.position = Math.max(0, j.optLong("position")); e.duration = Math.max(0, j.optLong("duration"));
                e.time = Math.max(0, j.optLong("time"));
                e.appCard = j.optBoolean("app", false);
                list.put(e);
            }
        } catch (Throwable ignored) { return new ContinueList(); }
        return list;
    }
}
