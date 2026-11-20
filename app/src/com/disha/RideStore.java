package com.disha;

import android.content.Context;
import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Finished rides live in the database (RideDb), all of them. The ride in progress is small and
 * saved often, so it stays in SharedPreferences, where it survives the app being closed mid-ride.
 */
final class RideStore {
    private static final String KEY_ACTIVE = "rideActive";
    private static final String COLS = "start_ms, end_ms, odo_start, odo_end, max_kmh, moving_ms, speed_km, avg_kml";

    private RideStore() {}

    /** The newest rides first, at most limit of them. */
    static List<RideTracker.Ride> recent(Context c, int limit) {
        return query(c, "SELECT " + COLS + " FROM " + RideDb.T + " ORDER BY start_ms DESC LIMIT " + limit);
    }

    /** Rides that started in [from, to), oldest first. */
    static List<RideTracker.Ride> between(Context c, long from, long to) {
        return query(c, "SELECT " + COLS + " FROM " + RideDb.T + " WHERE start_ms >= " + from
                + " AND start_ms < " + to + " ORDER BY start_ms");
    }

    /** When the first ride started, or 0 with no rides. */
    static long firstStart(Context c) {
        try (Cursor k = RideDb.get(c).rawQuery("SELECT MIN(start_ms) FROM " + RideDb.T, null)) {
            return k.moveToFirst() && !k.isNull(0) ? k.getLong(0) : 0;
        }
    }

    /** Odometer at the end of the last ride (0.1 km), or 0. */
    static int lastOdo(Context c) {
        try (Cursor k = RideDb.get(c).rawQuery(
                "SELECT odo_end FROM " + RideDb.T + " ORDER BY start_ms DESC LIMIT 1", null)) {
            return k.moveToFirst() ? k.getInt(0) : 0;
        }
    }

    private static List<RideTracker.Ride> query(Context c, String sql) {
        List<RideTracker.Ride> out = new ArrayList<>();
        try (Cursor k = RideDb.get(c).rawQuery(sql, null)) {
            while (k.moveToNext()) {
                RideTracker.Ride r = new RideTracker.Ride();
                r.start = k.getLong(0);
                r.end = k.getLong(1);
                r.odoStart = k.getInt(2);
                r.odoEnd = k.getInt(3);
                r.maxKmh = k.getInt(4);
                r.movingMs = k.getLong(5);
                r.speedKm = k.getDouble(6);
                r.avgKmL = k.getInt(7);
                out.add(r);
            }
        }
        return out;
    }

    static void add(Context c, RideTracker.Ride r) {
        RideDb.get(c).insert(RideDb.T, null, RideDb.values(r));
        BikeService.prefs(c).edit().remove(KEY_ACTIVE).apply();
    }

    static void clear(Context c) {
        int n = RideDb.get(c).delete(RideDb.T, null, null);
        BikeService.log("RIDE history cleared (" + n + " rides deleted)");
    }

    /** The ride in progress, so it survives the app being closed mid-ride. null clears it. */
    static void saveActive(Context c, RideTracker.Ride r) {
        if (r == null) { BikeService.prefs(c).edit().remove(KEY_ACTIVE).apply(); return; }
        BikeService.prefs(c).edit().putString(KEY_ACTIVE, toJson(r).toString()).apply();
    }

    static RideTracker.Ride active(Context c) {
        String s = BikeService.prefs(c).getString(KEY_ACTIVE, null);
        if (s == null) return null;
        try { return fromJson(new JSONObject(s)); } catch (JSONException e) { return null; }
    }

    /**
     * When the bike link is not running, a saved ride in progress whose last movement is long past
     * is really over: move it to the history (or drop it if too short).
     */
    static void finishStale(Context c) {
        RideTracker.Ride r = active(c);
        if (r == null || System.currentTimeMillis() - r.end < RideTracker.END_SILENCE_MS) return;
        if (r.km() >= RideTracker.MIN_KM) add(c, r); else saveActive(c, null);
    }

    private static JSONObject toJson(RideTracker.Ride r) {
        JSONObject o = new JSONObject();
        try {
            o.put("start", r.start).put("end", r.end).put("odoStart", r.odoStart).put("odoEnd", r.odoEnd)
                    .put("max", r.maxKmh).put("moving", r.movingMs).put("speedKm", r.speedKm);
        } catch (JSONException ignored) { }
        return o;
    }

    /** Also reads the old (before the database) history entries, which had the same fields. */
    static RideTracker.Ride fromJson(JSONObject o) {
        RideTracker.Ride r = new RideTracker.Ride();
        r.start = o.optLong("start");
        r.end = o.optLong("end");
        r.odoStart = o.optInt("odoStart");
        r.odoEnd = o.optInt("odoEnd");
        r.maxKmh = o.optInt("max");
        r.movingMs = o.optLong("moving");
        r.speedKm = o.optDouble("speedKm", 0);
        return r;
    }
}
