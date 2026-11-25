package com.disha.backup;

import com.disha.records.Records;
import com.disha.rides.RideStore;
import com.disha.rides.RideTracker;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The rides, fill-ups or services as a CSV table for Excel or Sheets. */
public final class CsvExport {
    private CsvExport() {}

    public static final int RIDES = 0, FUEL = 1, SERVICES = 2;
    private static final String[] NAMES = {"rides", "fill-ups", "services"};

    /** "Disha rides 2026-11-25.csv" */
    public static String fileName(int what) {
        return "Disha " + NAMES[what] + " " + day().format(new Date()) + ".csv";
    }

    public static byte[] make(Context c, int what) {
        StringBuilder b = new StringBuilder("﻿");    // so Excel reads it as UTF-8
        SimpleDateFormat day = day(), hm = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        if (what == RIDES) {
            line(b, "Date", "Start", "End", "Distance (km)", "Riding time (min)", "Moving (min)", "Avg speed (km/h)",
                    "Top speed (km/h)", "Mileage (km/L)", "Odometer start (km)", "Odometer end (km)", "ECO (min)",
                    "POWER (min)", "Idling (min)", "Avg RPM", "Peak RPM", "Gear shifts");
            for (RideTracker.Ride r : RideStore.between(c, 0, Long.MAX_VALUE)) {
                boolean e = r.hasEngine();
                line(b, day.format(new Date(r.start)), hm.format(new Date(r.start)), hm.format(new Date(r.end)),
                        one(r.km()), min(r.durationMs()), min(r.movingMs), one(r.avgKmh()), String.valueOf(r.maxKmh),
                        num(r.avgKmL), one(r.odoStart / 10.0), one(r.odoEnd / 10.0), e ? min(r.ecoMs) : "",
                        e ? min(r.powerMs) : "", e ? min(r.idleMs) : "", e ? num(r.avgRpm()) : "", e ? num(r.maxRpm) : "",
                        e ? String.valueOf(r.shifts) : "");
            }
        } else if (what == FUEL) {
            line(b, "Date", "Time", "Shed", "Litres", "Price (Rs.)", "Paid by");
            List<Records.Fuel> all = Records.fuel(c, 0, Long.MAX_VALUE, false);
            for (Records.Fuel f : all) line(b, day.format(new Date(f.timeMs)), f.hasTime ? hm.format(new Date(f.timeMs)) : "",
                    f.shed, f.litres > 0 ? two(f.litres) : "", f.price > 0 ? two(f.price) : "", f.paidBy);
        } else {
            line(b, "Date", "Odometer (km)", "Workshop", "Cost (Rs.)", "Paid by", "Next service", "Notes");
            List<Records.Service> all = Records.services(c);
            java.util.Collections.reverse(all);     // oldest first, like the others
            for (Records.Service s : all) line(b, day.format(new Date(s.timeMs)), num(s.odoKm), s.workshop,
                    s.cost > 0 ? two(s.cost) : "", s.paidBy, s.nextMs > 0 ? day.format(new Date(s.nextMs)) : "", s.notes);
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static SimpleDateFormat day() { return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH); }
    private static String one(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private static String two(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String min(long ms) { return one(ms / 60_000.0); }
    /** Empty for 0 (not known). */
    private static String num(int v) { return v > 0 ? String.valueOf(v) : ""; }

    static void line(StringBuilder b, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            String s = cells[i];
            boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
            if (quote) b.append('"').append(s.replace("\"", "\"\"")).append('"');
            else b.append(s);
        }
        b.append("\r\n");
    }
}
