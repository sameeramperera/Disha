package com.disha.maps;

import com.disha.bike.TvsProtocol;

/**
 * Known Google Maps arrow icons (32x32 masks from IconReader) -> TVS pictogram.
 * ArrowShape handles icons not listed here.
 */
final class IconTable {
    private IconTable() {}

    /** {hex mask, picto}. */
    private static final Object[][] KNOWN = {
        // Roundabout, 1st exit (left), clockwise.
        // Only the driven part of the ring is solid, so the shape alone looks like a plain left turn.
        {"0000000000000000000000000000000000000000000000000060000000f0000001f0000003e0000007c000000f8000001fffe0003fffe0003fffe0001ffff0000f80f00007c0780003e03f8001f03f8000f00f800060038000000380000003800000038000000380000003800000038000000000000000000000000000000000",
                TvsProtocol.P_RB_LEFT},
        // Roundabout: round the left, exit right (3rd).
        {"0000000000000000000000000000000000000000000000000000060007f00f000ffc0f801ffc07c03e1e03e03c0f01f0380ffff87807fffc7807fffc7807fff83c0001f03e0003e01fc007c00fe00f8007e00f0001c0060001c0000001c0000001c0000001c0000001c0000001c0000000000000000000000000000000000000",
                TvsProtocol.P_RB_RIGHT},
        // Destination pin (ring + dot + pointed tail). ArrowShape would read the ring's hole
        // as a roundabout.
        {"000000000000000000000000000ff000003ffc00007c3e0000f00f0001e0078001c00380038001c0038001c00303c0c00707c0e00707e0e00703c0e00703c0e0030000c0038001c0038001c001c0038000e0070000f00f0000701e0000383c00001c7800000ff0000007e0000003c00000018000000000000000000000000000",
                TvsProtocol.P_ARRIVE},
        // Roundabout straight through, clockwise. Its angle is close to the 45 deg boundary
        // ArrowShape uses, so pin it here.
        {"00000000000180000003c0000007e000000ff000001ff800003ffc00007bde0000f3cf0000e3c7000043c2000007c000001fc000003fc000003c0000007800000070000000700000007000000070000000780000003c0000003fc000001fc0000007c0000003c0000003c0000003c00000000000000000000000000000000000",
                TvsProtocol.P_RB_STRAIGHT},
        // U-turn, up the left and back down on the right (left-hand traffic).
        {"00000000000000000000000000000000003f000000ffc00001ffe00003f3f00007c0780007803c000f003c000f001c000e001c000e001c000e001c000e031c300e079c780e07dcf00e03fde00e01ffc00e00ff800e007f000e003f000e001e000e000c000e0000000e0000000e00000000000000000000000000000000000000",
                TvsProtocol.P_UTURN_RIGHT},
    };

    /** Max differing pixels (of 1024) to still count as the same arrow. */
    private static final int MAX_DIFF = 80;

    static int lookup(boolean[] ink) {
        int best = -1, bestDiff = Integer.MAX_VALUE;
        for (Object[] k : KNOWN) {
            int diff = diff(ink, (String) k[0]);
            if (diff < bestDiff) { bestDiff = diff; best = (Integer) k[1]; }
        }
        return bestDiff <= MAX_DIFF ? best : -1;
    }

    private static int diff(boolean[] ink, String hex) {
        int d = 0;
        for (int i = 0; i < hex.length() && i * 4 + 3 < ink.length; i++) {
            int nib = Character.digit(hex.charAt(i), 16);
            for (int b = 0; b < 4; b++) {
                if (((nib >> (3 - b)) & 1) == 1 != ink[i * 4 + b]) d++;
            }
        }
        return d;
    }
}
