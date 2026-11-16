package com.disha;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import java.util.HashSet;
import java.util.Set;

/**
 * Google Maps puts the turn arrow only in the notification's large icon; the text is just the
 * road name. This turns that icon into a 32x32 "ink" mask, a hash for matching, and an ASCII
 * picture for the log so we can see which arrow each hash is.
 */
final class IconReader {
    static final int N = 32;
    private static final Set<String> logged = new HashSet<>();

    private IconReader() {}

    static final class Result {
        String hash = "";    // N*N/4 hex chars
        String ascii = "";   // N lines of '#'/'.'
        int picto = -1;      // from IconTable or ArrowShape, -1 if unknown
        String debug = "";   // how ArrowShape decided
    }

    static Result read(Context ctx, Notification n) {
        try {
            Icon icon = n.getLargeIcon();
            if (icon == null) return null;
            Drawable d = icon.loadDrawable(ctx);
            if (d == null) return null;
            Bitmap bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, N, N);
            d.draw(c);

            int[] px = new int[N * N];
            bmp.getPixels(px, 0, N, 0, 0, N, N);
            bmp.recycle();

            // Ink = opaque pixels, unless the icon is a filled square, then bright pixels.
            int opaque = 0;
            long lumSum = 0;
            int[] lum = new int[px.length];
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                if (Color.alpha(p) > 128) opaque++;
                lum[i] = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100;
                lumSum += lum[i];
            }
            boolean useLum = opaque > px.length * 9 / 10;
            int mean = (int) (lumSum / px.length);
            boolean[] ink = new boolean[px.length];
            // Faint = half-transparent: Maps draws the unused part of a roundabout ring this way.
            boolean[] faint = new boolean[px.length];
            int faintCount = 0;
            for (int i = 0; i < px.length; i++) {
                ink[i] = useLum ? lum[i] > mean : Color.alpha(px[i]) > 128;
                faint[i] = !useLum && !ink[i] && Color.alpha(px[i]) > 40;
            }
            // Only count faint pixels away from the solid arrow: the arrow's own soft (anti-aliased)
            // edges are faint too, and must not look like a roundabout ring.
            for (int i = 0; i < px.length; i++) if (faint[i] && !nextToInk(ink, i)) faintCount++;

            Result r = new Result();
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < px.length; i += 4) {
                int nib = (ink[i] ? 8 : 0) | (ink[i + 1] ? 4 : 0) | (ink[i + 2] ? 2 : 0) | (ink[i + 3] ? 1 : 0);
                hex.append(Character.forDigit(nib, 16));
            }
            r.hash = hex.toString();
            StringBuilder art = new StringBuilder();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) art.append(ink[y * N + x] ? '#' : faint[y * N + x] ? '+' : '.');
                if (y < N - 1) art.append('\n');
            }
            r.ascii = art.toString();
            r.picto = IconTable.lookup(ink);
            if (r.picto < 0) {
                r.picto = ArrowShape.classify(ink, N, MapsParser.leftHandTraffic);
                r.debug = ArrowShape.lastDebug;
                // A faint ring around a turn arrow means it is a roundabout; the arrow gives the exit.
                if (faintCount >= 15 && !Double.isNaN(ArrowShape.lastAngle) && !isRoundabout(r.picto)) {
                    r.picto = ArrowShape.roundaboutExit(ArrowShape.lastAngle, MapsParser.leftHandTraffic);
                    r.debug += " faint ring (" + faintCount + " px) -> roundabout";
                }
            } else {
                r.debug = "known icon";
            }
            return r;
        } catch (Exception e) {
            BikeService.log("Icon read failed: " + e);
            return null;
        }
    }

    private static boolean nextToInk(boolean[] ink, int i) {
        int x = i % N, y = i / N;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < N && ny < N && ink[ny * N + nx]) return true;
            }
        return false;
    }

    static boolean isRoundabout(int p) {
        return TvsProtocol.isRoundabout(p);
    }

    /** Navigation ended: dump icons again next trip (the service lives for days, logs don't). */
    static void forgetLogged() {
        logged.clear();
        saved.clear();
    }

    private static final Set<String> saved = new HashSet<>();

    /**
     * Saves the full-size Maps arrow as a PNG in Pictures/Disha (shows in the Gallery), once per
     * icon per trip. The file name says what we made of it, so wrong ones are easy to spot:
     * 0651_p66_500m_Main-Street.png
     */
    static void saveIfNew(Context ctx, Notification n, Result r, String dist, String road) {
        if (r == null || saved.contains(r.hash) || saved.size() > 200) return;
        saved.add(r.hash);
        try {
            Icon icon = n.getLargeIcon();
            if (icon == null) return;
            Drawable d = icon.loadDrawable(ctx);
            if (d == null) return;
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 128;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 128;
            final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(bmp));
            String time = new java.text.SimpleDateFormat("MMdd_HHmmss", java.util.Locale.ROOT)
                    .format(new java.util.Date());
            String clean = (dist + "_" + road).replaceAll("[^A-Za-z0-9.]+", "-");
            if (clean.length() > 40) clean = clean.substring(0, 40);
            final String name = time + "_p" + r.picto + "_" + clean + ".png";
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                try {
                    writePng(app, bmp, name);
                    BikeService.log("Saved icon " + name);
                } catch (Exception e) {
                    BikeService.log("Icon save failed: " + e);
                } finally {
                    bmp.recycle();
                }
            }).start();
        } catch (Exception e) {
            BikeService.log("Icon save failed: " + e);
        }
    }

    private static void writePng(Context ctx, Bitmap bmp, String name) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");
            v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Disha");
            android.net.Uri uri = ctx.getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new java.io.IOException("MediaStore insert failed");
            try (java.io.OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } else {
            java.io.File dir = new java.io.File(ctx.getExternalFilesDir(null), "icons");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("no dir " + dir);
            try (java.io.OutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name))) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }

    /** Logs the picture the first time a hash is seen (bounded, so the log stays readable). */
    static void logIfNew(Result r) {
        if (r == null || logged.contains(r.hash) || logged.size() > 60) return;
        logged.add(r.hash);
        BikeService.log("ICON " + r.hash + " picto=" + r.picto + (r.picto < 0 ? " (unknown)" : "") + " " + r.debug);
        for (String line : r.ascii.split("\n")) BikeService.log("ICON|" + line);
    }
}
