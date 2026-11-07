package com.projectillusion.home;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a YouTube video id from its title. This TV's YouTube app reports the title and artist of
 * what's playing but no id, so the Continue watching card couldn't resume it or show its thumbnail.
 * One search per title, on a background thread; any failure just means no id (the card then opens
 * the app as before).
 */
final class YouTubeLookup {
    static final String PKG = "com.google.android.youtube.tv";
    private static final Pattern FIRST_VIDEO = Pattern.compile("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"");
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private YouTubeLookup() {
    }

    /** The first video result in a YouTube search page, or "" for none. */
    static String firstVideoId(String html) {
        if (html == null) return "";
        Matcher m = FIRST_VIDEO.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    /** Searches YouTube for the query; "" on any failure. Never call on the main thread. */
    static String search(String query) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(query, "UTF-8")).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36");
            c.setRequestProperty("Accept-Language", "en");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0 && buf.size() < MAX_BYTES) {
                    buf.write(b, 0, n);
                    // The first result sits early in the page: stop as soon as it's in.
                    String id = firstVideoId(buf.toString("UTF-8"));
                    if (!id.isEmpty()) return id;
                }
                return firstVideoId(buf.toString("UTF-8"));
            }
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
