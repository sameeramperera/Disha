package com.disha;

/**
 * Turns a raw frame from the bike into a readable log line, so a pasted log shows which byte is
 * which. Byte positions come from TVS Connect's own Ntorq frame classes (the Raider uses the same
 * ones), as listed by the TVSRideCompanion project. Only speed and odometer are confirmed on the
 * Raider; everything else is a candidate to check against the cluster. Multi-byte values are
 * big-endian. Pure Java so it can be tested off the phone.
 */
final class BikeFrames {
    private BikeFrames() {}

    // Latest values checked against the Raider's cluster (2026-11-19 photos). -1 = not received yet.
    static volatile int fuelBars = -1;      // 0x10 byte 6, 0..5 (5 = full)
    static volatile int avgKmL = -1;        // 0x10 byte 13, cluster "AVG MILEAGE"
    static volatile int tripBTenths = -1;   // 0x19 bytes 8-9, cluster "TRIP B" in 0.1 km
    static volatile int batteryTenths = -1; // 0x18 byte 11, volts x10 (fits, not yet checked)
    static volatile long updatedAt = 0;

    /** Stores the known values from a frame with a good checksum. */
    static void update(byte[] v) {
        if (v == null || v.length < 20 || u(v, 0) != 0x5A) return;
        int sum = 0;
        for (int i = 0; i < 18; i++) sum += u(v, i);
        if ((255 - sum % 256) != u(v, 18)) return;
        switch (u(v, 1)) {
            case 0x10:
                if (u(v, 6) <= 5) fuelBars = u(v, 6);
                avgKmL = u(v, 13);
                break;
            case 0x19: tripBTenths = be(v, 8, 2); break;
            case 0x18: batteryTenths = u(v, 11); break;
            default: return;
        }
        updatedAt = System.currentTimeMillis();
    }

    static String tenthsText(int n) { return tenths(n); }

    /** A "   = ..." line for a known frame type, or null. */
    static String describe(byte[] v) {
        if (v == null || v.length < 20 || u(v, 0) != 0x5A) return null;
        switch (u(v, 1)) {
            case 0x10: return "   = speed=" + u(v, 2) + "km/h"
                    + " odo=" + tenths(be(v, 3, 3)) + "km"
                    + " fuel[6]=0x" + h(u(v, 6)) + "(bars?" + (u(v, 6) & 0x0F) + ")"
                    + " avgSpeed[7]=" + u(v, 7)
                    + " tripMode[8]=" + u(v, 8)
                    + " topSpeed[9]=" + u(v, 9)
                    + " throttle[10]=" + u(v, 10)
                    + " light[11]=0x" + h(u(v, 11))
                    + " buttons[12]=" + u(v, 12)
                    + " trip[14-16]=" + tenths(be(v, 14, 3)) + "km"
                    + " rpm[17-18]=" + be(v, 17, 2) + " rpm[16-17]=" + be(v, 16, 2);
            case 0x11: return "   = fuelSensorFault[2]=" + u(v, 2)
                    + " state[3]=" + u(v, 3)
                    + " serviceDue[4]=" + u(v, 4)
                    + " state1[6]=" + u(v, 6)
                    + " firmware[7]=" + u(v, 7)
                    + " milCode[8]=" + u(v, 8)
                    + " model[9]=" + u(v, 9)
                    + " diag[10]=0x" + h(u(v, 10)) + "(engineLight=" + ((u(v, 10) >> 2) & 1) + ")"
                    + " issCode[11]=" + u(v, 11)
                    + " indicators[12]=0x" + h(u(v, 12))
                    + " connector[13]=" + u(v, 13);
            case 0x18: return "   = load[2]=" + u(v, 2)
                    + " injTimeTotal[3-4]=" + be(v, 3, 2)
                    + " map[5]=" + u(v, 5) + "kPa"
                    + " intake[7]=" + (u(v, 7) - 40) + "C"
                    + " engineTemp[8]=" + u(v, 8) + "C"
                    + " baro[9-10]=" + be(v, 9, 2)
                    + " battery[11]=" + tenths(u(v, 11)) + "V"
                    + " runtime[12-13]=" + be(v, 12, 2) + "s"
                    + " dist[14-15]=" + be(v, 14, 2)
                    + " injVolume[16-17]=" + be(v, 16, 2);
            case 0x19: return "   = issTime[2-4]=" + be(v, 2, 3)
                    + " ota[5]=" + u(v, 5)
                    + " avgKmL[8]=" + u(v, 8)
                    + " nowKmL[9]=" + u(v, 9)
                    + " range[11-12]=" + be(v, 11, 2) + "km"
                    + " issCount[15-17]=" + be(v, 15, 3);
            default: return null;
        }
    }

    private static int u(byte[] v, int i) { return v[i] & 0xFF; }

    private static int be(byte[] v, int at, int len) {
        int n = 0;
        for (int i = 0; i < len; i++) n = n << 8 | u(v, at + i);
        return n;
    }

    private static String h(int b) { return String.format("%02X", b); }

    private static String tenths(int n) { return (n / 10) + "." + (n % 10); }
}
