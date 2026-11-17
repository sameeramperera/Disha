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

    /** Navigation ended: log icons again next trip (the service lives for days, logs don't). */
    static void forgetLogged() {
        logged.clear();
    }

    /** Logs the picture the first time a hash is seen (bounded, so the log stays readable). */
    static void logIfNew(Result r) {
        if (r == null || logged.contains(r.hash) || logged.size() > 60) return;
        logged.add(r.hash);
        BikeService.log("ICON " + r.hash + " picto=" + r.picto + (r.picto < 0 ? " (unknown)" : "") + " " + r.debug);
        for (String line : r.ascii.split("\n")) BikeService.log("ICON|" + line);
    }
}
