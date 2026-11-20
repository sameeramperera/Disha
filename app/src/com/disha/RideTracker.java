package com.disha;

/**
 * Turns the bike's speed + odometer samples (about 3 a second) into rides. Pure Java, so it can
 * be tested on a PC; saving and notifications are done by the caller (BikeService).
 *
 * A ride starts the first time the bike moves (>= 3 km/h). It ends when the bike stops sending
 * (ignition off / out of range) for END_SILENCE_MS, or stands still for END_IDLE_MS. Its end is
 * the last moment the bike was moving, so the wait before switching off does not count.
 */
final class RideTracker {
    static final int MOVING_KMH = 3;
    static final long END_SILENCE_MS = 2 * 60_000L, END_IDLE_MS = 15 * 60_000L;
    /** Gaps longer than this between samples are not counted (app paused, link dropped). */
    static final long MAX_STEP_MS = 5_000L;
    /** Shorter rides are dropped (moving the bike in the yard). */
    static final double MIN_KM = 0.3;

    static final class Ride {
        long start, end;            // ms; end = last moment moving
        int odoStart, odoEnd;       // 0.1 km, odoEnd = odometer when last moving
        int maxKmh;
        long movingMs;
        double speedKm;             // distance from integrating speed, a fallback for the odometer
        int avgKmL;                 // cluster AVG MILEAGE when the ride ended, 0 = unknown

        double km() {
            int d = odoEnd - odoStart;
            return d >= 0 && d < 20000 ? d / 10.0 : speedKm;   // odometer unless it looks broken
        }

        long durationMs() { return Math.max(0, end - start); }

        /** Average while moving, km/h. */
        double avgKmh() { return movingMs < 10_000 ? 0 : km() / (movingMs / 3_600_000.0); }
    }

    interface Listener {
        void finished(Ride r);      // a ride worth keeping has ended
        void changed(Ride r);       // the ride in progress changed (for saving it now and then)
    }

    private final Listener listener;
    private Ride cur;
    private long lastSample, lastMoving;
    private int lastKmh;

    RideTracker(Listener l) { listener = l; }

    Ride current() { return cur; }

    /** Continue a ride saved before the app was closed (its samples restart from here). */
    void resume(Ride r, long lastMovingAt) {
        cur = r;
        lastMoving = lastMovingAt;
        lastSample = 0;
    }

    void sample(int kmh, int odo, long now) {
        if (odo <= 0) return;
        boolean moving = kmh >= MOVING_KMH;
        if (cur == null) {
            if (!moving) { lastSample = now; lastKmh = kmh; return; }
            cur = new Ride();
            cur.start = now;
            cur.odoStart = cur.odoEnd = odo;
            lastMoving = now;
        } else if (lastSample > 0) {
            long dt = now - lastSample;
            if (dt > 0 && dt <= MAX_STEP_MS) {
                // Count the step by the speed at its start; a step that is half moving counts as moving.
                if (lastKmh >= MOVING_KMH || moving) cur.movingMs += dt;
                cur.speedKm += lastKmh * dt / 3_600_000.0;
            }
        }
        if (moving) {
            lastMoving = now;
            cur.end = now;
            cur.odoEnd = Math.max(cur.odoEnd, odo);
        }
        cur.maxKmh = Math.max(cur.maxKmh, kmh);
        lastSample = now;
        lastKmh = kmh;
        listener.changed(cur);
    }

    /** Called every few seconds: ends the ride after silence or a long stop. */
    void tick(long now) {
        if (cur == null) return;
        boolean silent = lastSample == 0 ? now - lastMoving > END_SILENCE_MS : now - lastSample > END_SILENCE_MS;
        boolean idle = now - lastMoving > END_IDLE_MS;
        if (silent || idle) finish();
    }

    /** Ends the ride now (also when the app shuts down). */
    void finish() {
        Ride r = cur;
        cur = null;
        lastSample = 0;
        if (r == null) return;
        if (r.end <= r.start) r.end = r.start;
        if (r.km() >= MIN_KM) listener.finished(r);
        else listener.changed(null);
    }

    /** "12.4 km  ·  32 min  ·  avg 28  ·  max 67 km/h" */
    static String summary(Ride r) {
        return String.format(java.util.Locale.ROOT, "%.1f km  ·  %s  ·  avg %.0f  ·  max %d km/h",
                r.km(), duration(r.durationMs()), r.avgKmh(), r.maxKmh);
    }

    static String duration(long ms) {
        long min = Math.round(ms / 60_000.0);
        return min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
    }
}
