package com.disha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** The app's icons, drawn as 2 px lines on a 24x24 grid (like the design mockups), so no image files. */
final class LineIcon extends View {
    static final int HOME = 0, OVERVIEW = 1, LOG = 2, SETTINGS = 3, SEARCH = 4, SHARE = 5, BIKE = 6,
            FUEL = 7, SEND = 8, CHEVRON = 9, CHECK = 10, TREND_UP = 11, TREND_DOWN = 12,
            CODE = 13, TRASH = 14;

    private int kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final RectF r = new RectF();

    LineIcon(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setColor(color);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setKind(int k) {
        if (k != kind) { kind = k; invalidate(); }
    }

    void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
        c.scale(s, s);
        stroke.setStrokeWidth(2f);
        p.reset();
        switch (kind) {
            case HOME:
                p.moveTo(3, 11); p.lineTo(12, 3.5f); p.lineTo(21, 11); p.lineTo(19, 11); p.lineTo(19, 20);
                p.lineTo(14.5f, 20); p.lineTo(14.5f, 14.5f); p.lineTo(9.5f, 14.5f); p.lineTo(9.5f, 20);
                p.lineTo(5, 20); p.lineTo(5, 11); p.close();
                c.drawPath(p, fill);
                break;
            case OVERVIEW:   // gauge
                r.set(3, 4, 21, 22);
                c.drawArc(r, 150, 240, false, stroke);
                c.drawLine(12, 13, 16.5f, 8.5f, stroke);
                c.drawCircle(12, 13, 1.4f, fill);
                break;
            case LOG:        // page with lines
                r.set(5, 3, 19, 21);
                c.drawRoundRect(r, 2.5f, 2.5f, stroke);
                c.drawLine(9, 8, 15, 8, stroke);
                c.drawLine(9, 12, 15, 12, stroke);
                c.drawLine(9, 16, 12.5f, 16, stroke);
                break;
            case SETTINGS:   // cog: 8 teeth round a ring
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(45 * i, 12, 12);
                    r.set(10.4f, 2.6f, 13.6f, 6.6f);
                    c.drawRoundRect(r, 1f, 1f, fill);
                    c.restore();
                }
                stroke.setStrokeWidth(2.2f);
                c.drawCircle(12, 12, 6.4f, stroke);
                stroke.setStrokeWidth(2f);
                c.drawCircle(12, 12, 2.6f, stroke);
                break;
            case SEARCH:
                c.drawCircle(11, 11, 7, stroke);
                c.drawLine(16.5f, 16.5f, 20, 20, stroke);
                break;
            case SHARE:
                c.drawLine(12, 15, 12, 3, stroke);
                p.moveTo(7, 8); p.lineTo(12, 3); p.lineTo(17, 8);
                p.moveTo(5, 13); p.lineTo(5, 19); p.quadTo(5, 21, 7, 21); p.lineTo(17, 21);
                p.quadTo(19, 21, 19, 19); p.lineTo(19, 13);
                c.drawPath(p, stroke);
                break;
            case BIKE:       // motorbike: wheels, fork and bars, seat and tank, frame
                c.drawCircle(5.5f, 16.5f, 3, stroke);
                c.drawCircle(18.5f, 16.5f, 3, stroke);
                p.moveTo(18.5f, 16.5f); p.lineTo(14.5f, 7.5f);
                p.moveTo(13, 7.5f); p.lineTo(15.5f, 7.5f);
                p.moveTo(5, 11); p.lineTo(11.5f, 11); p.lineTo(13, 9.5f); p.lineTo(15.2f, 9.5f);
                p.moveTo(5.5f, 16.5f); p.lineTo(9, 11);
                p.moveTo(9, 14.5f); p.lineTo(13.5f, 14.5f); p.lineTo(16.3f, 11.5f);
                c.drawPath(p, stroke);
                break;
            case FUEL:       // pump
                p.moveTo(4, 21); p.lineTo(4, 5); p.quadTo(4, 3, 6, 3); p.lineTo(12, 3); p.quadTo(14, 3, 14, 5);
                p.lineTo(14, 21);
                p.moveTo(3, 21); p.lineTo(15, 21);
                p.moveTo(14, 9); p.lineTo(16, 9); p.quadTo(18, 9, 18, 11); p.lineTo(18, 16);
                p.quadTo(18, 17.5f, 19.5f, 17.5f); p.quadTo(21, 17.5f, 21, 16); p.lineTo(21, 8); p.lineTo(18, 5);
                p.moveTo(7, 8); p.lineTo(11, 8);
                c.drawPath(p, stroke);
                break;
            case SEND:       // navigation arrow
                p.moveTo(3, 11); p.lineTo(21, 3); p.lineTo(13, 21); p.lineTo(11, 13); p.close();
                c.drawPath(p, stroke);
                break;
            case CHEVRON:
                p.moveTo(9, 6); p.lineTo(15, 12); p.lineTo(9, 18);
                c.drawPath(p, stroke);
                break;
            case TREND_UP:   // zig-zag line rising to the right, with an arrow head
                p.moveTo(3, 17); p.lineTo(9, 11); p.lineTo(13, 15); p.lineTo(21, 7);
                p.moveTo(15, 7); p.lineTo(21, 7); p.lineTo(21, 13);
                c.drawPath(p, stroke);
                break;
            case TREND_DOWN:
                p.moveTo(3, 7); p.lineTo(9, 13); p.lineTo(13, 9); p.lineTo(21, 17);
                p.moveTo(15, 17); p.lineTo(21, 17); p.lineTo(21, 11);
                c.drawPath(p, stroke);
                break;
            case CODE:       // </>
                p.moveTo(8, 7); p.lineTo(3, 12); p.lineTo(8, 17);
                p.moveTo(16, 7); p.lineTo(21, 12); p.lineTo(16, 17);
                p.moveTo(14, 4); p.lineTo(10, 20);
                c.drawPath(p, stroke);
                break;
            case TRASH:      // bin with a lid
                p.moveTo(4, 7); p.lineTo(20, 7);
                p.moveTo(9, 7); p.lineTo(9, 4); p.lineTo(15, 4); p.lineTo(15, 7);
                p.moveTo(6, 7); p.lineTo(7, 20); p.lineTo(17, 20); p.lineTo(18, 7);
                p.moveTo(10, 11); p.lineTo(10, 16);
                p.moveTo(14, 11); p.lineTo(14, 16);
                c.drawPath(p, stroke);
                break;
            case CHECK:
                stroke.setStrokeWidth(3.2f);
                p.moveTo(5, 12); p.lineTo(10, 17); p.lineTo(20, 7);
                c.drawPath(p, stroke);
                break;
        }
        c.restore();
    }
}
