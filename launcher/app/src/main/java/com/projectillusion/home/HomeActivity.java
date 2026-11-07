package com.projectillusion.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home. The screen itself is the page in assets/home/index.html, run in a WebView; this activity is its
 * window onto the TV. The page asks for the dock, Continue watching (the system's Watch Next rows plus
 * this launcher's own history), New for you, the installed apps and the network, and calls back to
 * launch, resume and open settings.
 */
public class HomeActivity extends Activity {
    static final String ACTION_OPEN = "com.projectillusion.home.OPEN";
    static final String EXTRA_SCREEN = "screen";
    private static final String PAGE = "file:///android_asset/home/index.html";
    /** Colours for apps the theme doesn't colour. */
    private static final int[] PALETTE = {0xFF5ED6FF, 0xFFFFC857, 0xFF7EF0B0, 0xFF9B8CFF, 0xFFFF5C7A, 0xFFFF6B3D, 0xFF1DB4C4};

    /** True while Home is in front: the dial service leaves hold OK to the page then. */
    static volatile boolean showing;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable storeChanged = this::load;
    private WebView web;
    private Theme theme;
    private ContinueStore store;
    private volatile List<ContinueList.Entry> feed = Collections.emptyList();
    private volatile List<FreshRows.Channel> fresh = Collections.emptyList();
    /** The apps' preview rows behind New for you: walking them is slow, so at most every 10 minutes. */
    private List<WatchNextRows.Row> previewCache = Collections.emptyList();
    private long previewAt;
    private boolean loaded;
    private String pendingScreen;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = Theme.load(this);
        store = ContinueStore.get(this);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0D17);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // the weather reading and the speed level are kept between starts
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true); // hold OK is the page's (remove a show, the dial), not text selection
        web.setHapticFeedbackEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public void onPageFinished(WebView v, String url) {
                loaded = true;
                js("ck.refresh()");
                if (pendingScreen != null) { js("ck.go('" + pendingScreen + "')"); pendingScreen = null; }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR || m.messageLevel() == ConsoleMessage.MessageLevel.WARNING)
                    Log.w("Illusion", "page: " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "CK");
        web.loadUrl(PAGE);
        setContentView(web);
        store.listen(storeChanged);
        handle(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        showing = true;
        theme = Theme.load(this);              // theme.sh restarts Home, but a reload here costs nothing
        web.onResume();
        web.requestFocus();
        MediaListenerService.sample(this);
        js("ck.visible(true)");
        load();
    }

    @Override protected void onPause() {
        super.onPause();
        showing = false;
        js("ck.visible(false)");
        web.onPause();
    }

    @Override protected void onDestroy() {
        store.unlisten(storeChanged);
        worker.shutdownNow();
        web.destroy();
        super.onDestroy();
    }

    /** Home pressed while Home shows: back to its first screen. The dial asks for a screen by intent. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (ACTION_OPEN.equals(intent == null ? null : intent.getAction())) handle(intent);
        else if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) js("ck.go('home')");
    }

    /** Back goes to the page (closes the dial, or returns to Home's first screen); Home itself never leaves. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) js("ck.back()");
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void handle(Intent intent) {
        if (intent == null || !ACTION_OPEN.equals(intent.getAction())) return;
        String screen = intent.getStringExtra(EXTRA_SCREEN);
        setIntent(new Intent(Intent.ACTION_MAIN));
        if (screen == null || !screen.matches("[a-z]{1,12}")) return;
        if (loaded) js("ck.go('" + screen + "')"); else pendingScreen = screen;
    }

    private void js(String code) { main.post(() -> { if (loaded) web.evaluateJavascript(code, null); }); }

    /** Reads Watch Next and the history off the main thread, then tells the page. */
    private void load() {
        Context app = getApplicationContext();
        worker.execute(() -> {
            long now = System.currentTimeMillis();
            List<WatchNextRows.Row> rows = WatchNext.query(app);
            List<ContinueList.Entry> merged = WatchNextRows.merge(WatchNextRows.pick(rows, now), store.snapshot(),
                    store::isDismissed, WatchNextRows.WALL_MAX);
            feed = merged;
            js("ck.refresh()");
            if (now - previewAt > 10 * 60_000L) { previewCache = WatchNext.queryPreview(app, 12); previewAt = now; }
            Set<String> watching = new HashSet<>();
            for (ContinueList.Entry e : merged) watching.add(e.pkg + "|" + e.title.toLowerCase(Locale.ROOT).trim());
            fresh = FreshRows.build(rows, previewCache, watching, now);
            js("ck.freshReady()");
        });
    }

    // ---- helpers ------------------------------------------------------------------------------

    private String label(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    private int color(String pkg) {
        Integer c = theme.dockColors.get(pkg);
        return c != null ? c : PALETTE[Math.abs(pkg.hashCode()) % PALETTE.length];
    }

    private static String hex(int c) { return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF); }

    private void start(Intent i, String what) {
        main.post(() -> {
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) { Toast.makeText(this, "Can't open " + what, Toast.LENGTH_SHORT).show(); }
        });
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private void launchApp(String pkg) {
        Intent i = launchIntent(pkg);
        if (i == null) { main.post(() -> Toast.makeText(this, "Can't open " + label(pkg), Toast.LENGTH_SHORT).show()); return; }
        store.appLaunch(pkg);
        start(i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), label(pkg));
    }

    private ContinueList.Entry find(String key) {
        for (ContinueList.Entry e : feed) if (e.key().equals(key)) return e;
        return null;
    }

    private static String poster(ContinueList.Entry e) {
        if (e.poster.startsWith("https://") || e.poster.startsWith("http://")) return e.poster;
        if (!e.video.isEmpty() && e.pkg.equals(YouTubeLookup.PKG)) return "https://i.ytimg.com/vi/" + e.video + "/hqdefault.jpg";
        return "";
    }

    /** What the page calls ("CK" in index.html). Runs on the WebView's bridge thread; returns JSON or nothing. */
    private final class Bridge {
        @JavascriptInterface public String home() {
            try {
                JSONObject o = new JSONObject().put("name", theme.greetingName).put("brand", theme.showBrand);
                JSONArray dock = new JSONArray();
                for (String pkg : theme.dockApps) {
                    if (dock.length() == 5) break;
                    if (launchIntent(pkg) == null) continue;   // not installed: the dock re-flows without it
                    dock.put(new JSONObject().put("pkg", pkg).put("label", label(pkg)).put("color", hex(color(pkg))));
                }
                o.put("dock", dock);
                if (theme.weather) o.put("weather", new JSONObject().put("lat", theme.weatherLat).put("lon", theme.weatherLon).put("f", theme.fahrenheit));
                return o.toString();
            } catch (Exception e) { Log.w("Illusion", "home()", e); return "{}"; }
        }

        @JavascriptInterface public String shows() {
            JSONArray out = new JSONArray();
            for (ContinueList.Entry e : feed) {
                try {
                    out.put(new JSONObject().put("key", e.key()).put("pkg", e.pkg).put("app", label(e.pkg))
                            .put("color", hex(color(e.pkg))).put("title", e.title).put("season", e.season)
                            .put("episode", e.episode).put("position", e.position).put("duration", e.duration)
                            .put("time", e.time).put("poster", poster(e)));
                } catch (Exception ignored) { }
            }
            return out.toString();
        }

        /** Installed apps: the dock's first, then the rest by name, with the TV's own system apps last. */
        @JavascriptInterface public String apps() {
            PackageManager pm = getPackageManager();
            List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
            found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            Set<String> seen = new HashSet<>();
            List<JSONObject> first = new ArrayList<>(), rest = new ArrayList<>();
            for (ResolveInfo ri : found) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || theme.hiddenApps.contains(pkg) || !seen.add(pkg)) continue;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean sys = (flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                        && !pkg.equals("com.android.vending") && !theme.dockApps.contains(pkg);
                try {
                    (sys ? rest : first).add(new JSONObject().put("pkg", pkg).put("label", String.valueOf(ri.loadLabel(pm)))
                            .put("color", hex(color(pkg))).put("sys", sys));
                } catch (Exception ignored) { }
            }
            Collections.sort(first, (a, b) -> {
                int da = theme.dockApps.indexOf(a.optString("pkg")), db = theme.dockApps.indexOf(b.optString("pkg"));
                if (da >= 0 || db >= 0) return Integer.compare(da < 0 ? 999 : da, db < 0 ? 999 : db);
                return a.optString("label").compareToIgnoreCase(b.optString("label"));
            });
            Collections.sort(rest, (a, b) -> a.optString("label").compareToIgnoreCase(b.optString("label")));
            JSONArray out = new JSONArray();
            for (JSONObject o : first) out.put(o);
            for (JSONObject o : rest) out.put(o);
            return out.toString();
        }

        /** The network for the Wi-Fi icon, and a USB drive if one is plugged in. */
        @JavascriptInterface public String status() {
            try {
                JSONObject o = new JSONObject();
                ConnectivityManager cm = getSystemService(ConnectivityManager.class);
                NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    o.put("online", true).put("quality", "wired");
                } else if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
                    @SuppressWarnings("deprecation") int rssi = wm == null ? 0 : wm.getConnectionInfo().getRssi();
                    o.put("online", true).put("quality", rssi >= 0 ? "good" : rssi >= -55 ? "excellent" : rssi >= -67 ? "good" : rssi >= -75 ? "fair" : "weak");
                } else {
                    o.put("online", false).put("quality", "offline");
                }
                StorageManager sm = getSystemService(StorageManager.class);
                if (sm != null) for (StorageVolume v : sm.getStorageVolumes()) {
                    if (v.isRemovable() && "mounted".equals(v.getState())) { o.put("usb", new JSONObject().put("name", v.getDescription(HomeActivity.this))); break; }
                }
                return o.toString();
            } catch (Exception e) { Log.w("Illusion", "status()", e); return "{}"; }
        }

        /** New for you: [{pkg, app, color, fresh, items:[{key, kind, title, season, episode, duration, time, poster, desc}]}] */
        @JavascriptInterface public String fresh() {
            JSONArray out = new JSONArray();
            for (FreshRows.Channel c : fresh) {
                try {
                    JSONArray items = new JSONArray();
                    for (FreshRows.Item i : c.items) {
                        String poster = i.poster.startsWith("https://") || i.poster.startsWith("http://") ? i.poster : "";
                        items.put(new JSONObject().put("key", i.key()).put("kind", i.kind).put("title", i.title)
                                .put("season", i.season).put("episode", i.episode).put("duration", i.duration)
                                .put("time", i.time).put("poster", poster).put("desc", i.desc));
                    }
                    out.put(new JSONObject().put("pkg", c.pkg).put("app", label(c.pkg)).put("color", hex(color(c.pkg)))
                            .put("fresh", c.fresh()).put("items", items));
                } catch (Exception ignored) { }
            }
            return out.toString();
        }

        @JavascriptInterface public void open(String pkg) { if (pkg != null) launchApp(pkg); }

        /** Plays a New for you item through the app's own link, else opens the app. */
        @JavascriptInterface public void play(String key) {
            for (FreshRows.Channel c : fresh) for (FreshRows.Item i : c.items) {
                if (!i.key().equals(key)) continue;
                ContinueList.Entry e = new ContinueList.Entry();
                e.pkg = i.pkg; e.title = i.title; e.intent = i.intent;
                Intent in = WatchNext.launch(HomeActivity.this, e);
                if (in != null) { store.appLaunch(i.pkg); start(in, i.title); } else launchApp(i.pkg);
                return;
            }
        }

        /** Resumes a show: the app's own Watch Next link, else a YouTube or title link, else the app. */
        @JavascriptInterface public void resume(String key) {
            ContinueList.Entry e = find(key);
            if (e == null) return;
            Intent i = WatchNext.launch(HomeActivity.this, e);
            if (i != null) { store.appLaunch(e.pkg); start(i, e.title); return; }
            PackageManager pm = getPackageManager();
            if (e.pkg.equals(YouTubeLookup.PKG) && !e.video.isEmpty()) {
                Intent yt = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + e.video
                        + "&t=" + (e.position / 1000) + "s")).setPackage(e.pkg);
                if (pm.resolveActivity(yt, 0) != null) { store.resume(e); start(yt, e.title); return; }
            }
            if (!e.link.isEmpty() && !e.appCard) {
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(TitleLinks.forApp(e.pkg, e.link))).setPackage(e.pkg);
                if (TitleLinks.NETFLIX.equals(e.pkg)) view.putExtra("source", "30");
                if (pm.resolveActivity(view, 0) != null) { store.resume(e); start(view, e.title); return; }
            }
            launchApp(e.pkg);
        }

        @JavascriptInterface public void remove(String key) { store.remove(key); load(); }
        @JavascriptInterface public void wifi() { start(new Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi"); }
        @JavascriptInterface public void settings() { start(new Intent(Settings.ACTION_SETTINGS), "Settings"); }
        @JavascriptInterface public void inputs() { start(new Intent("com.android.tv.action.VIEW_INPUTS"), "Inputs"); }

        /** USB drives: Sony's Media Player where there is one, else the storage settings. */
        @JavascriptInterface public void usb() {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.sony.dtv.smartmediaapp");
            start(i != null ? i : new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), "USB");
        }

        /** False when the dial service is off: only an accessibility service can turn the screen off. */
        @JavascriptInterface public boolean sleep(int minutes) { return DialService.sleepIn(minutes); }

        @JavascriptInterface public void dialDone(String screen) { }
    }
}
