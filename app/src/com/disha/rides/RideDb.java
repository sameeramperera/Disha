package com.disha.rides;

import com.disha.bike.BikeService;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * The app's database, rides.db (named before it held more than rides):
 * - rides: one row per finished ride, kept for good (no limit). Before 0.2.0 rides lived in
 *   SharedPreferences as a JSON list of the last 100; when the database is first created those
 *   rides are copied in and the old list is removed.
 * - fuel and service: fill-ups and services typed in on the Records tab (Records). Clearing the
 *   ride history only empties the rides table.
 */
public final class RideDb extends SQLiteOpenHelper {
    static final String T = "rides";
    public static final String FUEL = "fuel", SERVICE = "service";
    private static final int VERSION = 3;
    /** The SharedPreferences key the old JSON list was kept under. */
    static final String OLD_KEY = "rides";

    private static RideDb inst;
    private final Context app;
    private boolean imported;

    private RideDb(Context app) {
        super(app, "rides.db", null, VERSION);
        this.app = app;
    }

    public static synchronized SQLiteDatabase get(Context c) {
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
                + "avg_kml INTEGER NOT NULL DEFAULT 0,"   // cluster AVG MILEAGE at the end, 0 = unknown
                + "engine TEXT)");                        // engine data as JSON (RideStore), null = none
        db.execSQL("CREATE INDEX rides_start ON " + T + " (start_ms)");
        createRecords(db);
        importOld(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        // Step by step from the installed version.
        if (from < 2) db.execSQL("ALTER TABLE " + T + " ADD COLUMN engine TEXT");   // 0.3.x: engine data
        if (from < 3) createRecords(db);                                               // 0.6.0: Records tab
    }

    /** Fuel fill-ups and services (Records). */
    private static void createRecords(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + FUEL + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "time_ms INTEGER NOT NULL,"          // date, plus the time if has_time
                + "has_time INTEGER NOT NULL DEFAULT 0,"
                + "shed TEXT NOT NULL DEFAULT '',"     // filling station: CPC, LIOC, ...
                + "litres REAL NOT NULL DEFAULT 0,"    // 0 = not entered
                + "price REAL NOT NULL DEFAULT 0,"     // rupees paid, 0 = not entered
                + "paid_by TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX fuel_time ON " + FUEL + " (time_ms)");
        db.execSQL("CREATE TABLE " + SERVICE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "time_ms INTEGER NOT NULL,"          // the day of the service
                + "odo_km INTEGER NOT NULL DEFAULT 0," // odometer, 0 = not entered
                + "workshop TEXT NOT NULL DEFAULT '',"
                + "cost REAL NOT NULL DEFAULT 0,"
                + "paid_by TEXT NOT NULL DEFAULT '',"
                + "next_ms INTEGER NOT NULL DEFAULT 0,"   // next service day, 0 = none
                + "notes TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX service_time ON " + SERVICE + " (time_ms)");
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
