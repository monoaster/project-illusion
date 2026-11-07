package com.projectillusion.home;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The owner's choices, from config.json in the app's external files folder (tools/theme.sh puts it
 * there). Every key is optional:
 *
 *   greeting_name  "Alex"                        the name Home greets; "" for none
 *   show_brand     true                          the project_illusion name at the top left
 *   weather        {"latitude":16.3, "longitude":80.4, "fahrenheit":false}   leave out for no weather
 *   dock_apps      ["com.netflix.ninja", ...]    three to five apps for the dock
 *   dock_colors    ["com.netflix.ninja=#E50914", ...]
 *   hidden_apps    ["com.example.app", ...]      left out of All apps
 */
final class Theme {
    static final String FILE = "config.json";
    static final List<String> SUGGESTED_DOCK = Arrays.asList(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv",
            "in.startv.hotstar", "com.crunchyroll.crunchyroid");

    String greetingName = "";
    boolean showBrand = true;
    boolean weather;
    double weatherLat, weatherLon;
    boolean fahrenheit;
    List<String> dockApps = SUGGESTED_DOCK;
    Map<String, Integer> dockColors = Collections.emptyMap();
    List<String> hiddenApps = Collections.emptyList();

    /** Where theme.sh writes; null when external storage is unavailable. */
    static File dir(Context ctx) { return ctx.getExternalFilesDir(null); }

    static Theme load(Context ctx) {
        Theme t = new Theme();
        File dir = dir(ctx);
        File f = dir == null ? null : new File(dir, FILE);
        if (f == null || !f.isFile() || f.length() > 64 * 1024) return t;
        try {
            JSONObject j = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            t.greetingName = j.optString("greeting_name", "").trim();
            t.showBrand = j.optBoolean("show_brand", true);
            JSONObject w = j.optJSONObject("weather");
            if (w != null && w.has("latitude") && w.has("longitude")) {
                t.weatherLat = w.optDouble("latitude");
                t.weatherLon = w.optDouble("longitude");
                t.fahrenheit = w.optBoolean("fahrenheit", false);
                t.weather = !Double.isNaN(t.weatherLat) && !Double.isNaN(t.weatherLon);
            }
            List<String> dock = strings(j.optJSONArray("dock_apps"));
            if (!dock.isEmpty()) t.dockApps = dock;
            t.hiddenApps = strings(j.optJSONArray("hidden_apps"));
            Map<String, Integer> colors = new HashMap<>();
            for (String entry : strings(j.optJSONArray("dock_colors"))) {
                int eq = entry.indexOf('=');
                if (eq <= 0) continue;
                try { colors.put(entry.substring(0, eq).trim(), Color.parseColor(entry.substring(eq + 1).trim())); }
                catch (IllegalArgumentException ignored) { }
            }
            t.dockColors = colors;
        } catch (Exception e) {
            Log.w("Illusion", "config.json unreadable, using defaults: " + e);
        }
        return t;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
