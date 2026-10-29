package com.syahmie.stormztv;

import android.graphics.Paint;

/**
 * The Stormz TV look, Manga Ink (dark): near-black screentone paper, dark panels drawn with a cream
 * ink line, and red for whatever is selected, as a fill or a hard shadow. Plain fills and strokes
 * only: cheaper for the GPU than blurred glass (the Onn and other 2 GB boxes notice).
 */
final class Hud {

    private Hud() {
    }

    /** The paper. */
    static final int PAPER = 0xFF0C0C0E;
    /** Panel faces. */
    static final int CARD = 0xFF18181C;
    /** The ink line. */
    static final int CREAM = 0xFFF3EFE6;
    /** Selection: fills and hard shadows. */
    static final int RED = 0xFFD62839;
    /** Red for text on dark panels, a touch lighter so it reads. */
    static final int RED_TEXT = 0xFFFF4B5C;
    /** A warm second colour for warnings, amber. */
    static final int AMBER = 0xFFFFB020;
    /** Edge of a panel: the cream ink line, the same all round (GlassRim with equal ends). */
    static final int RIM_SHINE = CREAM, RIM_FAINT = CREAM;
    /** Panel fill: a dark face, nearly opaque. */
    static final int PANEL = 0xF218181C;
    /** The top menu's focus fill: red. */
    static final int FOCUS_FILL = RED;

    /** The accent for text (labels, the selected row's title). */
    static int accent() {
        return RED_TEXT;
    }

    static Paint stroke(float widthPx, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(widthPx);
        p.setColor(color);
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }
}
