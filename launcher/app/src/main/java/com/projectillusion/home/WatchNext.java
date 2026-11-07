package com.projectillusion.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the system's Watch Next table, the rows Google TV's own Continue watching shows. Needs the
 * runtime permission READ_TV_LISTINGS (build-and-install grants it); without it there are no rows.
 * A caller without ACCESS_ALL_EPG_DATA sees every row marked searchable, may not pass a selection,
 * and must name its columns (a null projection fails on some Android 12 TVs). See docs/watch-next.md upstream.
 */
final class WatchNext {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final String[] COLUMNS = {
            "package_name", "title", "season_display_number", "episode_display_number",
            "last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis",
            "watch_next_type", "intent_uri", "poster_art_uri"};

    private WatchNext() { }

    static boolean allowed(Context ctx) {
        return ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Every readable row, or none when the permission is missing or the provider refuses. Off the main thread. */
    static List<WatchNextRows.Row> query(Context ctx) {
        if (!allowed(ctx)) return Collections.emptyList();
        List<WatchNextRows.Row> rows = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null)) {
            if (c == null) return rows;
            while (c.moveToNext() && rows.size() < 500) {
                WatchNextRows.Row r = new WatchNextRows.Row();
                r.pkg = str(c, 0); r.title = str(c, 1); r.season = str(c, 2); r.episode = str(c, 3);
                r.position = num(c, 4); r.duration = num(c, 5); r.time = num(c, 6);
                r.type = (int) num(c, 7); r.intent = str(c, 8); r.poster = str(c, 9);
                rows.add(r);
            }
        } catch (RuntimeException e) {
            Log.w("Illusion", "watch next unreadable: " + e);
        }
        return rows;
    }

    private static final String[] PREVIEW_COLUMNS = {
            "package_name", "title", "short_description", "poster_art_uri", "duration_millis", "intent_uri",
            "season_display_number", "episode_display_number", "type"};

    /**
     * The apps' preview channel rows (their picks), at most {@code perApp} per app in the order the
     * table gives them. Apple TV alone publishes about 25,000 rows on the BRAVIA, so the whole table is
     * walked but only the first few of each app are kept. Off the main thread.
     */
    static List<WatchNextRows.Row> queryPreview(Context ctx, int perApp) {
        if (!allowed(ctx)) return Collections.emptyList();
        List<WatchNextRows.Row> rows = new ArrayList<>();
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        try (Cursor c = ctx.getContentResolver().query(TvContract.PreviewPrograms.CONTENT_URI, PREVIEW_COLUMNS, null, null, null)) {
            if (c == null) return rows;
            int seen = 0;
            while (c.moveToNext() && seen++ < 40000) {
                String pkg = str(c, 0);
                int n = count.getOrDefault(pkg, 0);
                if (n >= perApp) continue;
                count.put(pkg, n + 1);
                WatchNextRows.Row r = new WatchNextRows.Row();
                r.pkg = pkg; r.title = str(c, 1); r.description = str(c, 2); r.poster = str(c, 3);
                r.duration = num(c, 4); r.intent = str(c, 5); r.season = str(c, 6); r.episode = str(c, 7);
                r.type = (int) num(c, 8);
                rows.add(r);
            }
        } catch (RuntimeException e) {
            Log.w("Illusion", "preview programs unreadable: " + e);
        }
        return rows;
    }

    private static String str(Cursor c, int i) { return c.isNull(i) ? "" : c.getString(i); }
    private static long num(Cursor c, int i) {
        if (c.isNull(i)) return -1;
        try { return c.getLong(i); } catch (RuntimeException e) { return -1; }
    }

    /** The intent that resumes an entry in its own app, or null when the row's link is unusable. */
    static Intent launch(Context ctx, ContinueList.Entry e) {
        if (e.intent.isEmpty()) return null;
        Intent i;
        try {
            if (e.intent.startsWith("intent:")) {
                i = Intent.parseUri(e.intent, Intent.URI_INTENT_SCHEME);
                // Another app's data: keep it inside that app.
                i.setSelector(null);
                ComponentName cn = i.getComponent();
                if (cn != null && !cn.getPackageName().equals(e.pkg)) i.setComponent(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(e.intent));
                // Netflix's TV app expects a partner source on deep links; 30 is the one TV launchers send.
                if (TitleLinks.NETFLIX.equals(e.pkg)) i.putExtra("source", "30");
            }
        } catch (Exception ex) {
            Log.w("Illusion", "watch next link unusable for " + e.pkg + ": " + ex);
            return null;
        }
        i.setPackage(e.pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return ctx.getPackageManager().resolveActivity(i, 0) != null ? i : null;
    }
}
