package com.syahmie.stormztv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-close on Home: a few seconds after Home comes back, the apps left behind in memory (Stremio,
 * a video player, ...) are told to go, so the next app opens with room to spare on a 2 GB box.
 *
 * <p>Uses {@link ActivityManager#killBackgroundProcesses}, which only ends processes that are already in
 * the background: music still playing from a foreground service is left alone, and so is the app the
 * owner is about to open. System apps the box needs (Google Play services, the keyboard, Bluetooth...)
 * are never touched: only apps with a TV launcher entry and apps the owner installed.
 */
final class AutoClose {

    private static final String TAG = "TvHome.AutoClose";
    private static final String PREFS = "auto_close";

    private AutoClose() {}

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true);
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply();
    }

    /** Runs on the caller's thread: call it off the main thread. {@code keep} is never closed. */
    static int run(Context ctx, Set<String> keep) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        PackageManager pm = ctx.getPackageManager();
        Set<String> targets = new HashSet<>();
        List<ResolveInfo> tv = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        for (ResolveInfo ri : tv) if (ri.activityInfo != null) targets.add(ri.activityInfo.packageName);
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) targets.add(ai.packageName);
        }
        targets.remove(ctx.getPackageName());
        targets.remove("com.google.android.gms");
        targets.remove("com.android.vending");
        targets.remove("com.android.tv.settings");
        if (keep != null) targets.removeAll(keep);
        int n = 0;
        for (String pkg : targets) {
            try {
                am.killBackgroundProcesses(pkg);
                n++;
            } catch (Exception e) {
                Log.w(TAG, pkg + ": " + e.getMessage());
            }
        }
        Log.i(TAG, "asked " + n + " background apps to close");
        return n;
    }
}
