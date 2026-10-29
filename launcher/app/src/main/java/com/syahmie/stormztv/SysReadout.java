package com.syahmie.stormztv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/**
 * The system readout next to the weather, kept short so it never reaches the verse on the right
 * ("RAM 65%  ·  2.0 GB free"): memory in use, storage left and, where the box lets an app
 * read it, the chip temperature. Read only while Home is on screen (once a minute with the clock):
 * a few small reads, nothing kept running.
 */
final class SysReadout {

    private SysReadout() {}

    static String line(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.totalMem > 0) {
                int used = Math.round(100f * (mi.totalMem - mi.availMem) / mi.totalMem);
                sb.append("RAM ").append(used).append('%');
            }
        } catch (Exception ignored) {
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            float free = fs.getAvailableBytes() / 1073741824f;
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(String.format(Locale.ROOT, "%.1f GB free", free));
        } catch (Exception ignored) {
        }
        int temp = chipTemp();
        if (temp > 0) sb.append("  ·  ").append(temp).append("°C");
        return sb.toString();
    }

    /** Degrees C from the first thermal zone, or -1 where the box keeps it private (most do). */
    private static int chipTemp() {
        try (BufferedReader r = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone0/temp"))) {
            int v = Integer.parseInt(r.readLine().trim());
            return v > 1000 ? v / 1000 : v;
        } catch (Exception e) {
            return -1;
        }
    }
}
