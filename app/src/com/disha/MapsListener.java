package com.disha;

import com.disha.bike.BikeService;
import com.disha.bike.CallWatcher;
import com.disha.bike.TvsProtocol;
import com.disha.maps.IconReader;
import com.disha.maps.MapsParser;

import android.app.Notification;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Reads Google Maps' ongoing navigation notification and forwards it to the bike; calls go to CallWatcher. */
public class MapsListener extends NotificationListenerService {
    static final String MAPS = "com.google.android.apps.maps";

    public static volatile String lastRaw = "(no Google Maps notification yet)";
    public static volatile String lastParsed = "";
    /** What the app screen shows: the last update sent, the road it refers to, the ETA. */
    public static volatile TvsProtocol.Nav current;
    public static volatile String road = "", eta = "";
    /** What tapping the Maps navigation notification opens: the trip in progress. */
    public static volatile android.app.PendingIntent tripIntent;
    private static final java.util.regex.Pattern ETA = java.util.regex.Pattern.compile(
            "(\\d{1,2}[:.]\\d{2}\\s*(?:[AaPp]\\.?[Mm]\\.?)?)\\s*ETA");
    private static String lastKey = "";
    private static long lastSentAt = 0;
    private static String lastIcon = "";
    /** A Google Maps trip is in progress (set by its first update, cleared when it ends). */
    private static boolean navActive;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TvsProtocol.Nav pending;
    private final Runnable flush = this::flushPending;

    static volatile boolean connected;

    @Override
    public void onListenerConnected() {
        connected = true;
        BikeService.initLog(this);
        BikeService.log("Notification reader connected");
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        BikeService.log("Notification reader disconnected, asking Android to reconnect it");
        rebind(this);
    }

    /**
     * Android does not always restart the reader after an app update (no Maps turns, no calls until
     * the phone restarts). Asking for a rebind is harmless when it is already running.
     */
    public static void rebind(android.content.Context c) {
        if (connected) return;
        String on = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (on == null || !on.contains(c.getPackageName())) return;   // permission not given
        try {
            requestRebind(new android.content.ComponentName(c, MapsListener.class));
            BikeService.log("Notification reader: asked Android to reconnect it");
        } catch (Exception e) {
            BikeService.log("Notification reader rebind failed: " + e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (CallWatcher.posted(this, sbn)) return;
        if (!MAPS.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_ONGOING_EVENT) == 0) return;
        if (n.contentIntent != null) tripIntent = n.contentIntent;
        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String info = str(e.getCharSequence(Notification.EXTRA_INFO_TEXT));
        String summary = str(e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        if (sub.isEmpty()) sub = info.isEmpty() ? summary : info;

        IconReader.Result icon = IconReader.read(this, n);
        IconReader.logIfNew(icon);
        String iconHash = icon == null ? "none" : icon.hash;

        String raw = "title=[" + title + "] text=[" + text + "] sub=[" + sub + "] big=[" + big + "]";
        if (!raw.equals(lastRaw) || !iconHash.equals(lastIcon)) {
            lastRaw = raw;
            lastIcon = iconHash;
            BikeService.log("MAPS " + raw + " icon=" + shortHash(iconHash));
        }

        TvsProtocol.Nav nav = MapsParser.parse(title, text, sub, big, icon == null ? -1 : icon.picto);
        if (nav == null) return;
        // Navigation is running: make sure the bike link is up without the user opening the app.
        // On the first update of a trip always; later only if the link died by itself (a Disconnect
        // pressed mid-trip is respected).
        if (mirrorOn(this)) BikeService.autoStart(this, navActive ? "maps" : "maps-start");
        navActive = true;
        road = text.startsWith("toward ") ? text.substring(7) : text;
        java.util.regex.Matcher em = ETA.matcher(sub);
        eta = em.find() ? em.group(1).trim() : "";
        String key = nav.toString();
        if (key.equals(lastKey)) return; // unchanged; BikeService keeps re-sending the last one
        // At most one change per second, but never drop the newest one: send it when the second is up.
        pending = nav;
        h.removeCallbacks(flush);
        long wait = 1000 - (System.currentTimeMillis() - lastSentAt);
        if (wait <= 0) flushPending(); else h.postDelayed(flush, wait);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (CallWatcher.removed(this, sbn)) return;
        if (!MAPS.equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_ONGOING_EVENT) == 0) return;
        h.removeCallbacks(flush);
        pending = null;
        lastKey = "";
        MapsParser.reset();
        IconReader.forgetLogged();
        navActive = false;
        current = null;
        tripIntent = null;
        road = eta = "";
        BikeService.log("Google Maps navigation ended");
        BikeService s = BikeService.instance;
        if (s != null) s.stopNav();
    }

    private void flushPending() {
        TvsProtocol.Nav nav = pending;
        pending = null;
        if (nav == null) return;
        String key = nav.toString();
        lastKey = key;
        lastSentAt = System.currentTimeMillis();
        lastParsed = key;
        current = nav;
        BikeService s = BikeService.instance;
        if (!mirrorOn(this)) return;   // turns still show in the app, just not on the bike
        if (s != null) s.sendNav(nav);
        else BikeService.log("Parsed " + key + " (bike service not running)");
    }

    /** Settings > Mirror navigation: send Google Maps turns to the cluster (on by default). */
    static boolean mirrorOn(android.content.Context c) {
        return BikeService.prefs(c).getBoolean("mirrorNav", true);
    }

    private static String shortHash(String h) {
        return h.length() > 12 ? "#" + Integer.toHexString(h.hashCode()) : h;
    }

    private static String str(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }
}
