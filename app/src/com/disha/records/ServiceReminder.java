package com.disha.records;

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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * "Service tomorrow" at 9 AM the day before the next service date of the most recent service.
 *
 * Uses AlarmManager (a system alarm), not a Handler delay: the Honor holds back app timers while
 * the screen is off. The alarm is inexact, so it may come a little after 9. Alarms are cleared when
 * the phone restarts, so this also runs after a restart or update, and whenever the app opens or a
 * service is saved; a reminder that was missed while the phone was off is shown then.
 */
public class ServiceReminder extends BroadcastReceiver {
    public static final String EXTRA_OPEN = "open", OPEN_SERVICE = "service";
    private static final String ACTION = "com.disha.SERVICE_REMINDER";
    private static final String KEY_DONE = "serviceReminded";
    static final int HOUR = 9;
    private static final int NOTIFY_ID = 4343;

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!ACTION.equals(a) && !Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        BikeService.initLog(c);
        check(c);
    }

    /** 9 AM on the day before the service day (nextMs is that day's midnight, or any time in it). */
    static long reminderAt(long nextMs) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(nextMs);
        k.set(Calendar.HOUR_OF_DAY, 0);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        k.add(Calendar.DAY_OF_MONTH, -1);
        k.set(Calendar.HOUR_OF_DAY, HOUR);
        return k.getTimeInMillis();
    }

    /** Midnight at the start of the service day. */
    static long dayStart(long nextMs) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(nextMs);
        k.set(Calendar.HOUR_OF_DAY, 0);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        return k.getTimeInMillis();
    }

    /** 0 = nothing to do, 1 = remind now, 2 = set the alarm for reminderAt. */
    static int decide(long nextMs, long now, boolean alreadyDone) {
        if (nextMs <= 0 || alreadyDone) return 0;
        if (now >= dayStart(nextMs)) return 0;       // the service day has come: too late for "tomorrow"
        return now >= reminderAt(nextMs) ? 1 : 2;
    }

    /** Shows the reminder if it is due, else sets the alarm for it (or clears it if there is none). */
    public static void check(Context c) {
        Records.Service s;
        try {
            s = Records.latestService(c);
        } catch (Exception e) {
            BikeService.log("SERVICE reminder: could not read services: " + e);
            return;
        }
        long next = s == null ? 0 : s.nextMs;
        String key = s == null ? "" : s.id + ":" + dayStart(next);
        SharedPreferences p = BikeService.prefs(c);
        int what = decide(next, System.currentTimeMillis(), key.equals(p.getString(KEY_DONE, "")));
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7,
                new Intent(c, ServiceReminder.class).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (what == 2) {
            long at = reminderAt(next);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            BikeService.log("SERVICE reminder set for " + new SimpleDateFormat("EEE d MMM HH:mm", Locale.ENGLISH).format(new Date(at)));
            return;
        }
        am.cancel(pi);
        if (what == 1) {
            p.edit().putString(KEY_DONE, key).apply();
            notify(c, next);
        }
    }

    private static void notify(Context c, long next) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("service", "Service reminders",
                NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, MainActivity.class).putExtra(EXTRA_OPEN, OPEN_SERVICE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 3, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String day = new SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(new Date(next));
        nm.notify(NOTIFY_ID, new Notification.Builder(c, "service")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Service tomorrow")
                .setContentText("Your bike is due for its service tomorrow, " + day + ".")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
        BikeService.log("SERVICE reminder shown for " + day);
    }
}
