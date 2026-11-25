package com.disha.backup;

import com.disha.bike.BikeService;
import com.disha.rides.RideDb;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Everything worth keeping as one JSON file: the rides, fuel and service tables row by row, and the
 * settings. Restoring replaces the tables in one transaction, so a bad file changes nothing.
 */
public final class Backup {
    private Backup() {}

    private static final int FORMAT = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final String[] TABLES = {RideDb.T, RideDb.FUEL, RideDb.SERVICE};
    private static final String[] SWITCHES = {"mirrorNav", "rideMode", "speedAlert", "rpmAlert", "callAlert",
            "fuelAlert", "devOptions", "saveLogs"};
    /** Key, lowest, highest (as the steppers in Settings). */
    private static final Object[][] NUMBERS = {{"speedLimit", 30, 150}, {"rpmLimit", 3000, 9000}, {"fuelLevel", 1, 4}};

    /** How many rides, fill-ups and services. */
    public static final class Counts {
        public int rides, fuel, services;
        public boolean none() { return rides + fuel + services == 0; }
        /** "34 rides, 12 fill-ups and 3 services" */
        @Override public String toString() {
            return rides + (rides == 1 ? " ride, " : " rides, ") + fuel + (fuel == 1 ? " fill-up and " : " fill-ups and ")
                    + services + (services == 1 ? " service" : " services");
        }
    }

    /** A backup that was read and looks whole. */
    public static final class File {
        private JSONObject root;
        public long time;               // when it was made, 0 = not known
        public final Counts counts = new Counts();
    }

    public static Counts counts(Context c) {
        Counts n = new Counts();
        SQLiteDatabase db = RideDb.get(c);
        n.rides = count(db, RideDb.T);
        n.fuel = count(db, RideDb.FUEL);
        n.services = count(db, RideDb.SERVICE);
        return n;
    }

    private static int count(SQLiteDatabase db, String table) {
        try (Cursor k = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return k.moveToFirst() ? k.getInt(0) : 0;
        }
    }

    // ---------- writing ----------

    static byte[] make(Context c) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Disha").put("format", FORMAT).put("time", System.currentTimeMillis());
        SQLiteDatabase db = RideDb.get(c);
        db.beginTransaction();      // one moment in time for all three tables
        try {
            for (String t : TABLES) root.put(t, rows(db, t));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        root.put("settings", settings(c));
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JSONArray rows(SQLiteDatabase db, String table) throws JSONException {
        JSONArray out = new JSONArray();
        try (Cursor k = db.rawQuery("SELECT * FROM " + table + " ORDER BY id", null)) {
            while (k.moveToNext()) {
                JSONObject o = new JSONObject();
                for (int i = 0; i < k.getColumnCount(); i++) {
                    String name = k.getColumnName(i);
                    if (name.equals("id")) continue;
                    switch (k.getType(i)) {
                        case Cursor.FIELD_TYPE_INTEGER: o.put(name, k.getLong(i)); break;
                        case Cursor.FIELD_TYPE_FLOAT: o.put(name, k.getDouble(i)); break;
                        case Cursor.FIELD_TYPE_STRING: o.put(name, k.getString(i)); break;
                        default: break;     // null: left out
                    }
                }
                out.put(o);
            }
        }
        return out;
    }

    private static JSONObject settings(Context c) throws JSONException {
        SharedPreferences p = BikeService.prefs(c);
        JSONObject o = new JSONObject();
        for (String k : SWITCHES) if (p.contains(k)) o.put(k, p.getBoolean(k, true));
        for (Object[] n : NUMBERS) if (p.contains((String) n[0])) o.put((String) n[0], p.getInt((String) n[0], 0));
        for (String k : new String[]{"theme", "bikeLabel", "mac", "bikeName"}) {
            String v = p.getString(k, "");
            if (!v.isEmpty()) o.put(k, v);
        }
        return o;
    }

    // ---------- reading ----------

    /** Reads and checks a backup. The exception's message is for the user. */
    public static File read(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        for (int n; (n = in.read(b)) > 0; ) {
            buf.write(b, 0, n);
            if (buf.size() > MAX_BYTES) throw new IOException("This file is too big to be a Disha backup");
        }
        File f = new File();
        try {
            f.root = new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("This is not a Disha backup");
        }
        if (!"Disha".equals(f.root.optString("app"))) throw new IOException("This is not a Disha backup");
        if (f.root.optInt("format") > FORMAT) throw new IOException("This backup is from a newer version of Disha. Update the app first.");
        for (String t : TABLES) {
            JSONArray a = f.root.optJSONArray(t);
            if (a == null) throw new IOException("This backup is damaged");
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) == null) throw new IOException("This backup is damaged");
        }
        f.time = f.root.optLong("time");
        f.counts.rides = f.root.optJSONArray(RideDb.T).length();
        f.counts.fuel = f.root.optJSONArray(RideDb.FUEL).length();
        f.counts.services = f.root.optJSONArray(RideDb.SERVICE).length();
        return f;
    }

    /** Replaces the rides, fill-ups and services with the backup's, and takes over its settings. */
    public static void restore(Context c, File f) {
        SQLiteDatabase db = RideDb.get(c);
        db.beginTransaction();
        try {
            for (String t : TABLES) {
                Set<String> known = columns(db, t);
                db.delete(t, null, null);
                JSONArray a = f.root.optJSONArray(t);
                for (int i = 0; i < a.length(); i++) db.insertOrThrow(t, null, values(a.optJSONObject(i), known));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        restoreSettings(c, f.root.optJSONObject("settings"));
        BikeService.log("BACKUP restored (" + f.counts + ")");
    }

    private static Set<String> columns(SQLiteDatabase db, String table) {
        Set<String> out = new HashSet<>();
        try (Cursor k = db.rawQuery("SELECT * FROM " + table + " LIMIT 0", null)) {
            for (String n : k.getColumnNames()) if (!n.equals("id")) out.add(n);
        }
        return out;
    }

    private static ContentValues values(JSONObject o, Set<String> known) {
        ContentValues v = new ContentValues();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!known.contains(k)) continue;
            Object x = o.opt(k);
            if (x instanceof Integer || x instanceof Long) v.put(k, ((Number) x).longValue());
            else if (x instanceof Number) v.put(k, ((Number) x).doubleValue());
            else if (x instanceof String) v.put(k, (String) x);
        }
        return v;
    }

    private static void restoreSettings(Context c, JSONObject o) {
        if (o == null) return;
        SharedPreferences p = BikeService.prefs(c);
        SharedPreferences.Editor e = p.edit();
        for (String k : SWITCHES) if (o.opt(k) instanceof Boolean) e.putBoolean(k, o.optBoolean(k));
        for (Object[] n : NUMBERS) if (o.opt((String) n[0]) instanceof Integer)
            e.putInt((String) n[0], Math.max((Integer) n[1], Math.min((Integer) n[2], o.optInt((String) n[0]))));
        String theme = o.optString("theme"), label = o.optString("bikeLabel"), mac = o.optString("mac");
        if (theme.matches("dark|light|system|auto")) e.putString("theme", theme);
        if (!label.isEmpty()) e.putString("bikeLabel", label.substring(0, Math.min(24, label.length())));
        // A bike already chosen on this phone stays.
        if (p.getString("mac", "").isEmpty() && mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
            e.putString("mac", mac);
            e.putString("bikeName", o.optString("bikeName"));
        }
        e.apply();
    }
}
