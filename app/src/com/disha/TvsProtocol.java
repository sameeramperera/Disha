package com.disha;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TVS SmartXonnect (N360 / Raider) navigation frames. Every frame is 20 bytes and ends with 0xFF.
 */
public final class TvsProtocol {
    // Turn arrow ("pictogram") codes understood by the cluster.
    public static final int P_LEFT = 0, P_SHARP_LEFT = 1, P_SLIGHT_LEFT = 2, P_RIGHT = 3,
            P_SHARP_RIGHT = 4, P_SLIGHT_RIGHT = 5, P_UTURN_LEFT = 6, P_STRAIGHT = 7, P_ARRIVE = 8,
            P_KEEP_LEFT = 15, P_KEEP_RIGHT = 16, P_LEAVE_HWY_LEFT = 34, P_ENTER_HWY_RIGHT = 35,
            P_LEAVE_HWY_RIGHT = 37, P_UTURN_RIGHT = 41,
            P_REROUTING = 72;

    /**
     * Roundabouts. Every code is a ring
     * entered from the bottom, and the code only says which DIRECTION the exit arrow points, in 45
     * degree steps clockwise round the clock face. It is not an exit count. Ring direction is not shown.
     */
    public static final int P_RB_SHARP_LEFT = 65,   // down-left
            P_RB_LEFT = 66,                          // left (9 o'clock)
            P_RB_SLIGHT_LEFT = 67,                   // up-left
            P_RB_STRAIGHT = 68,                      // straight up (12 o'clock)
            P_RB_SLIGHT_RIGHT = 69,                  // up-right
            P_RB_RIGHT = 70,                         // right (3 o'clock)
            P_RB_SHARP_RIGHT = 71;                   // down-right

    static boolean isRoundabout(int p) {
        return p >= P_RB_SHARP_LEFT && p <= P_RB_SHARP_RIGHT;
    }

    /** Exit direction drawn by a roundabout code (degrees: 0 = right, 90 = up, 180 = left). */
    static int roundaboutAngle(int p) {
        return 90 - (p - P_RB_STRAIGHT) * 45;
    }

    /**
     * Code for a roundabout whose exit leaves in direction a (degrees, 0 = right, 90 = up,
     * 180 = left, 270 = back down). Going all the way round (U-turn) there is no "down" picture:
     * show the last exit before the entry, which is down-right for clockwise (left-hand traffic).
     */
    static int roundaboutCode(double a, boolean leftHandTraffic) {
        while (a < -90) a += 360;
        while (a >= 270) a -= 360;               // -90 .. 270
        if (a > 247.5 || a < -67.5) return leftHandTraffic ? P_RB_SHARP_RIGHT : P_RB_SHARP_LEFT;
        int steps = (int) Math.round((90 - a) / 45.0);   // 0 = up, +1 = up-right, -1 = up-left ...
        return Math.max(P_RB_SHARP_LEFT, Math.min(P_RB_SHARP_RIGHT, P_RB_STRAIGHT + steps));
    }

    private TvsProtocol() {}

    /** One navigation update. */
    public static final class Nav {
        public int distNextM;   // metres to the next turn
        public int minutesLeft; // minutes to destination
        public int distLeftM;   // metres to destination
        public int picto;       // arrow code
        public String text;     // short instruction, max 17 ASCII chars on the cluster

        public Nav(int distNextM, int minutesLeft, int distLeftM, int picto, String text) {
            this.distNextM = distNextM;
            this.minutesLeft = minutesLeft;
            this.distLeftM = distLeftM;
            this.picto = picto;
            this.text = text;
        }

        @Override public String toString() {
            return "\"" + text + "\" picto=" + picto + " next=" + distNextM + "m left=" + distLeftM
                    + "m " + minutesLeft + "min";
        }
    }

    /** Status byte (index 14). */
    static int status(Nav n) {
        if (n.picto == P_ARRIVE) return 8;
        if (n.picto == P_REROUTING) return 0;
        // Roundabouts: status 1 between 65 m and 1 km, otherwise 0.
        if (isRoundabout(n.picto)) return n.distNextM >= 65 && n.distNextM <= 1000 ? 1 : 0;
        int def = n.picto == P_STRAIGHT ? 5 : 0;
        String t = n.text == null ? "" : n.text.toLowerCase();
        if (n.distNextM < 65) {
            if (t.contains("left")) return 3;
            if (t.contains("right")) return 4;
            return def;
        }
        if (n.distNextM <= 1000) return 1;
        return def;
    }

    static byte[] asciiText(String s) {
        if (s == null) s = "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) if (c >= 32 && c < 127) b.append(c);
        byte[] raw = b.toString().getBytes(StandardCharsets.US_ASCII);
        if (raw.length > 17) {
            byte[] cut = new byte[17];
            System.arraycopy(raw, 0, cut, 0, 17);
            return cut;
        }
        return raw;
    }

    /** Frame 1: 0x5A 0x4E ... (distances, arrow, status). */
    public static byte[] controlFrame(Nav n) {
        byte[] f = new byte[20];
        int dNext = n.picto == P_ARRIVE || n.picto == P_REROUTING ? 0 : clamp(n.distNextM, 0xFFFF);
        int mins = clamp(n.minutesLeft, 0xFFFF);
        int dLeft = clamp(n.distLeftM, 0xFFFFFF);
        f[0] = 0x5A;
        f[1] = 0x4E;
        f[2] = (byte) (dNext >> 8);
        f[3] = (byte) dNext;
        f[4] = (byte) (mins >> 8);
        f[5] = (byte) mins;
        f[6] = (byte) (dLeft >> 16);
        f[7] = (byte) (dLeft >> 8);
        f[8] = (byte) dLeft;
        f[9] = (byte) n.picto;
        f[10] = (byte) (asciiText(n.text).length > 17 ? 2 : 1);
        f[11] = 1;
        f[12] = 0;
        f[13] = 0;
        f[14] = (byte) status(n);
        // 15..18 stay 0
        f[19] = (byte) 0xFF;
        return f;
    }

    /** Frame 2: 0x5B 0x4F + instruction text. */
    public static byte[] textFrame(Nav n) {
        byte[] f = new byte[20];
        f[0] = 0x5B;
        f[1] = 0x4F;
        byte[] t = asciiText(n.text);
        System.arraycopy(t, 0, f, 2, t.length);
        f[19] = (byte) 0xFF;
        return f;
    }

    /** Second copy TVS sends: checksum in byte[len-2], then XOR bytes 2..len-2 with 0xEA. */
    public static byte[] protectedCopy(byte[] plain) {
        int len = plain.length;
        byte[] out = plain.clone();
        int sum = 0;
        for (int i = 0; i < len - 2; i++) sum += plain[i] & 0xFF;
        out[len - 2] = (byte) (255 - (sum % 256));
        out[len - 1] = (byte) 0xFF;
        for (int i = 2; i <= len - 2; i++) out[i] = (byte) (out[i] ^ 0xEA);
        return out;
    }

    /** All writes for one update, in TVS order: control (plain, protected), text (plain, protected). */
    public static List<byte[]> frames(Nav n, boolean plain, boolean prot) {
        List<byte[]> list = new ArrayList<>();
        for (byte[] f : new byte[][]{controlFrame(n), textFrame(n)}) {
            if (plain) list.add(f);
            if (prot) list.add(protectedCopy(f));
        }
        return list;
    }

    /**
     * Phone status 0x5B 0x4A, sent every 2 s like TVS Connect: byte 2 = signal and battery bars as
     * two hex digits, 5 = 2, 6-9 = clock (12-hour, midnight is 12, then 0 AM / 1 PM), 12-14 = date
     * (day, month, year % 100). No checksum. Layout from TVS Connect (via TVSRideCompanion); on the
     * Raider the bike only starts sending its data after the first one.
     */
    public static byte[] phoneStatusFrame(int batteryBars, java.util.Calendar cal) {
        byte[] f = new byte[20];
        f[0] = 0x5B;
        f[1] = 0x4A;
        f[2] = (byte) (0x50 | clamp(batteryBars, 5));   // signal 5 bars (not read), battery
        f[5] = 2;
        int hour = cal.get(java.util.Calendar.HOUR);
        f[6] = (byte) (hour == 0 ? 12 : hour);
        f[7] = (byte) cal.get(java.util.Calendar.MINUTE);
        f[8] = (byte) cal.get(java.util.Calendar.SECOND);
        f[9] = (byte) (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM ? 0 : 1);
        f[12] = (byte) cal.get(java.util.Calendar.DAY_OF_MONTH);
        f[13] = (byte) (cal.get(java.util.Calendar.MONTH) + 1);
        f[14] = (byte) (cal.get(java.util.Calendar.YEAR) % 100);
        f[19] = (byte) 0xFF;
        return f;
    }

    static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }

    public static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X ", x & 0xFF));
        return s.toString().trim();
    }
}
