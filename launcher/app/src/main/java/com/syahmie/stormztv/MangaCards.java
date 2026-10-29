package com.syahmie.stormztv;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words on the Manga Ink cards: a small red label, a big Anton line, a small line under it.
 * Each card is one TextView with spans, so focus and clicks stay as simple as they were.
 */
final class MangaCards {

    private MangaCards() {
    }

    static final int RED = 0xFFFF4B5C;
    static final int CREAM = 0xFFF3EFE6;
    static final int MUTED = 0xCCD8D2C4;

    private static final Pattern TEMP = Pattern.compile("^\\s*(-?\\d+\\s*°[CF]?)\\s*(.*)$");
    private static final String[] WEEKDAYS = {"日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日"};
    private static final String[] DIGITS = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    /** "WEATHER", the temperature large, then the sky and the haze. {@code apiColor} 0 keeps the haze cream. */
    static CharSequence weather(Context ctx, String weather, String api, int apiColor) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        label(sb, "WEATHER");
        String w = weather == null ? "" : weather.trim();
        Matcher m = TEMP.matcher(w);
        String sky = w;
        if (m.matches()) {
            big(ctx, sb, m.group(1).replace(" ", ""), 46);
            sky = m.group(2).trim();
        }
        sb.append('\n');
        int start = sb.length();
        sb.append(sky);
        if (api != null && !api.isEmpty()) {
            if (!sky.isEmpty()) sb.append("  ·  ");
            int a = sb.length();
            sb.append(api);
            if (apiColor != 0) sb.setSpan(new ForegroundColorSpan(apiColor | 0xFF000000), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sb.setSpan(new AbsoluteSizeSpan(14, true), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** "NEXT PRAYER", its name and time large, then the two after it (Sunrise is not a prayer). */
    static CharSequence prayer(Context ctx, PrayerTimes.PrayerItem[] items) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        label(sb, "NEXT PRAYER");
        if (items == null || items.length == 0) {
            sb.append("Set your location in Settings");
            return sb;
        }
        int next = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i].isNext) {
                next = i;
                break;
            }
        }
        big(ctx, sb, items[next].name.toUpperCase(Locale.ROOT), 30);
        sb.append('\n');
        big(ctx, sb, items[next].timeStr, 30);
        StringBuilder after = new StringBuilder();
        int shown = 0;
        for (int k = 1; k < items.length && shown < 2; k++) {
            PrayerTimes.PrayerItem it = items[(next + k) % items.length];
            if ("Sunrise".equalsIgnoreCase(it.name)) continue;
            if (after.length() > 0) after.append("  ·  ");
            after.append(it.name).append(' ').append(it.timeStr);
            shown++;
        }
        if (after.length() > 0) {
            sb.append('\n');
            int s = sb.length();
            sb.append(after);
            sb.setSpan(new AbsoluteSizeSpan(12, true), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(MUTED), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    /** 0 = Sunday, as Calendar.DAY_OF_WEEK - 1. */
    static String weekdayKanji(int dow) {
        return dow >= 0 && dow < 7 ? WEEKDAYS[dow] : "";
    }

    /**
     * The line over one of the owner's pictures: its number as an episode, "05_violet_flame.jpg" →
     * "第五話 · EPISODE 05". Pictures with no number are simply "壁紙 · MY WALLPAPER".
     */
    static String episode(File picture) {
        if (picture == null) return "壁紙  ·  MY WALLPAPER";
        Matcher m = Pattern.compile("^(\\d{1,3})[_ -]").matcher(picture.getName());
        if (!m.find()) return "壁紙  ·  MY WALLPAPER";
        String digits = m.group(1);
        int n = Integer.parseInt(digits);
        if (n <= 0) return "壁紙  ·  MY WALLPAPER";
        String num = digits.length() < 2 ? "0" + digits : digits;
        return "第" + kanji(n) + "話  ·  EPISODE " + num;
    }

    /** 1 to 999 in kanji: 5 → 五, 12 → 十二, 25 → 二十五, 100 → 百. */
    static String kanji(int n) {
        if (n <= 0 || n > 999) return String.valueOf(n);
        StringBuilder sb = new StringBuilder();
        int h = n / 100, t = (n / 10) % 10, o = n % 10;
        if (h > 0) sb.append(h > 1 ? DIGITS[h] : "").append('百');
        if (t > 0) sb.append(t > 1 ? DIGITS[t] : "").append('十');
        if (o > 0) sb.append(DIGITS[o]);
        return sb.toString();
    }

    private static void label(SpannableStringBuilder sb, String text) {
        int s = sb.length();
        sb.append(text).append('\n');
        sb.setSpan(new AbsoluteSizeSpan(11, true), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(RED), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new FaceSpan(Typeface.DEFAULT_BOLD), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void big(Context ctx, SpannableStringBuilder sb, String text, int sp) {
        int s = sb.length();
        sb.append(text);
        sb.setSpan(new AbsoluteSizeSpan(sp, true), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(CREAM), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Typeface anton = display(ctx);
        if (anton != null) sb.setSpan(new FaceSpan(anton), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static Typeface displayFace;

    /** Anton, loaded once. Null if it cannot be read, and the text falls back to the card's font. */
    static Typeface display(Context ctx) {
        if (displayFace == null) {
            try {
                displayFace = ctx.getResources().getFont(R.font.display);
            } catch (Exception e) {
                return null;
            }
        }
        return displayFace;
    }

    /** A typeface span that works before Android 9 (TypefaceSpan(Typeface) needs API 28). */
    private static final class FaceSpan extends MetricAffectingSpan {
        private final Typeface face;

        FaceSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setTypeface(face);
        }

        @Override
        public void updateMeasureState(TextPaint tp) {
            tp.setTypeface(face);
        }
    }
}
