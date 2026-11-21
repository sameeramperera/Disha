package com.disha.ui;

import com.disha.bike.TvsProtocol;
import com.disha.maps.MapsParser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Draws a TVS pictogram (turn arrow, roundabout, U-turn, flag ...) with vector paths, no images. */
public final class ArrowView extends View {
    private int picto = -1;
    private int color = 0xFFFFFFFF, dimColor = 0x40FFFFFF;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            dim = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ArrowView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        dim.setStyle(Paint.Style.STROKE);
        dim.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setPicto(int p) {
        if (p != picto) { picto = p; invalidate(); }
    }

    public void setColors(int main, int faint) {
        color = main; dimColor = faint; invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
        c.save();
        c.translate(ox, oy);
        c.scale(s / 100f, s / 100f); // draw in a 100 x 100 box
        stroke.setColor(color); fill.setColor(color); dim.setColor(dimColor);
        stroke.setStrokeWidth(11); dim.setStrokeWidth(7);

        switch (picto) {
            case TvsProtocol.P_STRAIGHT: straight(c); break;
            case TvsProtocol.P_LEFT: turn(c, 60, 180); break;
            case TvsProtocol.P_RIGHT: turn(c, 40, 0); break;
            case TvsProtocol.P_SLIGHT_LEFT: case TvsProtocol.P_KEEP_LEFT: case TvsProtocol.P_LEAVE_HWY_LEFT:
                turn(c, 58, 135); break;
            case TvsProtocol.P_SLIGHT_RIGHT: case TvsProtocol.P_KEEP_RIGHT: case TvsProtocol.P_LEAVE_HWY_RIGHT:
            case TvsProtocol.P_ENTER_HWY_RIGHT:
                turn(c, 42, 45); break;
            case TvsProtocol.P_SHARP_LEFT: turn(c, 78, 225, 24, 34); break;
            case TvsProtocol.P_SHARP_RIGHT: turn(c, 22, -45, 24, 34); break;
            case TvsProtocol.P_UTURN_LEFT: uturn(c, true); break;
            case TvsProtocol.P_UTURN_RIGHT: uturn(c, false); break;
            case TvsProtocol.P_RB_SHARP_LEFT: case TvsProtocol.P_RB_LEFT: case TvsProtocol.P_RB_SLIGHT_LEFT:
            case TvsProtocol.P_RB_STRAIGHT: case TvsProtocol.P_RB_SLIGHT_RIGHT: case TvsProtocol.P_RB_RIGHT:
            case TvsProtocol.P_RB_SHARP_RIGHT:
                roundabout(c, TvsProtocol.roundaboutAngle(picto), MapsParser.leftHandTraffic); break;
            case TvsProtocol.P_ARRIVE: flag(c); break;
            case TvsProtocol.P_REROUTING: reroute(c); break;
            default: idle(c); break;
        }
        c.restore();
    }

    private void straight(Canvas c) {
        Path p = new Path();
        p.moveTo(50, 92); p.lineTo(50, 34);
        c.drawPath(p, stroke);
        head(c, 50, 12, 90);
    }

    /** Stem up from the bottom, then a leg in direction angle (0 = right, 90 = up, 180 = left) and a head. */
    private void turn(Canvas c, float stemX, float angle) {
        turn(c, stemX, angle, 50, angle == 0 || angle == 180 ? 24 : 22);
    }

    private void turn(Canvas c, float stemX, float angle, float bendY, float len) {
        Path p = new Path();
        p.moveTo(stemX, 92);
        p.lineTo(stemX, bendY);
        double r = Math.toRadians(angle);
        float ex = (float) (stemX + Math.cos(r) * len), ey = (float) (bendY - Math.sin(r) * len);
        p.lineTo(ex, ey);
        c.drawPath(p, stroke);
        head(c, (float) (ex + Math.cos(r) * 20), (float) (ey - Math.sin(r) * 20), angle);
    }

    /** Filled triangle whose tip is at (tx, ty), pointing in direction angle. */
    private void head(Canvas c, float tx, float ty, float angle) {
        double r = Math.toRadians(angle);
        float dx = (float) Math.cos(r), dy = (float) -Math.sin(r);
        float bx = tx - dx * 22, by = ty - dy * 22;   // base centre
        float nx = -dy, ny = dx;                      // normal
        Path h = new Path();
        h.moveTo(tx, ty);
        h.lineTo(bx + nx * 16, by + ny * 16);
        h.lineTo(bx - nx * 16, by - ny * 16);
        h.close();
        c.drawPath(h, fill);
    }

    private void uturn(Canvas c, boolean left) {
        float a = left ? 66 : 34, b = left ? 34 : 66; // stem x, return x
        Path p = new Path();
        p.moveTo(a, 92);
        p.lineTo(a, 40);
        RectF arc = new RectF(Math.min(a, b), 24, Math.max(a, b), 56);
        p.arcTo(arc, left ? 0 : 180, left ? -180 : 180);
        p.lineTo(b, 56);
        c.drawPath(p, stroke);
        head(c, b, 78, 270);
    }

    /** Ring with entry from the bottom and an exit arm at exitAngle; the driven part is solid. */
    private void roundabout(Canvas c, float exitAngle, boolean clockwise) {
        float cx = 50, cy = 46, r = 17;
        RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
        c.drawOval(ring, dim);
        Path p = new Path();
        p.moveTo(cx, 94);
        p.lineTo(cx, cy + r);
        // Android angles: 0 = right, 90 = down (clockwise). Entry point is at 90.
        float exitAndroid = -exitAngle;
        float sweep = clockwise ? (exitAndroid - 90 + 360) % 360 : -((90 - exitAndroid + 360) % 360);
        if (sweep == 0) sweep = clockwise ? 360 : -360;
        p.arcTo(ring, 90, sweep);
        double er = Math.toRadians(exitAngle);
        float sx = (float) (cx + Math.cos(er) * r), sy = (float) (cy - Math.sin(er) * r);
        float ex = (float) (cx + Math.cos(er) * (r + 12)), ey = (float) (cy - Math.sin(er) * (r + 12));
        p.lineTo(sx, sy);
        p.lineTo(ex, ey);
        c.drawPath(p, stroke);
        head(c, (float) (ex + Math.cos(er) * 18), (float) (ey - Math.sin(er) * 18), exitAngle);
    }

    private void flag(Canvas c) {
        Path pole = new Path();
        pole.moveTo(30, 92); pole.lineTo(30, 12);
        stroke.setStrokeWidth(8);
        c.drawPath(pole, stroke);
        Path f = new Path();
        f.moveTo(34, 14); f.lineTo(78, 26); f.lineTo(34, 50); f.close();
        c.drawPath(f, fill);
    }

    private void reroute(Canvas c) {
        stroke.setStrokeWidth(9);
        RectF o = new RectF(22, 22, 78, 78);
        c.drawArc(o, 200, 250, false, stroke); // ends at the bottom, moving left
        head(c, 28, 78, 180);
    }

    private void idle(Canvas c) {
        // Navigation arrow "compass" mark shown when nothing is being navigated.
        Path p = new Path();
        p.moveTo(50, 12); p.lineTo(80, 86); p.lineTo(50, 70); p.lineTo(20, 86); p.close();
        dim.setStyle(Paint.Style.FILL);
        c.drawPath(p, dim);
        dim.setStyle(Paint.Style.STROKE);
    }
}
