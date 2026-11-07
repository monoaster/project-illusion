package com.projectillusion.home;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes a title link open the video, not just the app. Netflix's TV app takes
 * netflix.com/title/<id> links but lands on its home (tested on the BRAVIA: nothing plays,
 * even after OK), while netflix.com/watch/<id> starts the show (or resumes it) at once, with
 * the app cold or already running. So a Netflix title link is turned into its watch link.
 */
final class TitleLinks {
    static final String NETFLIX = "com.netflix.ninja";
    private static final Pattern NETFLIX_TITLE = Pattern.compile(
            "^https?://(?:www\\.)?netflix\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?title/(\\d+)\\b.*$", Pattern.CASE_INSENSITIVE);

    private TitleLinks() {
    }

    /** The link to send to {@code pkg}: a Netflix title link becomes its watch link; anything else is unchanged. */
    static String forApp(String pkg, String link) {
        if (link == null) return null;
        String l = link.trim();
        if (NETFLIX.equals(pkg)) {
            Matcher m = NETFLIX_TITLE.matcher(l);
            if (m.matches()) return "https://www.netflix.com/watch/" + m.group(1);
        }
        return l;
    }
}
