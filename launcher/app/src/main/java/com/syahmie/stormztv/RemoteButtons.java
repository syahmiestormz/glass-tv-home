package com.syahmie.stormztv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

/**
 * The remote's app buttons and the app each one opens, chosen by the owner in Settings → Remote buttons.
 *
 * <p>On the onn. 4K remote: YouTube sends BUTTON_3, Netflix BUTTON_4, Disney+ BUTTON_9 and Paramount+
 * BUTTON_10. YouTube's button was handled by the Google TV home screen, so it does nothing once Stormz TV
 * replaces it; the other three go to the box's own receiver, which opens a fixed app or the Play Store.
 * {@link ButtonService}, an accessibility service the owner turns on once, catches the four before
 * either of those and opens the chosen app instead. A button with no app chosen is left alone.
 */
final class RemoteButtons {

    private static final String TAG = "TvHome.Buttons";
    private static final String PREFS = "remote_buttons";

    static final int[] KEYS = {
            KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_4, KeyEvent.KEYCODE_BUTTON_9, KeyEvent.KEYCODE_BUTTON_10,
    };
    static final String[] NAMES = {"YouTube button", "Netflix button", "Disney+ button", "Paramount+ button"};

    private RemoteButtons() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isAppButton(int keyCode) {
        for (int k : KEYS) if (k == keyCode) return true;
        return false;
    }

    /** The package the button opens, or null when the owner has not chosen one. */
    static String get(Context ctx, int keyCode) {
        return prefs(ctx).getString("k" + keyCode, null);
    }

    static void set(Context ctx, int keyCode, String pkg) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (pkg == null) e.remove("k" + keyCode);
        else e.putString("k" + keyCode, pkg);
        e.apply();
    }

    static boolean hasMapping(Context ctx, int keyCode) {
        return isAppButton(keyCode) && get(ctx, keyCode) != null;
    }

    /** Opens the button's app. Returns false when no app is chosen or it cannot be started. */
    static boolean launch(Context ctx, int keyCode) {
        String pkg = get(ctx, keyCode);
        if (pkg == null) return false;
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Log.w(TAG, pkg + " has no launch intent");
            return false;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            RecentAppsManager.recordAppLaunch(ctx, pkg);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "could not open " + pkg + ": " + e.getMessage());
            return false;
        }
    }

    /** A readable name for the package, or "Not set". */
    static String label(Context ctx, String pkg) {
        if (pkg == null) return "Not set";
        try {
            PackageManager pm = ctx.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Whether the owner has switched on Settings → Accessibility → Stormz TV Buttons. */
    static boolean serviceOn(Context ctx) {
        String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (on == null) return false;
        String me = new ComponentName(ctx, ButtonService.class).flattenToString();
        String meShort = new ComponentName(ctx, ButtonService.class).flattenToShortString();
        for (String s : on.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }
}
