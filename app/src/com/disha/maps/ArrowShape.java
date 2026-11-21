package com.disha.maps;

import com.disha.bike.TvsProtocol;

import java.util.ArrayDeque;

/**
 * Works out the turn direction from the shape of Google Maps' arrow icon (an ink mask), so we do
 * not need a table of known icons. Pure Java so it can be tested on a PC.
 *
 * Idea: the arrow is one stroke that starts at the bottom (the stem) and ends at the arrowhead.
 * We walk the ink from the stem (BFS, so distance is measured along the stroke), then compare the
 * centre of the last part of the stroke with the part before it. That gives the direction the
 * arrowhead points: up = straight, left = left, down-left = sharp left, down = U-turn, ...
 * A ring with a hole in the middle is a roundabout; the exit is where the stroke leaves the ring.
 */
final class ArrowShape {
    private ArrowShape() {}

    /** Last angle measured (degrees, 90 = up, 180 = left), for the log. */
    static volatile String lastDebug = "";
    /** Direction the arrowhead points in the last classify() (degrees, 90 = up), or NaN. */
    static volatile double lastAngle = Double.NaN;

    static int classify(boolean[] ink, int n, boolean leftHandTraffic) {
        lastAngle = Double.NaN;
        int count = 0, minX = n, maxX = -1, minY = n, maxY = -1;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (ink[y * n + x]) {
                    count++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        if (count < n * n / 60 || count > n * n * 3 / 4) { lastDebug = "ink=" + count; return -1; }

        // ---- roundabout: background enclosed by ink ----
        boolean[] outside = new boolean[n * n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            seed(ink, outside, q, i, 0, n); seed(ink, outside, q, i, n - 1, n);
            seed(ink, outside, q, 0, i, n); seed(ink, outside, q, n - 1, i, n);
        }
        while (!q.isEmpty()) {
            int p = q.poll(), x = p % n, y = p / n;
            seed(ink, outside, q, x + 1, y, n); seed(ink, outside, q, x - 1, y, n);
            seed(ink, outside, q, x, y + 1, n); seed(ink, outside, q, x, y - 1, n);
        }
        int hole = 0; double hx = 0, hy = 0;
        for (int i = 0; i < n * n; i++)
            if (!ink[i] && !outside[i]) { hole++; hx += i % n; hy += i / n; }
        if (hole >= Math.max(3, n * n / 200)) {
            if (isPin(ink, n, minY, maxY)) { lastDebug = "destination pin"; return TvsProtocol.P_ARRIVE; }
            return roundabout(ink, n, hx / hole, hy / hole, Math.sqrt(hole / Math.PI), leftHandTraffic);
        }

        // ---- stem: narrowest ink run on the bottom row (the arrowhead side is wider) ----
        int[] runs = runs(ink, n, maxY);
        int stemA = runs[0], stemB = runs[1], headA = -1, headB = -1;
        for (int i = 2; i + 1 < runs.length && runs[i] >= 0; i += 2) {
            if (runs[i + 1] - runs[i] < stemB - stemA) {
                headA = stemA; headB = stemB; stemA = runs[i]; stemB = runs[i + 1];
            } else if (headA < 0 || runs[i + 1] - runs[i] > headB - headA) {
                headA = runs[i]; headB = runs[i + 1];
            }
        }

        // ---- distance along the ink from the stem (steps may jump 1-px gaps: Maps draws dashed stems) ----
        int[] dist = new int[n * n], parent = new int[n * n];
        java.util.Arrays.fill(dist, -1);
        for (int x = stemA; x <= stemB; x++) { dist[maxY * n + x] = 0; q.add(maxY * n + x); }
        int maxD = 0;
        while (!q.isEmpty()) {
            int p = q.poll(), x = p % n, y = p / n, d = dist[p];
            maxD = Math.max(maxD, d);
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2; dx <= 2; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= n || ny >= n) continue;
                    int np = ny * n + nx;
                    if (ink[np] && dist[np] < 0) { dist[np] = d + 1; parent[np] = p; q.add(np); }
                }
        }
        if (maxD < n / 4) { lastDebug = "short stroke " + maxD; return -1; }

        double fx = 0, fy = 0, mx = 0, my = 0; int fc = 0, mc = 0, tip = -1, farMinX = n, farMaxX = -1;
        for (int i = 0; i < n * n; i++) {
            if (dist[i] < 0) continue;
            if (dist[i] == maxD && tip < 0) tip = i;
            if (dist[i] >= maxD * 0.8) {
                fx += i % n; fy += i / n; fc++;
                farMinX = Math.min(farMinX, i % n); farMaxX = Math.max(farMaxX, i % n);
            }
            else if (dist[i] >= maxD * 0.5) { mx += i % n; my += i / n; mc++; }
        }
        if (fc == 0 || mc == 0) return -1;
        fx /= fc; fy /= fc; mx /= mc; my /= mc;
        double a = Math.toDegrees(Math.atan2(my - fy, fx - mx)); // y grows downwards
        if (a < -90) a += 360;                                     // range -90 .. 270
        double stemX = (stemA + stemB) / 2.0;
        lastAngle = a;
        lastDebug = String.format(java.util.Locale.ROOT, "angle=%.0f tip=(%.1f,%.1f) stem=%.1f",
                a, fx, fy, stemX);

        // ---- roundabout drawn as an open loop (Maps greys out the unused part of the ring):
        // the stroke swings out to one side, past both the stem and the arrowhead, then comes back.
        int pathMinX = n, pathMaxX = -1;
        for (int p = tip; p >= 0 && dist[p] > 0; p = parent[p]) {
            pathMinX = Math.min(pathMinX, p % n); pathMaxX = Math.max(pathMaxX, p % n);
        }
        int swing = Math.max(3, n / 8);
        boolean loopLeft = pathMinX <= Math.min(stemA, farMinX) - swing;    // clockwise
        boolean loopRight = pathMaxX >= Math.max(stemB, farMaxX) + swing;   // anticlockwise
        if (loopLeft != loopRight) {
            lastDebug += " loop" + (loopLeft ? "L" : "R") + " path x " + pathMinX + ".." + pathMaxX;
            return roundaboutExit(a, leftHandTraffic);
        }

        if (a >= 65 && a <= 115) return TvsProtocol.P_STRAIGHT;
        if (a > 115 && a <= 155) return TvsProtocol.P_SLIGHT_LEFT;
        if (a > 155 && a <= 205) return TvsProtocol.P_LEFT;
        if (a > 205 && a <= 238) return TvsProtocol.P_SHARP_LEFT;
        if (a >= 25 && a < 65) return TvsProtocol.P_SLIGHT_RIGHT;
        if (a >= -25 && a < 25) return TvsProtocol.P_RIGHT;
        if (a >= -58 && a < -25) return TvsProtocol.P_SHARP_RIGHT;
        // Pointing down: U-turn. The side is where the arrowhead ends up.
        double tipX = headA >= 0 ? (headA + headB) / 2.0 : fx;
        return tipX < stemX ? TvsProtocol.P_UTURN_LEFT : TvsProtocol.P_UTURN_RIGHT;
    }

    /**
     * Destination pin: a ring like a roundabout, but its bottom narrows row by row to a point,
     * where a roundabout has a straight entry stem of constant width.
     */
    static boolean isPin(boolean[] ink, int n, int minY, int maxY) {
        if (maxY - minY < 8) return false;
        int prev = Integer.MAX_VALUE;
        for (int y = maxY - 4; y <= maxY; y++) {
            int a = n, b = -1;
            for (int x = 0; x < n; x++) if (ink[y * n + x]) { a = Math.min(a, x); b = Math.max(b, x); }
            int w = b - a + 1;
            if (b < 0 || w >= prev) return false;
            prev = w;
        }
        return prev <= 4;
    }

    private static int roundabout(boolean[] ink, int n, double cx, double cy, double r, boolean lh) {
        // The exit is the ink point farthest from the ring centre, ignoring the entry stem below it.
        double best = -1, bx = cx, by = cy;
        for (int i = 0; i < n * n; i++) {
            if (!ink[i]) continue;
            double x = i % n, y = i / n;
            if (y > cy + r && Math.abs(x - cx) < r) continue; // entry stem
            double d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (d > best) { best = d; bx = x; by = y; }
        }
        double a = Math.toDegrees(Math.atan2(cy - by, bx - cx));
        if (a < -90) a += 360; // -90 .. 270; 0 = right, 90 = up, 180 = left
        lastDebug = String.format(java.util.Locale.ROOT, "roundabout exit angle=%.0f", a);
        return roundaboutExit(a, lh);
    }

    /** a = direction the exit leaves in (0 = right, 90 = up, 180 = left), entering from the bottom. */
    static int roundaboutExit(double a, boolean lh) {
        return TvsProtocol.roundaboutCode(a, lh);
    }

    /** Ink runs on row y as {start, end, start, end, ..., -1}. */
    private static int[] runs(boolean[] ink, int n, int y) {
        int[] r = new int[n + 2];
        java.util.Arrays.fill(r, -1);
        int k = 0;
        for (int x = 0; x < n; x++) {
            if (ink[y * n + x] && (x == 0 || !ink[y * n + x - 1])) r[k] = x;
            if (ink[y * n + x] && (x == n - 1 || !ink[y * n + x + 1])) { r[k + 1] = x; k += 2; }
        }
        return r;
    }

    private static void seed(boolean[] ink, boolean[] seen, ArrayDeque<Integer> q, int x, int y, int n) {
        if (x < 0 || y < 0 || x >= n || y >= n) return;
        int p = y * n + x;
        if (ink[p] || seen[p]) return;
        seen[p] = true;
        q.add(p);
    }
}
