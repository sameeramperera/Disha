package com.disha.backup;

import com.disha.MainActivity;
import com.disha.bike.BikeService;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Writes the backup file the user chose: now, or every day at a set time. A system alarm like
 * ServiceReminder (inexact, so it can come a little late); a backup missed while the phone was off
 * is made after the restart or when the app is opened.
 */
public class AutoBackup extends BroadcastReceiver {
    private static final String ACTION = "com.disha.AUTO_BACKUP";
    private static final int NOTIFY_ID = 4344;
    public static final String ON = "backupOn", MINUTE = "backupMinute", URI = "backupUri", NAME = "backupName",
            WHERE = "backupWhere", AT = "backupAt", SINCE = "backupSince", ERROR = "backupError";
    public static final int DEFAULT_MINUTE = 23 * 60;

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!ACTION.equals(a) && !Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        BikeService.initLog(c);
        final Context app = c.getApplicationContext();
        final PendingResult done = goAsync();
        new Thread(() -> {
            try { check(app); } finally { done.finish(); }
        }, "backup").start();
    }

    /** The backup time today if it has come, else yesterday's. */
    static long lastDue(long now, int minute) {
        Calendar k = at(now, minute);
        if (k.getTimeInMillis() > now) { k.add(Calendar.DAY_OF_MONTH, -1); setTime(k, minute); }
        return k.getTimeInMillis();
    }

    /** The next backup time after now. */
    static long nextDue(long now, int minute) {
        Calendar k = at(now, minute);
        if (k.getTimeInMillis() <= now) { k.add(Calendar.DAY_OF_MONTH, 1); setTime(k, minute); }
        return k.getTimeInMillis();
    }

    private static Calendar at(long day, int minute) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(day);
        setTime(k, minute);
        return k;
    }

    private static void setTime(Calendar k, int minute) {
        k.set(Calendar.HOUR_OF_DAY, minute / 60);
        k.set(Calendar.MINUTE, minute % 60);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
    }

    /** Sets (or clears) the alarm, and makes the backup if one is due. Writes a file: not on the main thread. */
    public static void check(Context c) {
        SharedPreferences p = BikeService.prefs(c);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 8, new Intent(c, AutoBackup.class).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (!p.getBoolean(ON, false) || p.getString(URI, "").isEmpty()) { am.cancel(pi); return; }
        int minute = p.getInt(MINUTE, DEFAULT_MINUTE);
        long now = System.currentTimeMillis();
        long next = nextDue(now, minute);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        synchronized (AutoBackup.class) {
            long last = p.getLong(AT, 0);
            if (last > now) last = 0;    // the clock was set back
            if (Math.max(last, p.getLong(SINCE, 0)) >= lastDue(now, minute)) return;
            boolean failedBefore = !p.getString(ERROR, "").isEmpty();
            // An empty app (new install, or all deleted) must not replace a good backup.
            if (Backup.counts(c).none()) return;
            String error = run(c);
            BikeService.log("BACKUP next at " + new SimpleDateFormat("EEE d MMM HH:mm", Locale.ENGLISH).format(new Date(next)));
            if (error != null && !failedBefore) notify(c, error);
        }
    }

    /** Writes the backup now. Null when it worked, else what went wrong, for the user. Not on the main thread. */
    public static synchronized String run(Context c) {
        SharedPreferences p = BikeService.prefs(c);
        String uri = p.getString(URI, "");
        if (uri.isEmpty()) return "Choose a backup file first";
        String error = null;
        try {
            if (Backup.counts(c).none()) return "Nothing to back up yet";
            byte[] data = Backup.make(c);   // all of it first, so a failure here leaves the old file alone
            try (OutputStream o = c.getContentResolver().openOutputStream(Uri.parse(uri), "wt")) {
                if (o == null) throw new java.io.FileNotFoundException();
                o.write(data);
            }
            BikeService.log("BACKUP saved (" + data.length / 1024 + " KB)");
        } catch (java.io.FileNotFoundException | IllegalArgumentException e) {
            error = "Backup file not found. Choose it again.";
            BikeService.log("BACKUP failed: file not found");
        } catch (SecurityException e) {
            error = "No access to the backup file. Choose it again.";
            BikeService.log("BACKUP failed: no access");
        } catch (Exception e) {
            error = "Could not write the backup file";
            BikeService.log("BACKUP failed: " + e.getClass().getSimpleName());
        }
        SharedPreferences.Editor e = p.edit();
        if (error == null) {
            e.putLong(AT, System.currentTimeMillis()).remove(ERROR);
            c.getSystemService(NotificationManager.class).cancel(NOTIFY_ID);
        } else e.putString(ERROR, error);
        e.apply();
        return error;
    }

    private static void notify(Context c, String error) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("backup", "Backup problems",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent pi = PendingIntent.getActivity(c, 4, new Intent(c, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(NOTIFY_ID, new Notification.Builder(c, "backup")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Backup failed")
                .setContentText(error)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }
}
