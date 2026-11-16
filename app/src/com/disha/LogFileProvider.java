package com.disha;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets other apps (WhatsApp, Gmail, Drive ...) read a shared log file. Minimal stand-in for
 * AndroidX FileProvider: read-only, and only files directly inside cache/logs/.
 * URI: content://com.disha.logs/<file name>
 */
public final class LogFileProvider extends ContentProvider {
    static final String AUTHORITY = "com.disha.logs";

    static File dir(Context c) {
        return new File(c.getCacheDir(), "logs");
    }

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(f.getName()).build();
    }

    /** The file a URI points to, or null if it is not a plain name inside cache/logs/. */
    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("\\") || name.startsWith(".")) return null;
        File f = new File(dir(getContext()), name);
        return f.isFile() ? f : null;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Receiving apps ask for the file name and size before attaching it. */
    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        File f = fileFor(uri);
        if (f == null) return null;
        if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        java.util.List<String> cols = new java.util.ArrayList<>();
        java.util.List<Object> vals = new java.util.ArrayList<>();
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) { cols.add(col); vals.add(f.getName()); }
            else if (OpenableColumns.SIZE.equals(col)) { cols.add(col); vals.add(f.length()); }
        }
        MatrixCursor c = new MatrixCursor(cols.toArray(new String[0]), 1);
        c.addRow(vals.toArray());
        return c;
    }

    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException("read-only"); }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
