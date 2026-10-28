package com.syahmie.stormztv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * The Stormz TV look: a sci-fi heads-up display. Deep navy panels instead of frosted glass, thin cyan
 * rims, and corner brackets around whatever is selected. Everything here is plain fills and strokes, so
 * it costs the GPU less than the blurred glass it replaces (the Onn and other 2 GB boxes notice).
 */
final class Hud {

    private Hud() {
    }

    /** The accent: an electric cyan. */
    static final int CYAN = 0xFF00E5FF;
    /** A warm second accent for warnings and the active prayer, amber. */
    static final int AMBER = 0xFFFFB020;
    /** Rim of a panel: bright at the top-left and bottom-right, faint at the other corners (GlassRim). */
    static final int RIM_SHINE = 0xE600E5FF, RIM_FAINT = 0x2600E5FF;
    /** Panel fill: deep navy, see-through enough that the wallpaper still reads behind it. */
    static final int PANEL = 0xC0060E1A;
    /** The top menu's focus fill, a darker navy. */
    static final int FOCUS_FILL = 0xE0040A14;

    /** A cyan stroke for brackets and accent lines. */
    static Paint stroke(float widthPx, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(widthPx);
        p.setStrokeCap(Paint.Cap.SQUARE);
        p.setColor(color);
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    /**
     * Four corner brackets just outside {@code r}: the targeting frame of the HUD. {@code gap} pushes them
     * out from the shape, {@code len} is the length of each arm. Allocates nothing.
     */
    static void brackets(Canvas c, RectF r, float gap, float len, Paint p) {
        float l = r.left - gap, t = r.top - gap, rt = r.right + gap, b = r.bottom + gap;
        float arm = Math.min(len, Math.min(rt - l, b - t) / 3f);
        // top-left
        c.drawLine(l, t, l + arm, t, p);
        c.drawLine(l, t, l, t + arm, p);
        // top-right
        c.drawLine(rt - arm, t, rt, t, p);
        c.drawLine(rt, t, rt, t + arm, p);
        // bottom-left
        c.drawLine(l, b, l + arm, b, p);
        c.drawLine(l, b - arm, l, b, p);
        // bottom-right
        c.drawLine(rt - arm, b, rt, b, p);
        c.drawLine(rt, b - arm, rt, b, p);
    }

    /** A short row of tick marks along the top edge of a panel, like an instrument scale. */
    static void ticks(Canvas c, RectF r, float step, float h, Paint p) {
        if (step <= 0f) return;
        float y = r.top;
        float start = r.left + step * 2f, end = r.right - step * 2f;
        int n = 0;
        for (float x = start; x <= end && n < 400; x += step, n++) {
            c.drawLine(x, y, x, y + (n % 5 == 0 ? h * 1.8f : h), p);
        }
    }
}
