package com.projectillusion.home;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets Continue watching learn what plays: notification access is used only to watch media sessions
 * (title, position, length). Notifications themselves are never read.
 */
public class MediaListenerService extends NotificationListenerService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaController, MediaController.Callback> callbacks = new HashMap<>();
    private MediaSessionManager manager;
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::sessions;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            for (MediaController c : callbacks.keySet()) capture(MediaListenerService.this, c);
            handler.postDelayed(this, 5000);
        }
    };
    @Override public void onListenerConnected() {
        manager = getSystemService(MediaSessionManager.class);
        try {
            manager.addOnActiveSessionsChangedListener(changed, new ComponentName(this, MediaListenerService.class), handler);
            sessions(manager.getActiveSessions(new ComponentName(this, MediaListenerService.class)));
            handler.removeCallbacks(tick); handler.post(tick);
        } catch (Exception e) { Log.w("Illusion", "continue sessions unavailable", e); }
    }
    private void sessions(List<MediaController> active) {
        for (Map.Entry<MediaController, MediaController.Callback> e : callbacks.entrySet()) e.getKey().unregisterCallback(e.getValue());
        callbacks.clear();
        if (active == null) return;
        for (MediaController c : active) {
            MediaController.Callback callback = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState state) { capture(MediaListenerService.this, c); }
                @Override public void onMetadataChanged(MediaMetadata metadata) { capture(MediaListenerService.this, c); }
                @Override public void onSessionDestroyed() { c.unregisterCallback(this); callbacks.remove(c); }
            };
            callbacks.put(c, callback); c.registerCallback(callback, handler); capture(this, c);
        }
    }
    static void sample(Context ctx) {
        try {
            MediaSessionManager m = ctx.getSystemService(MediaSessionManager.class);
            for (MediaController c : m.getActiveSessions(new ComponentName(ctx, MediaListenerService.class))) capture(ctx, c);
        } catch (Exception e) { Log.w("Illusion", "continue sample unavailable: " + e); }
    }
    private static final Map<String, String> reported = new HashMap<>();
    private static void capture(Context ctx, MediaController c) {
        try {
            PlaybackState p = c.getPlaybackState();
            if (p == null) return;
            MediaMetadata m = c.getMetadata();
            String title = m == null ? "" : m.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null || title.trim().isEmpty()) title = m == null ? "" : m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String video = m == null ? "" : ContinueStore.videoId(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
            if (video.isEmpty() && m != null) video = ContinueStore.videoId(m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI));
            String metadata = "title=" + title + " duration=" + (m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION))
                    + " mediaIdPresent=" + (m != null && m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) != null)
                    + " mediaUriPresent=" + (m != null && m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI) != null);
            if (!metadata.equals(reported.get(c.getPackageName()))) {
                if (reported.size() > 32) reported.clear();
                reported.put(c.getPackageName(), metadata);
                Log.d("Illusion", "continue " + c.getPackageName() + " " + metadata);
            }
            String app = "";
            try {
                app = ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(c.getPackageName(), 0)).toString();
                if (app.equalsIgnoreCase(title)) title = "";
            } catch (Exception ignored) { }
            if (c.getPackageName().equals(ctx.getPackageName())) return;
            long position = p.getPosition();
            if (position < 0) return;
            long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (p.getState() == PlaybackState.STATE_PLAYING && p.getLastPositionUpdateTime() > 0) {
                long elapsed = android.os.SystemClock.elapsedRealtime() - p.getLastPositionUpdateTime();
                if (elapsed >= 0) position += (long) (elapsed * p.getPlaybackSpeed());
                if (duration > 0) position = Math.min(position, duration);
            }
            ContinueStore store = ContinueStore.get(ctx);
            store.update(c.getPackageName(), title, video, position, duration, p.getState(),
                    m == null ? "" : m.getString("android.media.metadata.TV_SEASON_NUMBER"),
                    m == null ? "" : m.getString("android.media.metadata.TV_EPISODE_NUMBER"), app);
            // YouTube names the video but gives no id: look the id up so the card can resume.
            if (YouTubeLookup.PKG.equals(c.getPackageName()) && video.isEmpty() && title != null && !title.trim().isEmpty()) {
                store.lookUpYouTube(title, m == null ? "" : m.getString(MediaMetadata.METADATA_KEY_ARTIST));
            }
        } catch (Exception e) { Log.w("Illusion", "continue playback skipped", e); }
    }
    @Override public void onListenerDisconnected() { stop(); }
    private void stop() {
        handler.removeCallbacks(tick);
        if (manager != null) manager.removeOnActiveSessionsChangedListener(changed);
        for (Map.Entry<MediaController, MediaController.Callback> e : callbacks.entrySet()) e.getKey().unregisterCallback(e.getValue());
        callbacks.clear();
    }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
}
