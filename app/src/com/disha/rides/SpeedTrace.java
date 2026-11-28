package com.disha.rides;

/**
 * A ride's speed over time for its speed graph: the average speed in each slice of STEP seconds
 * from the ride's start. A slice with no samples (link dropped, app closed) is a gap. When the ride
 * gets longer than MAX slices, pairs of slices are merged and the step doubles, so a ride of any
 * length keeps at most MAX points (5 s steps up to 50 min, 10 s up to 100 min, ...). Pure Java.
 *
 * Saved as text: "5:12,15,,18" = step in seconds, then whole km/h per slice, empty for a gap.
 */
public final class SpeedTrace {
    static final int MAX = 600, FIRST_STEP = 5;

    private int step = FIRST_STEP;          // seconds per point
    private float[] sum = new float[64];    // km/h summed over the slice's samples
    private int[] cnt = new int[64];        // samples in the slice, 0 = gap
    private int n;                          // slices so far

    /** A sample msSinceStart after the ride started. */
    synchronized void add(long msSinceStart, int kmh) {
        if (msSinceStart < 0 || kmh < 0) return;
        int i = (int) (msSinceStart / (step * 1000L));
        while (i >= MAX) { halve(); i = (int) (msSinceStart / (step * 1000L)); }
        if (i >= sum.length) {
            int len = Math.min(MAX, Math.max(i + 1, sum.length * 2));
            sum = java.util.Arrays.copyOf(sum, len);
            cnt = java.util.Arrays.copyOf(cnt, len);
        }
        sum[i] += kmh;
        cnt[i]++;
        n = Math.max(n, i + 1);
    }

    private void halve() {
        int m = (n + 1) / 2;
        for (int j = 0; j < m; j++) {
            int a = 2 * j, b = a + 1;
            float s = sum[a]; int c = cnt[a];
            if (b < n) { s += sum[b]; c += cnt[b]; }
            sum[j] = s; cnt[j] = c;
        }
        for (int j = m; j < n; j++) { sum[j] = 0; cnt[j] = 0; }
        n = m;
        step *= 2;
    }

    /** Seconds per point. */
    public synchronized int step() { return step; }

    /** km/h per point, -1 for a gap. A copy, so it can be drawn while the ride goes on. */
    public synchronized float[] points() {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = cnt[i] == 0 ? -1 : sum[i] / cnt[i];
        return out;
    }

    /** As text; untilMs > 0 leaves out the points after it (the wait after the ride's last movement). */
    synchronized String encode(long untilMs) {
        int keep = n;
        if (untilMs > 0) keep = Math.min(n, (int) (untilMs / (step * 1000L)) + 1);
        StringBuilder s = new StringBuilder(keep * 3 + 4).append(step).append(':');
        for (int i = 0; i < keep; i++) {
            if (i > 0) s.append(',');
            if (cnt[i] > 0) s.append(Math.round(sum[i] / cnt[i]));
        }
        return s.toString();
    }

    /** null for empty or unreadable text. */
    static SpeedTrace decode(String text) {
        if (text == null) return null;
        int colon = text.indexOf(':');
        if (colon <= 0) return null;
        SpeedTrace t = new SpeedTrace();
        try {
            t.step = Integer.parseInt(text.substring(0, colon));
            if (t.step <= 0) return null;
            String[] v = text.substring(colon + 1).split(",", -1);
            int len = Math.min(v.length, MAX);
            t.sum = new float[Math.max(64, len)];
            t.cnt = new int[t.sum.length];
            for (int i = 0; i < len; i++) {
                if (v[i].isEmpty()) continue;
                t.sum[i] = Integer.parseInt(v[i]);
                t.cnt[i] = 1;
            }
            t.n = len;
        } catch (NumberFormatException e) {
            return null;
        }
        return t.n == 0 ? null : t;
    }
}
