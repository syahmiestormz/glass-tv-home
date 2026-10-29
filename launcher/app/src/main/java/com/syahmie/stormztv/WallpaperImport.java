package com.syahmie.stormztv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pictures dropped into the launcher's import folder become the owner's own wallpapers:
 * {@code /sdcard/Android/data/com.syahmie.stormztv/files/import}, reachable over ADB
 * ({@code adb push my.jpg /sdcard/Android/data/com.syahmie.stormztv/files/import/}). Each one is
 * scaled to at most 1920 wide, saved with the uploads (files/custom_wallpapers) under its own name,
 * so the name can be shown on Home, and removed from the import folder. Nothing leaves the TV.
 */
final class WallpaperImport {

    private static final int MAX_WIDTH = 1920;

    private WallpaperImport() {
    }

    /** The import folder, created if missing so it is there to push into. Null without storage. */
    static File folder(Context context) {
        File dir = context.getExternalFilesDir("import");
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** Imports whatever is waiting. Slow: call off the main thread. Returns the new wallpapers. */
    static List<File> run(Context context) {
        List<File> done = new ArrayList<>();
        File in = folder(context);
        if (in == null) return done;
        File[] files = in.listFiles((dir, name) -> {
            String n = name.toLowerCase(Locale.ROOT);
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
        });
        if (files == null || files.length == 0) return done;
        File out = new File(context.getFilesDir(), "custom_wallpapers");
        if (!out.isDirectory() && !out.mkdirs()) return done;
        for (File f : files) {
            // A file still being copied in is left for the next visit to Home.
            if (System.currentTimeMillis() - f.lastModified() < 3000) continue;
            File saved = save(f, out);
            if (saved != null) {
                done.add(saved);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        return done;
    }

    private static File save(File src, File dir) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeFile(src.getAbsolutePath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (bmp == null) return null;
        if (bmp.getWidth() > MAX_WIDTH) {
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, MAX_WIDTH,
                    Math.round(bmp.getHeight() * (MAX_WIDTH / (float) bmp.getWidth())), true);
            bmp.recycle();
            bmp = scaled;
        }
        File target = new File(dir, safeName(src.getName()) + ".jpg");
        try (FileOutputStream os = new FileOutputStream(target)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return null;
        } finally {
            bmp.recycle();
        }
        return target;
    }

    /** "05 Violet Flame.PNG" → "05_violet_flame": letters, digits and underscores only. */
    static String safeName(String fileName) {
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "wallpaper_" + System.currentTimeMillis();
        if (base.length() > 60) base = base.substring(0, 60);
        return base;
    }

    /**
     * The name to show on Home for one of the owner's pictures, from its file name:
     * "05_violet_flame.jpg" → "Violet Flame". Pictures saved from an upload or a link are named
     * by the time ("custom_1730000000000.jpg") and get none.
     */
    static String title(File picture) {
        if (picture == null) return null;
        String base = picture.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        if (base.matches("(?i)(custom|wallpaper|upload|img|image|photo|screenshot)?[_ -]*\\d*")) return null;
        base = base.replaceFirst("^\\d+[_ -]+", "").replaceAll("[_-]+", " ").trim();
        if (base.length() < 2 || base.matches("\\d+")) return null;
        StringBuilder sb = new StringBuilder(base.length());
        for (String w : base.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
