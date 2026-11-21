package com.disha.records;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.disha.rides.RideDb;

import java.util.ArrayList;
import java.util.List;

/** Reading and writing fuel fill-ups and services: the fuel and service tables in rides.db (RideDb). */
public final class Records {
    private Records() {}

    public static final class Fuel {
        public long id;             // 0 = not saved yet
        public long timeMs;
        public boolean hasTime;
        public String shed = "";
        public double litres;       // 0 = not entered
        public double price;        // rupees, 0 = not entered
        public String paidBy = "";
    }

    public static final class Service {
        public long id;
        public long timeMs;
        public int odoKm;           // 0 = not entered
        public String workshop = "";
        public double cost;
        public String paidBy = "";
        public long nextMs;         // next service day, 0 = none
        public String notes = "";
    }

    // ---------- fuel ----------

    /** Newest first. */
    public static List<Fuel> fuel(Context c) { return fuel(c, 0, Long.MAX_VALUE, true); }

    /** Fill-ups in [from, to). */
    public static List<Fuel> fuel(Context c, long from, long to, boolean newestFirst) {
        List<Fuel> out = new ArrayList<>();
        try (Cursor k = RideDb.get(c).rawQuery("SELECT id, time_ms, has_time, shed, litres, price, paid_by FROM "
                + RideDb.FUEL + " WHERE time_ms >= ? AND time_ms < ? ORDER BY time_ms " + (newestFirst ? "DESC" : "ASC")
                + ", id " + (newestFirst ? "DESC" : "ASC"), new String[]{String.valueOf(from), String.valueOf(to)})) {
            while (k.moveToNext()) {
                Fuel f = new Fuel();
                f.id = k.getLong(0);
                f.timeMs = k.getLong(1);
                f.hasTime = k.getInt(2) != 0;
                f.shed = k.getString(3);
                f.litres = k.getDouble(4);
                f.price = k.getDouble(5);
                f.paidBy = k.getString(6);
                out.add(f);
            }
        }
        return out;
    }

    /** Inserts (id 0) or updates the fill-up, and sets its id. */
    public static void save(Context c, Fuel f) {
        ContentValues v = new ContentValues();
        v.put("time_ms", f.timeMs);
        v.put("has_time", f.hasTime ? 1 : 0);
        v.put("shed", f.shed);
        v.put("litres", f.litres);
        v.put("price", f.price);
        v.put("paid_by", f.paidBy);
        SQLiteDatabase db = RideDb.get(c);
        if (f.id == 0) f.id = db.insert(RideDb.FUEL, null, v);
        else db.update(RideDb.FUEL, v, "id = ?", new String[]{String.valueOf(f.id)});
    }

    public static void deleteFuel(Context c, long id) {
        RideDb.get(c).delete(RideDb.FUEL, "id = ?", new String[]{String.valueOf(id)});
    }

    // ---------- services ----------

    /** Newest first. */
    public static List<Service> services(Context c) { return services(c, 0, Long.MAX_VALUE); }

    /** Services in [from, to), newest first. */
    public static List<Service> services(Context c, long from, long to) {
        List<Service> out = new ArrayList<>();
        try (Cursor k = RideDb.get(c).rawQuery("SELECT id, time_ms, odo_km, workshop, cost, paid_by, next_ms, notes FROM "
                + RideDb.SERVICE + " WHERE time_ms >= ? AND time_ms < ? ORDER BY time_ms DESC, id DESC",
                new String[]{String.valueOf(from), String.valueOf(to)})) {
            while (k.moveToNext()) {
                Service s = new Service();
                s.id = k.getLong(0);
                s.timeMs = k.getLong(1);
                s.odoKm = k.getInt(2);
                s.workshop = k.getString(3);
                s.cost = k.getDouble(4);
                s.paidBy = k.getString(5);
                s.nextMs = k.getLong(6);
                s.notes = k.getString(7);
                out.add(s);
            }
        }
        return out;
    }

    public static void save(Context c, Service s) {
        ContentValues v = new ContentValues();
        v.put("time_ms", s.timeMs);
        v.put("odo_km", s.odoKm);
        v.put("workshop", s.workshop);
        v.put("cost", s.cost);
        v.put("paid_by", s.paidBy);
        v.put("next_ms", s.nextMs);
        v.put("notes", s.notes);
        SQLiteDatabase db = RideDb.get(c);
        if (s.id == 0) s.id = db.insert(RideDb.SERVICE, null, v);
        else db.update(RideDb.SERVICE, v, "id = ?", new String[]{String.valueOf(s.id)});
    }

    public static void deleteService(Context c, long id) {
        RideDb.get(c).delete(RideDb.SERVICE, "id = ?", new String[]{String.valueOf(id)});
    }

    /** Values typed in a column before (sheds, ways to pay), most used first, for the dropdowns. */
    public static List<String> used(Context c, String table, String column) {
        List<String> out = new ArrayList<>();
        try (Cursor k = RideDb.get(c).rawQuery("SELECT " + column + " FROM " + table + " WHERE " + column
                + " != '' GROUP BY " + column + " ORDER BY COUNT(*) DESC", null)) {
            while (k.moveToNext()) out.add(k.getString(0));
        }
        return out;
    }

    /** Ways to pay used in fill-ups and services. */
    public static List<String> paidBy(Context c) {
        List<String> out = used(c, RideDb.FUEL, "paid_by");
        for (String v : used(c, RideDb.SERVICE, "paid_by")) if (!out.contains(v)) out.add(v);
        return out;
    }

    /**
     * The next service the rider is waiting for: the next date written on the most recent service.
     * Older services' next dates are out of date once a newer service is recorded. Null if none.
     */
    public static Service latestService(Context c) {
        List<Service> all = services(c);
        return all.isEmpty() ? null : all.get(0);
    }
}
