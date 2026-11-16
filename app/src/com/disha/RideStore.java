package com.disha;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saves finished rides (newest first, last 100) and the ride in progress, in SharedPreferences. */
final class RideStore {
    private static final String KEY_RIDES = "rides", KEY_ACTIVE = "rideActive";
    static final int KEEP = 100;

    private RideStore() {}

    private static SharedPreferences p(Context c) { return BikeService.prefs(c); }

    static List<RideTracker.Ride> all(Context c) {
        List<RideTracker.Ride> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString(KEY_RIDES, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (JSONException e) {
            BikeService.log("Ride history unreadable: " + e);
        }
        return out;
    }

    static void add(Context c, RideTracker.Ride r) {
        List<RideTracker.Ride> rides = all(c);
        rides.add(0, r);
        JSONArray a = new JSONArray();
        for (int i = 0; i < rides.size() && i < KEEP; i++) a.put(toJson(rides.get(i)));
        p(c).edit().putString(KEY_RIDES, a.toString()).remove(KEY_ACTIVE).apply();
    }

    static void clear(Context c) {
        p(c).edit().remove(KEY_RIDES).apply();
    }

    /** The ride in progress, so it survives the app being closed mid-ride. null clears it. */
    static void saveActive(Context c, RideTracker.Ride r) {
        if (r == null) { p(c).edit().remove(KEY_ACTIVE).apply(); return; }
        p(c).edit().putString(KEY_ACTIVE, toJson(r).toString()).apply();
    }

    static RideTracker.Ride active(Context c) {
        String s = p(c).getString(KEY_ACTIVE, null);
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

    private static RideTracker.Ride fromJson(JSONObject o) {
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
