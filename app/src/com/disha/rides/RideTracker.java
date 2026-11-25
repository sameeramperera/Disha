package com.disha.rides;

import com.disha.bike.BikeService;

/**
 * Turns the bike's speed + odometer samples (about 3 a second) into rides. Pure Java, so it can
 * be tested on a PC; saving and notifications are done by the caller (BikeService).
 *
 * A ride starts the first time the bike moves (>= 3 km/h). It ends when the bike stops sending
 * (ignition off / out of range) for END_SILENCE_MS, or stands still for END_IDLE_MS. Its end is
 * the last moment the bike was moving, so the wait before switching off does not count.
 */
public final class RideTracker {
    static final int MOVING_KMH = 3;
    static final long END_SILENCE_MS = 2 * 60_000L, END_IDLE_MS = 15 * 60_000L;
    /** Gaps longer than this between samples are not counted (app paused, link dropped). */
    static final long MAX_STEP_MS = 5_000L;
    /** Shorter rides are dropped (moving the bike in the yard). */
    static final double MIN_KM = 0.3;
    /** RPM is counted in 1000-rpm bands, the last one is 9000 and up. Throttle at or above HARD is "hard". */
    public static final int RPM_BANDS = 10, HARD_THROTTLE = 50;

    public static final class Ride {
        public long id;                    // row in the rides table, 0 = not saved (still riding)
        public long start, end;            // ms; end = last moment moving
        public int odoStart, odoEnd;       // 0.1 km, odoEnd = odometer when last moving
        public int maxKmh;
        public long movingMs;
        double speedKm;             // distance from integrating speed, a fallback for the odometer
        public int avgKmL;                 // cluster AVG MILEAGE when the ride ended, 0 = unknown

        // Engine data (rides from 0.3.x on; all 0 for older rides). Times in ms.
        public long engineMs;              // engine running (rpm > 0)
        public long idleMs;                // engine running, standing still
        public long ecoMs, powerMs;        // moving, by ride mode
        public double ecoKm, powerKm;      // distance by ride mode (from speed)
        public long[] gearMs = new long[6];          // moving, by gear: 0 = neutral, 1..5
        public int shifts;                 // gear changes between 1..5
        public long[] rpmBandMs = new long[RPM_BANDS]; // engine running, by 1000-rpm band
        long rpmMovingMs;           // moving with rpm known
        double rpmSum;              // rpm x ms while moving, for the average
        public int maxRpm;
        public int rpmAlerts;              // times the revs reached the ECO limit in ECO
        public long throttleMs, hardMs;    // moving with throttle known; at HARD_THROTTLE or more
        double throttleSum;         // throttle x ms while moving

        public boolean hasEngine() { return engineMs > 0; }
        public long modeMs() { return ecoMs + powerMs; }
        public int avgRpm() { return rpmMovingMs < 10_000 ? 0 : (int) Math.round(rpmSum / rpmMovingMs); }
        public int avgThrottle() { return throttleMs < 10_000 ? 0 : (int) Math.round(throttleSum / throttleMs); }
        /** 1..5 with the most moving time, or 0. */
        public int topGear() {
            int g = 0;
            for (int i = 1; i < 6; i++) if (gearMs[i] > gearMs[g]) g = i;
            return gearMs[g] > 0 ? g : 0;
        }

        /** Adds another ride's engine data (for totals over a period). */
        public void addEngine(Ride o) {
            engineMs += o.engineMs; idleMs += o.idleMs; ecoMs += o.ecoMs; powerMs += o.powerMs;
            ecoKm += o.ecoKm; powerKm += o.powerKm; shifts += o.shifts; rpmMovingMs += o.rpmMovingMs;
            rpmSum += o.rpmSum; maxRpm = Math.max(maxRpm, o.maxRpm); rpmAlerts += o.rpmAlerts;
            throttleMs += o.throttleMs; hardMs += o.hardMs; throttleSum += o.throttleSum;
            for (int i = 0; i < gearMs.length; i++) gearMs[i] += o.gearMs[i];
            for (int i = 0; i < rpmBandMs.length; i++) rpmBandMs[i] += o.rpmBandMs[i];
        }

        public double km() {
            int d = odoEnd - odoStart;
            return d >= 0 && d < 20000 ? d / 10.0 : speedKm;   // odometer unless it looks broken
        }

        public long durationMs() { return Math.max(0, end - start); }

        /** Average while moving, km/h. */
        public double avgKmh() { return movingMs < 10_000 ? 0 : km() / (movingMs / 3_600_000.0); }
    }

    public interface Listener {
        void finished(Ride r);      // a ride worth keeping has ended
        void changed(Ride r);       // the ride in progress changed (for saving it now and then)
    }

    private final Listener listener;
    private Ride cur;
    private long lastSample, lastMoving;
    private int lastKmh;
    private int lastRpm = -1, lastGear = -1, lastMode = -1, lastThrottle = -1, lastDriveGear = -1;
    private boolean rpmArmed = true;
    /** The ECO RPM alert level from Settings (counted per ride whether or not the alert is on). */
    public volatile int rpmLimit = 5000;

    public RideTracker(Listener l) { listener = l; }

    public Ride current() { return cur; }

    /** Continue a ride saved before the app was closed (its samples restart from here). */
    public void resume(Ride r, long lastMovingAt) {
        cur = r;
        lastMoving = lastMovingAt;
        lastSample = 0;
        lastDriveGear = -1;
    }

    /** Speed and odometer only (no engine data). */
    public void sample(int kmh, int odo, long now) { sample(kmh, odo, now, -1, -1, -1, -1); }

    /** rpm, gear (0 = neutral), mode (0 ECO, 1 POWER), throttle: -1 when not known. */
    public void sample(int kmh, int odo, long now, int rpm, int gear, int mode, int throttle) {
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
                boolean wasMoving = lastKmh >= MOVING_KMH || moving;
                if (wasMoving) cur.movingMs += dt;
                double stepKm = lastKmh * dt / 3_600_000.0;
                cur.speedKm += stepKm;
                engineStep(dt, wasMoving, stepKm);
            }
        }
        if (gear >= 1 && gear <= 5) {
            if (lastDriveGear >= 1 && gear != lastDriveGear) cur.shifts++;
            lastDriveGear = gear;
        }
        if (rpm > cur.maxRpm) cur.maxRpm = rpm;
        if (rpm >= 0) {
            if (rpm < rpmLimit - 500) rpmArmed = true;
            else if (rpmArmed && rpm >= rpmLimit && mode == 0) { cur.rpmAlerts++; rpmArmed = false; }
        }
        if (moving) {
            lastMoving = now;
            cur.end = now;
            cur.odoEnd = Math.max(cur.odoEnd, odo);
        }
        cur.maxKmh = Math.max(cur.maxKmh, kmh);
        lastSample = now;
        lastKmh = kmh;
        lastRpm = rpm; lastGear = gear; lastMode = mode; lastThrottle = throttle;
        listener.changed(cur);
    }

    /** One step of dt ms, by the values at its start (like the speed). */
    private void engineStep(long dt, boolean moving, double stepKm) {
        if (lastRpm > 0) {
            cur.engineMs += dt;
            cur.rpmBandMs[Math.min(RPM_BANDS - 1, lastRpm / 1000)] += dt;
            if (lastKmh == 0) cur.idleMs += dt;
        }
        if (!moving) return;
        if (lastMode == 0) { cur.ecoMs += dt; cur.ecoKm += stepKm; }
        else if (lastMode == 1) { cur.powerMs += dt; cur.powerKm += stepKm; }
        if (lastGear >= 0 && lastGear <= 5) cur.gearMs[lastGear] += dt;
        if (lastRpm > 0) { cur.rpmMovingMs += dt; cur.rpmSum += lastRpm * (double) dt; }
        if (lastThrottle >= 0) {
            cur.throttleMs += dt;
            cur.throttleSum += lastThrottle * (double) dt;
            if (lastThrottle >= HARD_THROTTLE) cur.hardMs += dt;
        }
    }

    /** Called every few seconds: ends the ride after silence or a long stop. */
    public void tick(long now) {
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
        lastDriveGear = -1;
        if (r == null) return;
        if (r.end <= r.start) r.end = r.start;
        if (r.km() >= MIN_KM) listener.finished(r);
        else listener.changed(null);
    }

    /** "12.4 km  ·  32 min  ·  avg 28  ·  max 67 km/h" */
    public static String summary(Ride r) {
        return String.format(java.util.Locale.ROOT, "%.1f km  ·  %s  ·  avg %.0f  ·  max %d km/h",
                r.km(), duration(r.durationMs()), r.avgKmh(), r.maxKmh);
    }

    public static String duration(long ms) {
        long min = Math.round(ms / 60_000.0);
        return min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
    }
}
