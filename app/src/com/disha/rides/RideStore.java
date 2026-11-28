package com.disha.rides;

import com.disha.bike.BikeService;

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
public final class RideStore {
    private static final String KEY_ACTIVE = "rideActive";
    private static final String COLS = "id, start_ms, end_ms, odo_start, odo_end, max_kmh, moving_ms, speed_km, avg_kml, engine";

    private RideStore() {}

    /** One page of the rides that started in [from, to), newest first. */
    public static List<RideTracker.Ride> page(Context c, long from, long to, int limit, int offset) {
        return query(c, "SELECT " + COLS + " FROM " + RideDb.T + " WHERE start_ms >= " + from + " AND start_ms < " + to
                + " ORDER BY start_ms DESC, id DESC LIMIT " + limit + " OFFSET " + offset);
    }

    /** How many rides started in [from, to), and their distance together. */
    public static final class Totals {
        public int rides;
        public double km;
    }

    public static Totals totals(Context c, long from, long to) {
        Totals t = new Totals();
        try (Cursor k = RideDb.get(c).rawQuery("SELECT COUNT(*), SUM(km) FROM " + RideDb.T
                + " WHERE start_ms >= " + from + " AND start_ms < " + to, null)) {
            if (k.moveToFirst()) { t.rides = k.getInt(0); t.km = k.getDouble(1); }
        }
        return t;
    }

    /** Rides that started in [from, to), oldest first. */
    public static List<RideTracker.Ride> between(Context c, long from, long to) {
        return query(c, "SELECT " + COLS + " FROM " + RideDb.T + " WHERE start_ms >= " + from
                + " AND start_ms < " + to + " ORDER BY start_ms");
    }

    /** When the first ride started, or 0 with no rides. */
    public static long firstStart(Context c) {
        try (Cursor k = RideDb.get(c).rawQuery("SELECT MIN(start_ms) FROM " + RideDb.T, null)) {
            return k.moveToFirst() && !k.isNull(0) ? k.getLong(0) : 0;
        }
    }

    /** Odometer at the end of the last ride (0.1 km), or 0. */
    public static int lastOdo(Context c) {
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
                r.id = k.getLong(0);
                r.start = k.getLong(1);
                r.end = k.getLong(2);
                r.odoStart = k.getInt(3);
                r.odoEnd = k.getInt(4);
                r.maxKmh = k.getInt(5);
                r.movingMs = k.getLong(6);
                r.speedKm = k.getDouble(7);
                r.avgKmL = k.getInt(8);
                if (!k.isNull(9)) {
                    try { readEngine(r, new JSONObject(k.getString(9))); } catch (JSONException ignored) { }
                }
                out.add(r);
            }
        }
        return out;
    }

    /** A saved ride's speed over time (left out of the lists, which do not need it), or null. */
    public static SpeedTrace trace(Context c, long id) {
        try (Cursor k = RideDb.get(c).rawQuery("SELECT trace FROM " + RideDb.T + " WHERE id = " + id, null)) {
            return k.moveToFirst() && !k.isNull(0) ? SpeedTrace.decode(k.getString(0)) : null;
        }
    }

    public static void add(Context c, RideTracker.Ride r) {
        RideDb.get(c).insert(RideDb.T, null, RideDb.values(r));
        BikeService.prefs(c).edit().remove(KEY_ACTIVE).apply();
    }

    public static void delete(Context c, long id) {
        int n = RideDb.get(c).delete(RideDb.T, "id = ?", new String[]{String.valueOf(id)});
        BikeService.log("RIDE deleted (" + n + ")");
    }

    public static void clear(Context c) {
        int n = RideDb.get(c).delete(RideDb.T, null, null);
        BikeService.log("RIDE history cleared (" + n + " rides deleted)");
    }

    /** The ride in progress, so it survives the app being closed mid-ride. null clears it. */
    public static void saveActive(Context c, RideTracker.Ride r) {
        if (r == null) { BikeService.prefs(c).edit().remove(KEY_ACTIVE).apply(); return; }
        BikeService.prefs(c).edit().putString(KEY_ACTIVE, toJson(r).toString()).apply();
    }

    public static RideTracker.Ride active(Context c) {
        String s = BikeService.prefs(c).getString(KEY_ACTIVE, null);
        if (s == null) return null;
        try { return fromJson(new JSONObject(s)); } catch (JSONException e) { return null; }
    }

    /**
     * When the bike link is not running, a saved ride in progress whose last movement is long past
     * is really over: move it to the history (or drop it if too short).
     */
    public static void finishStale(Context c) {
        RideTracker.Ride r = active(c);
        if (r == null || System.currentTimeMillis() - r.end < RideTracker.END_SILENCE_MS) return;
        if (r.km() >= RideTracker.MIN_KM) add(c, r); else saveActive(c, null);
    }

    private static JSONObject toJson(RideTracker.Ride r) {
        JSONObject o = new JSONObject();
        try {
            o.put("start", r.start).put("end", r.end).put("odoStart", r.odoStart).put("odoEnd", r.odoEnd)
                    .put("max", r.maxKmh).put("moving", r.movingMs).put("speedKm", r.speedKm);
            if (r.avgKmL > 0) o.put("kml", r.avgKmL);
            if (r.hasEngine()) o.put("engine", engineJson(r));
            if (r.trace != null) o.put("trace", r.trace.encode(0));
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
        r.avgKmL = o.optInt("kml", 0);
        JSONObject e = o.optJSONObject("engine");
        if (e != null) readEngine(r, e);
        r.trace = SpeedTrace.decode(o.optString("trace", null));
        return r;
    }

    /** A ride's engine data as JSON (the rides table's engine column, and the ride in progress). */
    static JSONObject engineJson(RideTracker.Ride r) {
        JSONObject o = new JSONObject();
        try {
            o.put("engine", r.engineMs).put("idle", r.idleMs).put("eco", r.ecoMs).put("power", r.powerMs)
                    .put("ecoKm", r.ecoKm).put("powerKm", r.powerKm).put("shifts", r.shifts)
                    .put("rpmMoving", r.rpmMovingMs).put("rpmSum", r.rpmSum).put("maxRpm", r.maxRpm)
                    .put("rpmAlerts", r.rpmAlerts).put("throttleMs", r.throttleMs).put("hard", r.hardMs)
                    .put("throttleSum", r.throttleSum)
                    .put("gears", longs(r.gearMs)).put("rpmBands", longs(r.rpmBandMs));
        } catch (JSONException ignored) { }
        return o;
    }

    static void readEngine(RideTracker.Ride r, JSONObject o) {
        r.engineMs = o.optLong("engine"); r.idleMs = o.optLong("idle");
        r.ecoMs = o.optLong("eco"); r.powerMs = o.optLong("power");
        r.ecoKm = o.optDouble("ecoKm", 0); r.powerKm = o.optDouble("powerKm", 0);
        r.shifts = o.optInt("shifts"); r.rpmMovingMs = o.optLong("rpmMoving");
        r.rpmSum = o.optDouble("rpmSum", 0); r.maxRpm = o.optInt("maxRpm");
        r.rpmAlerts = o.optInt("rpmAlerts"); r.throttleMs = o.optLong("throttleMs");
        r.hardMs = o.optLong("hard"); r.throttleSum = o.optDouble("throttleSum", 0);
        readLongs(o.optJSONArray("gears"), r.gearMs);
        readLongs(o.optJSONArray("rpmBands"), r.rpmBandMs);
    }

    private static org.json.JSONArray longs(long[] a) {
        org.json.JSONArray j = new org.json.JSONArray();
        for (long v : a) j.put(v);
        return j;
    }

    private static void readLongs(org.json.JSONArray j, long[] into) {
        if (j == null) return;
        for (int i = 0; i < into.length && i < j.length(); i++) into[i] = j.optLong(i);
    }
}
