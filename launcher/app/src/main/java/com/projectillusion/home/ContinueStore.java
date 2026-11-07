package com.projectillusion.home;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One process-wide owner; service callbacks and Home cannot overwrite each other's updates. */
final class ContinueStore {
    private static ContinueStore instance;
    static synchronized ContinueStore get(Context ctx) {
        if (instance == null) instance = new ContinueStore(ctx.getApplicationContext());
        return instance;
    }
    private final AtomicFile file;
    private final android.content.SharedPreferences prefs;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final ContinueList list;
    private ContinueList.Entry pending;
    private final List<Runnable> listeners = new ArrayList<>();
    private final java.util.Set<String> dismissed = new java.util.HashSet<>();
    /** Apps seen reporting a title: they get real cards, never an app card. */
    private final java.util.Set<String> namedApps = new java.util.HashSet<>();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private ContinueStore(Context ctx) {
        file = new AtomicFile(new File(ctx.getFilesDir(), "continue.json"));
        prefs = ctx.getSharedPreferences("continue_pending", Context.MODE_PRIVATE);
        ContinueList loaded = new ContinueList();
        try {
            if (file.getBaseFile().length() <= 128 * 1024) loaded = ContinueList.read(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        list = loaded;
        ContinueList p = ContinueList.read(prefs.getString("pending", "[]"));
        if (!p.entries.isEmpty()) pending = p.entries.get(0);
        dismissed.addAll(prefs.getStringSet("dismissed", java.util.Collections.emptySet()));
        namedApps.addAll(prefs.getStringSet("named_apps", java.util.Collections.emptySet()));
    }
    synchronized List<ContinueList.Entry> snapshot() {
        List<ContinueList.Entry> out = new ArrayList<>();
        for (ContinueList.Entry e : list.entries) out.add(e.copy());
        return out;
    }
    /** Removed with hold OK: by key or by app and title, so a Watch Next row stays gone too. */
    synchronized boolean isDismissed(ContinueList.Entry e) {
        return dismissed.contains(e.key()) || dismissed.contains(e.pkg + "|" + e.title);
    }
    synchronized void listen(Runnable r) { listeners.add(r); }
    synchronized void unlisten(Runnable r) { listeners.remove(r); }
    synchronized void resume(ContinueList.Entry entry) {
        pending = entry.copy(); dismissed.remove(entry.key()); dismissed.remove(entry.pkg + "|" + entry.title); savePending();
    }
    synchronized void appLaunch(String pkg) {
        // An app's old title must not be mistaken for the next thing chosen inside that app.
        pending = null;
        dismissed.removeIf(key -> key.startsWith(pkg + "|"));
        savePending();
    }
    synchronized void remove(String key) {
        if (dismissed.size() > 100) dismissed.clear();
        dismissed.add(key);
        for (ContinueList.Entry e : list.entries) if (e.key().equals(key)) dismissed.add(e.pkg + "|" + e.title);
        list.remove(key);
        savePending(); save();
    }
    private void savePending() {
        ContinueList p = new ContinueList();
        if (pending != null) p.put(pending);
        prefs.edit().putString("pending", p.json()).putStringSet("dismissed", new java.util.HashSet<>(dismissed)).apply();
    }
    synchronized void update(String pkg, String title, String video, long position, long duration,
                             int state, String season, String episode) {
        update(pkg, title, video, position, duration, state, season, episode, "");
    }

    /**
     * appName: the app's label, used for an app card when the app plays something without naming
     * it and nothing the launcher opened is pending for it (Netflix and Prime on this TV).
     */
    synchronized void update(String pkg, String title, String video, long position, long duration,
                             int state, String season, String episode, String appName) {
        // Ignore idle sessions and reset-to-zero stop callbacks after a player tears down.
        if (state != 3 && state != 2 && state != 1) return;
        boolean named = !ContinueList.text(title).isEmpty();
        boolean ours = pending != null && pending.pkg.equals(pkg);
        if (named && namedApps.add(pkg)) {
            // This app names what it plays (YouTube does): it never needs an app card. Drop any made
            // from a moment between videos when it reported nothing.
            prefs.edit().putStringSet("named_apps", new java.util.HashSet<>(namedApps)).apply();
            boolean changed = list.entries.removeIf(x -> x.appCard && x.pkg.equals(pkg));
            if (changed) save();
        }
        if (!named && !ours) {
            if (namedApps.contains(pkg)) return;
            // Only while actually playing: an idle session (Netflix keeps one) must not make a card.
            // Prime reports position 0 the whole time, so 0 is allowed here.
            if (state == 3 && !ContinueList.text(appName).isEmpty()) appCard(pkg, appName, position);
            return;
        }
        if (position <= 0) return;
        ContinueList.Entry e = null;
        if (pending != null && pending.pkg.equals(pkg)
                && (video.isEmpty() || pending.video.isEmpty() || pending.video.equals(video))) {
            e = pending.copy();
            for (ContinueList.Entry old : list.entries) if (old.key().equals(pending.key())) { e = old.copy(); break; }
        }
        if (e == null && !ContinueList.text(title).isEmpty()) {
            for (ContinueList.Entry old : list.entries) {
                if (old.pkg.equals(pkg) && ((!video.isEmpty() && old.video.equals(video)) || old.title.equals(title))) {
                    e = old.copy(); break;
                }
            }
            if (e == null) { e = new ContinueList.Entry(); e.pkg = pkg; }
        }
        if (e == null) return;
        if (!ContinueList.text(title).isEmpty()) e.title = ContinueList.text(title);
        if (!video.isEmpty()) {
            e.video = video;
            if (pkg.equals("com.google.android.youtube.tv")) e.link = "https://www.youtube.com/watch?v=" + video;
        }
        e.playback(position, duration, season, episode);
        if (dismissed.contains(e.key()) || dismissed.contains(e.pkg + "|" + e.title)) return;
        // A paused session polled on Home must not leap ahead of the video just watched.
        ContinueList.Entry old = null;
        for (ContinueList.Entry item : list.entries) if (item.key().equals(e.key())) old = item;
        e.time = old != null && state != 3 && old.position == e.position ? old.time : System.currentTimeMillis();
        if (old != null && old.position == e.position && old.duration == e.duration && old.title.equals(e.title) && !e.finished()) return;
        list.put(e);
        if (e.finished()) { dismissed.add(e.key()); dismissed.add(e.pkg + "|" + e.title); savePending(); }
        save();
    }
    private void appCard(String pkg, String appName, long position) {
        ContinueList.Entry e = ContinueList.appCard(pkg, appName);
        if (dismissed.contains(e.key())) return;
        ContinueList.Entry old = null;
        for (ContinueList.Entry item : list.entries) if (item.key().equals(e.key())) old = item;
        // Polls every few seconds while playing: write only when the minute shown changes, or when new.
        if (old != null && old.position / 60000 == position / 60000) return;
        e.position = Math.max(0, position);
        e.time = System.currentTimeMillis();
        list.put(e);
        save();
    }

    // ---- YouTube: find the video when the app only gives a title ---------------------------------
    private final java.util.Set<String> looked = new java.util.HashSet<>();

    /** Once per title: search YouTube for "title artist" and keep the first video's id. */
    synchronized void lookUpYouTube(String title, String artist) {
        String t = ContinueList.text(title);
        if (t.isEmpty() || !looked.add(t)) return;
        String q = (t + " " + ContinueList.text(artist)).trim();
        writer.execute(() -> {
            String id = YouTubeLookup.search(q);
            if (id.isEmpty()) return;
            synchronized (ContinueStore.this) {
                for (ContinueList.Entry e : list.entries) {
                    if (e.pkg.equals(YouTubeLookup.PKG) && e.title.equals(t) && e.video.isEmpty()) {
                        e.video = id;
                        e.link = "https://www.youtube.com/watch?v=" + id;
                        save();
                        return;
                    }
                }
            }
        });
    }

    private void save() {
        String raw = list.json();
        writer.execute(() -> {
            FileOutputStream out = null;
            try {
                out = file.startWrite(); out.write(raw.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out);
            } catch (Exception ex) { file.failWrite(out); Log.w("Illusion", "continue save", ex); }
        });
        for (Runnable r : new ArrayList<>(listeners)) main.post(r);
    }
    static String videoId(String link) {
        if (link == null) return "";
        try {
            Uri u = Uri.parse(link);
            String id = u.getQueryParameter("v");
            if (id == null && "youtu.be".equals(u.getHost())) id = u.getLastPathSegment();
            if (id == null && link.length() == 11) id = link;
            if (id == null || id.length() != 11) return "";
            for (int i = 0; i < id.length(); i++) {
                char c = id.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) return "";
            }
            return id;
        } catch (Exception ignored) { return ""; }
    }
}
