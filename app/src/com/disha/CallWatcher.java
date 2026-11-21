package com.disha;

import android.app.Notification;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

/**
 * Spots incoming-call notifications (the phone app, WhatsApp and other calling apps all post one
 * with the "call" category) and shows the caller on the cluster while the phone rings.
 */
final class CallWatcher {
    private CallWatcher() {}

    /** Notification.CallStyle extras (API 31): the call type, 1 = incoming, 2 = ongoing, 3 = screening. */
    private static final String EXTRA_CALL_TYPE = "android.callType", EXTRA_CALL_PERSON = "android.callPerson";
    private static final int CALL_INCOMING = 1;

    /** The notification showing the call that is ringing now. */
    private static String ringingKey;
    private static String lastSeen = "";

    /** Settings > Calls on the cluster (on by default). */
    static boolean on(Context c) {
        return BikeService.prefs(c).getBoolean("callAlert", true);
    }

    /** True if this was a call notification (handled here). */
    static boolean posted(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return false;
        if (!Notification.CATEGORY_CALL.equals(n.category)) {
            logOther(sbn, n);
            return false;
        }
        if (c.getPackageName().equals(sbn.getPackageName())) return true;
        boolean incoming = isIncoming(n);
        String seen = sbn.getPackageName() + (incoming ? " ringing" : " in call");
        if (!seen.equals(lastSeen)) {   // which app and state, never the caller
            lastSeen = seen;
            BikeService.log("CALL notification: " + seen);
        }
        if (incoming) {
            String who = caller(n.extras);
            ringingKey = sbn.getKey();
            h.removeCallbacks(holdCheck);
            BikeService s = BikeService.instance;
            if (s != null && on(c)) s.showCaller(who.isEmpty() ? "Unknown" : who);
        } else if (sbn.getKey().equals(ringingKey)) {
            end();   // the same call is now ongoing: answered
        }
        return true;
    }

    /** True if this was a call notification (handled here). */
    static boolean removed(Context c, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || !Notification.CATEGORY_CALL.equals(n.category)) return false;
        if (!sbn.getKey().equals(ringingKey)) return true;
        ringingKey = null;
        // With the screen off, the phone app swaps its notification for a full-screen page about a
        // second into the call, while it is still ringing. Keep the caller up until it stops ringing.
        audio = c.getSystemService(AudioManager.class);
        if (ringing()) {
            BikeService.log("CALL notification gone, phone still ringing");
            holdUntil = System.currentTimeMillis() + MAX_RING_MS;
            h.removeCallbacks(holdCheck);
            h.postDelayed(holdCheck, 1000);
        } else {
            end();
        }
        return true;
    }

    private static final long MAX_RING_MS = 90_000;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static AudioManager audio;
    private static long holdUntil;

    /** Every second after the notification went: still ringing? (Answered or ended = not.) */
    private static final Runnable holdCheck = new Runnable() {
        @Override public void run() {
            if (ringing() && System.currentTimeMillis() < holdUntil) h.postDelayed(this, 1000);
            else end();
        }
    };

    /** The phone's sound mode is "ringtone" only while a call rings (no permission needed). */
    private static boolean ringing() {
        return audio != null && audio.getMode() == AudioManager.MODE_RINGTONE;
    }

    /**
     * Not tagged as a call, but looks like one (a phone or calling app, or a full-screen pop-up):
     * logged once per app and category, never the content, to find calls that are missed.
     */
    private static final java.util.Set<String> loggedOther = new java.util.HashSet<>();

    private static void logOther(StatusBarNotification sbn, Notification n) {
        String pkg = sbn.getPackageName();
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        boolean phoneApp = p.contains("dialer") || p.contains("incall") || p.contains("telecom")
                || p.contains("contacts") || p.contains("phone") || p.equals("com.whatsapp");
        if (!phoneApp && n.fullScreenIntent == null) return;
        String what = pkg + " category=" + n.category + (n.fullScreenIntent != null ? " fullscreen" : "");
        if (loggedOther.add(what)) BikeService.log("Notification (not a call): " + what);
    }

    private static void end() {
        h.removeCallbacks(holdCheck);
        ringingKey = null;
        lastSeen = "";
        BikeService s = BikeService.instance;
        if (s != null) s.stopCaller();
    }

    /** Ringing, not a call in progress. CallStyle says so directly; older styles are judged by their parts. */
    static boolean isIncoming(Notification n) {
        Bundle e = n.extras;
        int type = e == null ? 0 : e.getInt(EXTRA_CALL_TYPE, 0);
        if (type != 0) return type == CALL_INCOMING;
        if (e != null && e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) return false;   // a call in progress shows its running time
        if (n.actions != null) {
            for (Notification.Action a : n.actions) {
                String t = a.title == null ? "" : a.title.toString().toLowerCase(java.util.Locale.ROOT);
                if (t.contains("answer") || t.contains("accept")) return true;
            }
        }
        return n.fullScreenIntent != null;
    }

    /** The caller's name (or number), as the notification shows it. */
    static String caller(Bundle e) {
        if (e == null) return "";
        Object p = e.getParcelable(EXTRA_CALL_PERSON);
        if (p instanceof android.app.Person) {
            CharSequence name = ((android.app.Person) p).getName();
            if (name != null && name.toString().trim().length() > 0) return name.toString().trim();
        }
        CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE);
        return t == null ? "" : t.toString().trim();
    }
}
