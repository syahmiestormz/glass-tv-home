package com.syahmie.stormztv;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Stormz TV's own neon-anime night scenes, drawn by tools/neon_art.py and shipped in assets/neon. The
 * default wallpaper collection: no network, no outside artwork. On first use each picture is copied to
 * files/neon_wallpapers with a caption file beside it in the format OnlineWallpapers.describe reads
 * (title, caption, subtitle), so the top shelf names it like any other picture.
 */
final class NeonWallpapers {

    static final String CATEGORY = "Neon";
    private static final String TAG = "TvHome.Neon";

    /** File name, title, caption, Japanese subtitle shown where a place shows its country. */
    private static final String[][] ART = {
            {"01_neon_skyline", "Neon Skyline", "A city that never sleeps, lit pink and cyan under a full moon", "ネオン・シティ  //  NEON CITY"},
            {"02_synth_sunset", "Synth Sunset", "A striped sun sinking behind violet peaks onto an endless grid", "シンセ・サンセット  //  SYNTH SUNSET"},
            {"03_sakura_night", "Sakura Night", "Cherry blossoms and a quiet shrine gate under the moon", "夜桜  //  YOZAKURA"},
            {"04_cyber_rain", "Cyber Rain", "Rain falling through a neon alley, the signs mirrored in the street", "サイバー・レイン  //  CYBER RAIN"},
    };

    private NeonWallpapers() {}

    static File dir(Context ctx) {
        return new File(ctx.getFilesDir(), "neon_wallpapers");
    }

    /** The neon pictures, copied out of the APK the first time. Small disk work: about 500 KB, once. */
    static List<File> list(Context ctx) {
        File d = dir(ctx);
        if (!d.isDirectory() && !d.mkdirs()) return new ArrayList<>();
        List<File> out = new ArrayList<>();
        for (String[] a : ART) {
            File pic = new File(d, a[0] + ".jpg");
            if (!pic.isFile() || pic.length() == 0) copyAsset(ctx, "neon/" + a[0] + ".jpg", pic);
            File txt = new File(d, a[0] + ".txt");
            if (!txt.isFile() || txt.length() == 0) write(txt, a[1] + "\n" + a[2] + "\n" + a[3] + "\n");
            if (pic.isFile() && pic.length() > 0) out.add(pic);
        }
        return out;
    }

    static boolean isNeon(File f) {
        return f != null && f.getAbsolutePath().contains("neon_wallpapers");
    }

    private static void copyAsset(Context ctx, String asset, File to) {
        File tmp = new File(to.getParentFile(), to.getName() + ".part");
        try (InputStream in = ctx.getAssets().open(asset); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            Log.w(TAG, "copy " + asset + " failed: " + e.getMessage());
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(to)) tmp.delete();
    }

    private static void write(File f, String text) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "caption " + f.getName() + " failed: " + e.getMessage());
        }
    }
}
