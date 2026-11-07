package com.projectillusion.home;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Project Illusion's hold-OK dial over any app: hold OK for half a second and the dial from assets/home/index.html
 * (its #dial mode) opens on top of whatever is playing. Left and Right pick, OK runs, Back closes. Its
 * entries open Home's screens (Continue watching, all apps), Wi-Fi settings, the input switcher, or a
 * 30-minute sleep timer (the screen is turned off with the lock-screen global action).
 *
 * OK is passed to the app until it has been held long enough, so a short press works as before. The app
 * has then seen the start of a long press and no release. On Home itself the page handles hold OK.
 * build-and-install switches it on (DIAL_SERVICE=0 leaves it off).
 */
public class DialService extends AccessibilityService {
    private static final long HOLD_MS = 500;
    private static volatile DialService instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView overlay;
    private boolean swallow;
    /** The OK that picked a dial entry: its release must not reach the screen the entry opens. */
    private boolean eatOkUp;
    private final Runnable sleepNow = () -> {
        if (android.os.Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    };

    /** Called by Home's page: arm the sleep timer. False when this service is not running. */
    static boolean sleepIn(int minutes) {
        DialService s = instance;
        if (s == null || minutes <= 0) return false;
        s.main.post(() -> {
            s.main.removeCallbacks(s.sleepNow);
            s.main.postDelayed(s.sleepNow, minutes * 60_000L);
            Toast.makeText(s, "Sleep in " + minutes + " min", Toast.LENGTH_SHORT).show();
        });
        return true;
    }

    @Override protected void onServiceConnected() { super.onServiceConnected(); instance = this; }

    @Override public boolean onUnbind(Intent intent) { instance = null; closeDial(); return super.onUnbind(intent); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (overlay != null) {
            if (code == KeyEvent.KEYCODE_HOME) { closeDial(); return false; }
            if (isOk(code) && !down && swallow) { swallow = false; return true; }
            if (down) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) key("left");
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN) key("right");
                else if (isOk(code) && e.getRepeatCount() == 0 && !swallow) { eatOkUp = true; key("ok"); }
                else if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) closeDial();
            }
            return true;
        }
        // On Home the page (or the stack) owns hold OK. Asked of Home itself: apps that draw their own
        // screen (Netflix) send no window events, so the foreground app can't be told from events alone.
        if (isOk(code) && !down && eatOkUp) { eatOkUp = false; return true; }
        if (isOk(code) && down) eatOkUp = false;
        if (!isOk(code) || HomeActivity.showing) return false;
        // Remotes differ: some repeat a held key, the BRAVIA's sends one press and one release seconds
        // later. So a timer decides: still down after HOLD_MS means a hold.
        if (down) {
            if (e.getRepeatCount() == 0) {
                swallow = false;
                main.removeCallbacks(holdCheck);
                main.postDelayed(holdCheck, HOLD_MS);
                return false;
            }
            return swallow;
        }
        main.removeCallbacks(holdCheck);
        return swallow;
    }

    /** OK has been down for HOLD_MS: open the dial and keep the release from reaching the app. */
    private final Runnable holdCheck = () -> { swallow = true; openDial(); };

    private void key(String k) {
        if (overlay != null) overlay.evaluateJavascript("window.ck&&ck.key('" + k + "')", null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void openDial() {
        if (overlay != null) return;
        WebView web = new WebView(this);
        web.setBackgroundColor(0);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "CK");
        web.loadUrl("file:///android_asset/home/index.html#dial");
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try {
            getSystemService(WindowManager.class).addView(web, lp);
            overlay = web;
        } catch (Exception ex) {
            android.util.Log.w("Illusion", "dial overlay", ex);
            web.destroy();
            swallow = false;
        }
    }

    private void closeDial() {
        WebView web = overlay;
        overlay = null;
        if (web == null) return;
        try { getSystemService(WindowManager.class).removeView(web); } catch (Exception ignored) { }
        web.destroy();
    }

    /** Opens one of Home's screens, or Home itself. */
    private void openHome(String panel) {
        Intent i = new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (panel.equals("home")) i.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        else i.setAction(HomeActivity.ACTION_OPEN).putExtra(HomeActivity.EXTRA_SCREEN, panel);
        startActivity(i);
    }

    /** The dial page's window onto the TV: only what the dial needs. */
    private final class Bridge {
        @JavascriptInterface public void dialDone(String panel) {
            main.post(() -> {
                closeDial();
                if (panel != null && panel.matches("[a-z]{1,12}")) openHome(panel);
            });
        }
        @JavascriptInterface public void inputs() {
            main.post(() -> {
                try { startActivity(new Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception ignored) { }
            });
        }
        @JavascriptInterface public boolean sleep(int minutes) { return sleepIn(minutes); }
        @JavascriptInterface public void wifi() {
            main.post(() -> {
                try { startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Exception ignored) { }
            });
        }
        @JavascriptInterface public String home() { return "{}"; }
        @JavascriptInterface public String shows() { return "[]"; }
        @JavascriptInterface public String apps() { return "[]"; }
        @JavascriptInterface public String status() { return "{}"; }
    }
}
