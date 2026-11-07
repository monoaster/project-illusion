package com.projectillusion.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TitleLinksTest {
    @Test
    public void netflixTitleLinksBecomeWatchLinks() {
        assertEquals("https://www.netflix.com/watch/70153404",
                TitleLinks.forApp("com.netflix.ninja", "https://www.netflix.com/title/70153404"));
        assertEquals("https://www.netflix.com/watch/70153404",
                TitleLinks.forApp("com.netflix.ninja", " http://netflix.com/in/title/70153404?source=35 "));
        assertEquals("https://www.netflix.com/watch/80014749",
                TitleLinks.forApp("com.netflix.ninja", "https://www.netflix.com/en-in/title/80014749"));
        // Already a watch link, or another kind of Netflix link: left as it is.
        assertEquals("https://www.netflix.com/watch/80014749",
                TitleLinks.forApp("com.netflix.ninja", "https://www.netflix.com/watch/80014749"));
        assertEquals("https://www.netflix.com/browse", TitleLinks.forApp("com.netflix.ninja", "https://www.netflix.com/browse"));
    }

    @Test
    public void otherAppsAreUntouched() {
        assertEquals("https://www.netflix.com/title/1", TitleLinks.forApp("com.other", "https://www.netflix.com/title/1"));
        assertEquals("crunchyroll://series/GR19V7816",
                TitleLinks.forApp("com.crunchyroll.crunchyroid", "crunchyroll://series/GR19V7816"));
        assertNull(TitleLinks.forApp("com.netflix.ninja", null));
    }
}
