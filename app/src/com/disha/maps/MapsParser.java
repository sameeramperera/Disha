package com.disha.maps;

import com.disha.bike.TvsProtocol;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Google Maps' navigation notification text (English) into a TVS navigation update.
 * Google Maps typically shows: title = "250 m" or "Turn left onto X", text = instruction / road,
 * subText = "12 min · 4.5 km · 10:20 ETA". We search all of them, so layout changes hurt less.
 */
public final class MapsParser {
    private static final Pattern DIST = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(km|m|mi|ft|yd)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*(?:hr|hrs|hour|hours|h)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MINS = Pattern.compile("(\\d+)\\s*(?:min|mins|minutes)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXIT_NUM = Pattern.compile("(\\d)(?:st|nd|rd|th)\\s+exit",
            Pattern.CASE_INSENSITIVE);

    /** The app is for Sri Lanka and India only: traffic drives on the left, roundabouts go clockwise. */
    public static final boolean leftHandTraffic = true;

    /** Last trip totals seen. Maps leaves them out while rerouting; the bike would show 0 otherwise. */
    private static int lastMinutes = -1, lastDistLeft = -1;

    private MapsParser() {}

    /** Navigation ended: forget the trip totals. */
    public static void reset() {
        lastMinutes = lastDistLeft = -1;
    }

    public static TvsProtocol.Nav parse(String title, String text, String sub, String big) {
        return parse(title, text, sub, big, -1);
    }

    /** iconPicto: arrow recognised from the notification icon, or -1. Explicit words still win. */
    public static TvsProtocol.Nav parse(String title, String text, String sub, String big, int iconPicto) {
        title = n(title);
        text = n(text);
        sub = n(sub);
        big = n(big);
        String main = (title + " | " + text + " | " + big).toLowerCase(Locale.ROOT);

        int distNext = firstDistance(title);
        if (distNext < 0) distNext = firstDistance(text);
        if (distNext < 0) distNext = firstDistance(big);

        int distLeft = firstDistance(sub);
        int minutes = minutes(sub);

        if (distLeft >= 0) lastDistLeft = distLeft; else distLeft = lastDistLeft;
        if (minutes >= 0) lastMinutes = minutes; else minutes = lastMinutes;

        int picto = picto(main);
        boolean fromWords = picto >= 0 && picto != TvsProtocol.P_STRAIGHT;
        if (iconPicto >= 0 && !fromWords) picto = iconPicto;
        // Words say "roundabout, 2nd exit" but only the icon knows which way that exit points.
        if (TvsProtocol.isRoundabout(picto) && TvsProtocol.isRoundabout(iconPicto)) picto = iconPicto;
        // Next step as far away as the destination (this update's own trip distance, not a stale
        // one): the next step IS the destination. Maps then shows only the place name and a pin.
        // Only a fallback for when the icon wasn't read: on a long last stretch Maps shows
        // "Stay on A3" with a straight arrow and 15 km for both distances (2026-11-21 ride),
        // and the pin icon itself is recognised.
        if (!fromWords && iconPicto < 0 && distNext >= 0 && distLeft > 0 && firstDistance(sub) >= 0
                && distNext >= distLeft && picto != TvsProtocol.P_REROUTING) picto = TvsProtocol.P_ARRIVE;
        if (picto < 0 && distNext < 0) return null; // not a turn-by-turn notification
        if (picto < 0) picto = TvsProtocol.P_STRAIGHT;
        // Right after a turn Maps briefly shows the road name with no distance. Sending that as
        // "0 m" makes the bike drop the navigation panel; keep showing the last update instead.
        if (distNext < 0 && picto != TvsProtocol.P_ARRIVE && picto != TvsProtocol.P_REROUTING) return null;

        return new TvsProtocol.Nav(Math.max(distNext, 0), Math.max(minutes, 0),
                Math.max(distLeft, 0), picto, label(picto, main));
    }

    static int picto(String s) {
        if (s.contains("arriv") || s.contains("destination") || s.contains("you have reached"))
            return TvsProtocol.P_ARRIVE;
        if (s.contains("rerouting") || s.contains("re-routing")) return TvsProtocol.P_REROUTING;
        if (s.contains("u-turn") || s.contains("u turn") || s.contains("make a u")) {
            if (s.contains("left")) return TvsProtocol.P_UTURN_LEFT;
            if (s.contains("right")) return TvsProtocol.P_UTURN_RIGHT;
            return leftHandTraffic ? TvsProtocol.P_UTURN_RIGHT : TvsProtocol.P_UTURN_LEFT;
        }
        if (s.contains("roundabout") || s.contains("traffic circle") || s.contains("rotary")) {
            // Text only gives the exit count; the bike needs a direction. Guess a plain 4-way
            // roundabout. The icon (real exit angle) replaces this whenever we have it.
            int exit = roundaboutExit(s);
            if (s.contains("u-turn") || exit >= 4)
                return leftHandTraffic ? TvsProtocol.P_RB_SHARP_RIGHT : TvsProtocol.P_RB_SHARP_LEFT;
            if (exit == 2) return TvsProtocol.P_RB_STRAIGHT;
            boolean towardsLeft = (exit <= 1) == leftHandTraffic; // clockwise: 1st exit is on the left
            return towardsLeft ? TvsProtocol.P_RB_LEFT : TvsProtocol.P_RB_RIGHT;
        }
        if (s.contains("sharp left")) return TvsProtocol.P_SHARP_LEFT;
        if (s.contains("sharp right")) return TvsProtocol.P_SHARP_RIGHT;
        if (s.contains("slight left") || s.contains("bear left")) return TvsProtocol.P_SLIGHT_LEFT;
        if (s.contains("slight right") || s.contains("bear right")) return TvsProtocol.P_SLIGHT_RIGHT;
        if (s.contains("keep left") || s.contains("stay left") || s.contains("fork left")
                || (s.contains("fork") && s.contains("left"))) return TvsProtocol.P_KEEP_LEFT;
        if (s.contains("keep right") || s.contains("stay right") || s.contains("fork right")
                || (s.contains("fork") && s.contains("right"))) return TvsProtocol.P_KEEP_RIGHT;
        if (s.contains("exit") || s.contains("ramp")) {
            if (s.contains("left")) return TvsProtocol.P_LEAVE_HWY_LEFT;
            if (s.contains("right")) return TvsProtocol.P_LEAVE_HWY_RIGHT;
        }
        if (s.contains("turn left") || s.matches(".*\\bleft\\b.*")) return TvsProtocol.P_LEFT;
        if (s.contains("turn right") || s.matches(".*\\bright\\b.*")) return TvsProtocol.P_RIGHT;
        if (s.contains("continue") || s.contains("straight") || s.contains("head ")
                || s.contains("merge") || s.contains("toward")) return TvsProtocol.P_STRAIGHT;
        return -1;
    }

    static int roundaboutExit(String s) {
        Matcher m = EXIT_NUM.matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1));
        String[] words = {"first", "second", "third", "fourth", "fifth", "sixth"};
        for (int i = 0; i < words.length; i++) if (s.contains(words[i] + " exit")) return i + 1;
        return 1;
    }

    /** Short text for the cluster (max 17 chars). Must contain "left"/"right" for TVS status logic. */
    static String label(int picto, String s) {
        switch (picto) {
            case TvsProtocol.P_LEFT: return "Turn left";
            case TvsProtocol.P_RIGHT: return "Turn right";
            case TvsProtocol.P_SHARP_LEFT: return "Sharp left";
            case TvsProtocol.P_SHARP_RIGHT: return "Sharp right";
            case TvsProtocol.P_SLIGHT_LEFT: return "Slight left";
            case TvsProtocol.P_SLIGHT_RIGHT: return "Slight right";
            case TvsProtocol.P_KEEP_LEFT: return "Keep left";
            case TvsProtocol.P_KEEP_RIGHT: return "Keep right";
            case TvsProtocol.P_UTURN_LEFT: case TvsProtocol.P_UTURN_RIGHT: return "Make a U-turn";
            case TvsProtocol.P_LEAVE_HWY_LEFT: return "Exit left";
            case TvsProtocol.P_LEAVE_HWY_RIGHT: return "Exit right";
            case TvsProtocol.P_ARRIVE: return "Arrived";
            case TvsProtocol.P_REROUTING: return "Rerouting";
            case TvsProtocol.P_RB_SHARP_LEFT: return "Rbt sharp left";
            case TvsProtocol.P_RB_LEFT: return "Roundabout left";
            case TvsProtocol.P_RB_SLIGHT_LEFT: return "Rbt slight left";
            case TvsProtocol.P_RB_STRAIGHT: return "Roundabout ahead";
            case TvsProtocol.P_RB_SLIGHT_RIGHT: return "Rbt slight right";
            case TvsProtocol.P_RB_RIGHT: return "Roundabout right";
            case TvsProtocol.P_RB_SHARP_RIGHT: return "Rbt sharp right";
            default: return "Go straight";
        }
    }

    /** First distance in the string, in metres, or -1. */
    static int firstDistance(String s) {
        Matcher m = DIST.matcher(s);
        if (!m.find()) return -1;
        double v = Double.parseDouble(m.group(1).replace(',', '.'));
        switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "km": return (int) Math.round(v * 1000);
            case "mi": return (int) Math.round(v * 1609.34);
            case "ft": return (int) Math.round(v * 0.3048);
            case "yd": return (int) Math.round(v * 0.9144);
            default: return (int) Math.round(v);
        }
    }

    static int minutes(String s) {
        int total = -1;
        Matcher h = HOURS.matcher(s);
        if (h.find()) total = Integer.parseInt(h.group(1)) * 60;
        Matcher m = MINS.matcher(s);
        if (m.find()) total = Math.max(total, 0) + Integer.parseInt(m.group(1));
        return total;
    }

    private static String n(String s) {
        // Maps writes "20 m" and "6:52 AM". Android's regex counts those as \s, plain Java
        // does not; normalise so the parser behaves the same everywhere (and in PC tests).
        return s == null ? "" : s.replace(' ', ' ').replace(' ', ' ');
    }
}
