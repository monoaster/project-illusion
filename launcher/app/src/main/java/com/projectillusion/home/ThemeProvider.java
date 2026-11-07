package com.projectillusion.home;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets tools/theme.sh write config.json on TVs where the adb shell can't reach Android/data (some
 * Android 11 firmware). Only that one file, and only for callers holding WRITE_SECURE_SETTINGS, which
 * the adb shell has and ordinary apps can't get:
 *
 *   content write --uri content://com.projectillusion.home.theme/config.json < /data/local/tmp/config.json
 *   content delete --uri content://com.projectillusion.home.theme/config.json
 */
public class ThemeProvider extends ContentProvider {
    private File file(Uri uri) throws FileNotFoundException {
        File dir = Theme.dir(getContext());
        if (dir == null || !uri.getPathSegments().equals(java.util.Collections.singletonList(Theme.FILE)))
            throw new FileNotFoundException(String.valueOf(uri));
        if (!dir.isDirectory() && !dir.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return new File(dir, Theme.FILE);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int m = mode.contains("w") ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE
                | ParcelFileDescriptor.MODE_TRUNCATE : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(file(uri), m);
    }

    @Override public int delete(Uri uri, String s, String[] a) {
        try { return file(uri).delete() ? 1 : 0; } catch (FileNotFoundException e) { return 0; }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
