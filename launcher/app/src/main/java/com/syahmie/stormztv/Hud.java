package com.syahmie.stormztv;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * The Stormz TV look: wallpaper first. The picture fills the screen; everything over it is near-black
 * and see-through, with thin neutral edges, so the art stays the star. One accent colour, picked from
 * the wallpaper on screen, marks what is selected and the date. Plain fills and strokes only: cheaper
 * for the GPU than blurred glass (the Onn and other 2 GB boxes notice).
 */
final class Hud {

    private Hud() {
    }

    /** The accent used until a wallpaper has given one: a soft crimson-pink. */
    static final int DEFAULT_ACCENT = 0xFFFF5A7A;
    /** A warm second colour for warnings, amber. */
    static final int AMBER = 0xFFFFB020;
    /** Edge of a panel: a faint white, brighter at the top-left and bottom-right (GlassRim). */
    static final int RIM_SHINE = 0x47FFFFFF, RIM_FAINT = 0x14FFFFFF;
    /** Panel fill: OLED black, see-through enough that the wallpaper still reads behind it. */
    static final int PANEL = 0xB0050507;
    /** The top menu's focus fill, a deeper black. */
    static final int FOCUS_FILL = 0xE6000000;

    private static volatile int accent = DEFAULT_ACCENT;

    /** The accent of the wallpaper on screen. */
    static int accent() {
        return accent;
    }

    /** The accent at {@code alpha} (0 to 255). */
    static int accent(int alpha) {
        return (accent & 0x00FFFFFF) | (alpha << 24);
    }

    static void setAccent(int color) {
        accent = color | 0xFF000000;
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

    /**
     * The wallpaper's accent: the hue that is both most common and most vivid among its bright,
     * saturated pixels, lifted so it reads on black. Grey pictures give the default. Runs on a
     * 64-wide copy, off the main thread.
     */
    static int pickAccent(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) return DEFAULT_ACCENT;
        int w = 64, h = Math.max(1, Math.round(64f * bmp.getHeight() / bmp.getWidth()));
        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(bmp, w, h, true);
        } catch (Throwable t) {
            return DEFAULT_ACCENT;
        }
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != bmp) small.recycle();
        final int bins = 24;
        float[] score = new float[bins], sumS = new float[bins], sumV = new float[bins];
        float[] sumX = new float[bins], sumY = new float[bins];
        float[] hsv = new float[3];
        for (int c : px) {
            Color.colorToHSV(c, hsv);
            float s = hsv[1], v = hsv[2];
            if (s < 0.35f || v < 0.30f) continue;
            float weight = s * v * v;
            int b = Math.min(bins - 1, (int) (hsv[0] / 360f * bins));
            score[b] += weight;
            sumS[b] += s * weight;
            sumV[b] += v * weight;
            // Average the hue as a vector, so reds either side of 0° do not cancel out.
            double a = Math.toRadians(hsv[0]);
            sumX[b] += (float) Math.cos(a) * weight;
            sumY[b] += (float) Math.sin(a) * weight;
        }
        int best = -1;
        float bestScore = 0f;
        for (int b = 0; b < bins; b++) {
            // A bin counts with its neighbours, so one hue split over two bins is not overlooked.
            float s = score[b] + 0.5f * (score[(b + 1) % bins] + score[(b + bins - 1) % bins]);
            if (s > bestScore) {
                bestScore = s;
                best = b;
            }
        }
        // Too little colour to go on (under about 2% of a picture at full strength): keep the default.
        if (best < 0 || bestScore < px.length * 0.02f) return DEFAULT_ACCENT;
        float hue = (float) Math.toDegrees(Math.atan2(sumY[best], sumX[best]));
        if (hue < 0) hue += 360f;
        float sat = sumS[best] / score[best];
        hsv[0] = hue;
        hsv[1] = Math.max(0.55f, Math.min(0.85f, sat));
        hsv[2] = 1f;
        // Deep blues and violets are dark on black at full strength: lighten them.
        if (hue >= 200f && hue <= 295f) hsv[1] = Math.min(hsv[1], 0.6f);
        return Color.HSVToColor(hsv);
    }
}
