package com.disha;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * The ride history: one row per finished ride, kept for good (no limit), in rides.db.
 * Before 0.2.0 rides lived in SharedPreferences as a JSON list of the last 100; when the
 * database is first created those rides are copied in and the old list is removed.
 */
final class RideDb extends SQLiteOpenHelper {
    static final String T = "rides";
    private static final int VERSION = 2;
    /** The SharedPreferences key the old JSON list was kept under. */
    static final String OLD_KEY = "rides";

    private static RideDb inst;
    private final Context app;
    private boolean imported;

    private RideDb(Context app) {
        super(app, "rides.db", null, VERSION);
        this.app = app;
    }

    static synchronized SQLiteDatabase get(Context c) {
        if (inst == null) inst = new RideDb(c.getApplicationContext());
        SQLiteDatabase db = inst.getWritableDatabase();
        if (inst.imported) {
            // Only now: onCreate's transaction (with the copied rides) has been committed.
            inst.imported = false;
            BikeService.prefs(c).edit().remove(OLD_KEY).apply();
        }
        return db;
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "start_ms INTEGER NOT NULL,"     // first moment moving
                + "end_ms INTEGER NOT NULL,"       // last moment moving
                + "odo_start INTEGER NOT NULL,"    // 0.1 km
                + "odo_end INTEGER NOT NULL,"
                + "max_kmh INTEGER NOT NULL,"
                + "moving_ms INTEGER NOT NULL,"
                + "speed_km REAL NOT NULL,"        // distance from integrating speed
                + "km REAL NOT NULL,"              // the ride's distance (Ride.km()), stored for sums
                + "engine TEXT)");                        // engine data as JSON (RideStore), null = none
        db.execSQL("CREATE INDEX rides_start ON " + T + " (start_ms)");
        importOld(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        // Step by step from the installed version.
        if (from < 2) db.execSQL("ALTER TABLE " + T + " ADD COLUMN engine TEXT");   // 0.3.x: engine data
    }

    private void importOld(SQLiteDatabase db) {
        SharedPreferences p = BikeService.prefs(app);
        String json = p.getString(OLD_KEY, null);
        if (json == null) return;
        int n = 0;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                db.insert(T, null, values(RideStore.fromJson(a.getJSONObject(i))));
                n++;
            }
        } catch (JSONException e) {
            BikeService.log("Old ride history unreadable, not copied: " + e);
        }
        imported = true;
        BikeService.log("RIDE history moved to the database (" + n + " rides)");
    }

    static ContentValues values(RideTracker.Ride r) {
        ContentValues v = new ContentValues();
        v.put("start_ms", r.start);
        v.put("end_ms", r.end);
        v.put("odo_start", r.odoStart);
        v.put("odo_end", r.odoEnd);
        v.put("max_kmh", r.maxKmh);
        v.put("moving_ms", r.movingMs);
        v.put("speed_km", r.speedKm);
        v.put("km", r.km());
        v.put("avg_kml", Math.max(0, r.avgKmL));
        if (r.hasEngine()) v.put("engine", RideStore.engineJson(r).toString());
        return v;
    }
}
