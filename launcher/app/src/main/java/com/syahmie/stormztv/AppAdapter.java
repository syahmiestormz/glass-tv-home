package com.syahmie.stormztv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the dock: the apps the owner pinned there
 * (hold OK on an app, then Add to dock), in their order, and an empty "+" slot for every free place, which
 * opens a picker. On the first start the dock is seeded from config.xml / the theme's dock_apps. After the
 * dock come the module cards the owner chose to show (by label), and the rest is alphabetical. Once anyone has
 * moved a tile (hold OK, then Move), the order they made is kept instead, and apps installed later
 * join at the end.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        /** OK was held on a tile. Return false to open the app's info page, as before. */
        boolean onTileHeld(int position, View card);

        /** An empty dock slot was clicked: offer the apps that can be pinned there. */
        void onAddSlotClicked();
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, ADD_SLOT = 3;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.07f;
    /** Shared by every tile's zoom, so a focus move makes no new objects. */
    private static final DecelerateInterpolator ZOOM_REDUCED = new DecelerateInterpolator();
    private static final OvershootInterpolator ZOOM_IN = new OvershootInterpolator(1.6f);
    private static final OvershootInterpolator ZOOM_OUT = new OvershootInterpolator(0f);
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 18 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    private final List<Entry> items = new ArrayList<>();
    /** The order someone arranged the tiles in, by key (see {@link #keyAt}); empty until they do. */
    private final List<String> arranged = new ArrayList<>();
    private static final String ORDER_PREFS = "tiles", ORDER_KEY = "order";
    /** Packages pinned to the dock, in dock order. Seeded once from dock_apps, then the owner's own. */
    private final List<String> pinned = new ArrayList<>();
    private boolean pinnedSeeded;
    private static final String DOCK_PREFS = "dock", DOCK_KEY = "pinned";
    private static final String ADD_SLOT_CLASS = "dock.add-slot-";
    /** Rebind payload: only whether the tile's name shows, which depends on being in the dock. */
    private static final Object DOCK_CHANGED = new Object();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
        String saved = ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).getString(ORDER_KEY, "");
        if (!saved.isEmpty()) arranged.addAll(Arrays.asList(saved.split("\n")));
        SharedPreferences dockPrefs = ctx.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE);
        pinnedSeeded = dockPrefs.contains(DOCK_KEY);
        String dock = dockPrefs.getString(DOCK_KEY, "");
        if (!dock.isEmpty()) pinned.addAll(Arrays.asList(dock.split("\n")));
    }

    private void savePinned() {
        ctx.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE).edit()
                .putString(DOCK_KEY, android.text.TextUtils.join("\n", pinned)).apply();
    }

    private Entry addSlot(int index) {
        return new Entry(Entry.ADD_SLOT, ctx.getString(R.string.dock_add_slot),
                new ComponentName(ctx.getPackageName(), ADD_SLOT_CLASS + index), null,
                ctx.getDrawable(R.drawable.ic_dock_add), null, 0xFF00E5FF);
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    /** What the tiles were last built from; the same list next time means there is nothing to redo. */
    private String builtFrom;
    /**
     * The app tiles of the last build, by component and install path: a rebuild (one app updated,
     * installed or removed) loads only what changed, and the rest keep their artwork and sampled glow.
     */
    private Map<String, Entry> builtApps = new HashMap<>();

    /**
     * Rebuilds the tiles, unless the launchable apps, their installed versions and the cards are
     * exactly what the tiles were last built from. Loading every banner, icon and label is most of
     * what coming back to Home costs, it runs before Home can draw, and almost every return finds
     * nothing changed.
     *
     * @return whether the tiles were rebuilt
     */
    boolean reload(List<Cards.Card> cards) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(probe, 0));
        // A phone-only app named in dock_apps (e.g. the phone Play Store some boxes ship) has no leanback
        // entry; take its ordinary launcher activity instead. Apps not named stay hidden.
        Set<String> listed = new HashSet<>();
        for (ResolveInfo ri : found) if (ri.activityInfo != null) listed.add(ri.activityInfo.packageName);
        for (String pkg : favourites) {
            if (listed.contains(pkg)) continue;
            Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
            List<ResolveInfo> more = pm.queryIntentActivities(phone, 0);
            if (!more.isEmpty() && more.get(0).activityInfo != null) {
                found.add(more.get(0));
                listed.add(pkg);
            }
        }
        // The install path moves when an app is updated, so it stands in for the version: an update
        // that brought a new banner still gets a rebuild.
        List<String> parts = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            parts.add(ri.activityInfo.packageName + '/' + ri.activityInfo.name + '@'
                    + ri.activityInfo.applicationInfo.sourceDir);
        }
        for (Cards.Card c : cards) {
            parts.add('#' + c.pkg + '/' + c.component.flattenToShortString() + '|' + c.label + '|'
                    + c.caption + '|' + c.accent);
        }
        Collections.sort(parts);
        String signature = android.text.TextUtils.join(";", parts);
        if (signature.equals(builtFrom) && !items.isEmpty()) return false;
        builtFrom = signature;
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        Map<String, Entry> apps = new HashMap<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            String key = component.flattenToShortString() + '@' + ri.activityInfo.applicationInfo.sourceDir;
            Entry built = card == null ? builtApps.get(key) : null;
            if (built != null) {
                fresh.add(built);
                apps.put(key, built);
                continue;
            }
            Drawable banner = loadBanner(pm, ri);
            // The icon only shows on a tile without a banner.
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            if (card != null) {
                fresh.add(new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent));
            } else {
                // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
                // redraw the tile name as something else (#117).
                Entry entry = new Entry(Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg), component, banner, icon);
                fresh.add(entry);
                apps.put(key, entry);
            }
        }
        builtApps = apps;
        Map<String, Integer> place = new HashMap<>();
        for (int i = 0; i < arranged.size(); i++) place.put(arranged.get(i), i);
        Collections.sort(fresh, (a, b) -> {
            // The order someone arranged comes first; apps it has not seen yet go after it.
            Integer pa = place.get(a.component.flattenToShortString()), pb = place.get(b.component.flattenToShortString());
            if (pa != null || pb != null) {
                if (pa == null) return 1;
                if (pb == null) return -1;
                return Integer.compare(pa, pb);
            }
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // The dock: pinned apps first, in the owner's order, then a "+" slot for every free place, so the
        // grid below always starts on its own row.
        if (!pinnedSeeded) {
            for (String pkg : favourites) {
                if (pinned.size() >= dockSize) break;
                if (pinned.contains(pkg)) continue;
                for (Entry e : fresh) {
                    if (e.pkg.equals(pkg)) {
                        pinned.add(pkg);
                        break;
                    }
                }
            }
            pinnedSeeded = true;
            savePinned();
        }
        List<Entry> dock = new ArrayList<>();
        for (String pkg : pinned) {
            if (dock.size() >= dockSize) break;
            for (Entry e : fresh) {
                if (e.pkg.equals(pkg)) {
                    dock.add(e);
                    break;
                }
            }
        }
        fresh.removeAll(dock);
        for (int i = dock.size(); i < dockSize; i++) dock.add(addSlot(i));
        fresh.addAll(0, dock);
        // Optional "Default home" tile (config.xml), only when this TV actually has a home-screen picker.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
        return true;
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return ri.activityInfo.loadBanner(pm);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, reducedMotion);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.name);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        h.card.setBackgroundResource(e.kind == Entry.ADD_SLOT ? R.drawable.tile_add_bg : R.drawable.tile_bg);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION && listener.onTileHeld(pos, v)) return true;
            openAppInfo(e);
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.size() == 1 && payloads.get(0) == DOCK_CHANGED) {
            h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);
            return;
        }
        onBindViewHolder(h, position);
    }

    /** Android's page for the app at {@code position}: storage, permissions, uninstall. */
    void openAppInfo(int position) {
        if (position >= 0 && position < items.size()) openAppInfo(items.get(position));
    }

    private void openAppInfo(Entry e) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Whether the tile at {@code position} can be moved: every app can, the Default home tile and empty dock slots cannot. */
    boolean canMove(int position) {
        if (position < 0 || position >= items.size()) return false;
        int kind = items.get(position).kind;
        return kind != Entry.HOME_SETTINGS && kind != Entry.ADD_SLOT;
    }

    /** Whether {@code position} is one of the dock's places (an app or an empty slot). */
    boolean isDock(int position) {
        return position >= 0 && position < dockSize && position < items.size();
    }

    /** Whether {@code position} is an empty dock slot. */
    boolean isAddSlot(int position) {
        return position >= 0 && position < items.size() && items.get(position).kind == Entry.ADD_SLOT;
    }

    /** Whether {@code position} is an app pinned to the dock. */
    boolean isPinned(int position) {
        return isDock(position) && !isAddSlot(position);
    }

    /** Whether every dock place holds an app. */
    boolean isDockFull() {
        for (int i = 0; i < dockSize && i < items.size(); i++) if (items.get(i).kind == Entry.ADD_SLOT) return false;
        return true;
    }

    /** First empty dock slot, or -1. */
    int firstAddSlot() {
        for (int i = 0; i < dockSize && i < items.size(); i++) if (items.get(i).kind == Entry.ADD_SLOT) return i;
        return -1;
    }

    /**
     * Pins the app with this key to the first free dock place. The tiles must be rebuilt afterwards
     * (MainActivity reloads them). Returns false when the dock is full or the key is not an app.
     */
    boolean pin(String key) {
        int pos = positionOf(key);
        if (pos < 0 || isDock(pos) || !canMove(pos) || isDockFull()) return false;
        String pkg = items.get(pos).pkg;
        pinned.remove(pkg);
        // Placed after the pinned apps that are installed, so it lands in the first free slot.
        int at = 0;
        for (int i = 0; i < dockSize && i < items.size(); i++) {
            if (items.get(i).kind == Entry.ADD_SLOT) break;
            int idx = pinned.indexOf(items.get(i).pkg);
            if (idx >= 0) at = idx + 1;
        }
        pinned.add(Math.min(at, pinned.size()), pkg);
        savePinned();
        builtFrom = null;
        return true;
    }

    /** Takes the dock app at {@code position} out of the dock; it goes back to the grid. Rebuild afterwards. */
    boolean unpin(int position) {
        if (!isPinned(position)) return false;
        pinned.remove(items.get(position).pkg);
        savePinned();
        builtFrom = null;
        return true;
    }

    /** Apps not in the dock that can be pinned: {keys, labels}, in grid order. */
    String[][] undockedApps() {
        List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        for (int i = dockSize; i < items.size(); i++) {
            Entry e = items.get(i);
            if (e.kind != Entry.APP && e.kind != Entry.CARD) continue;
            keys.add(e.component.flattenToShortString());
            labels.add(e.label);
        }
        return new String[][]{keys.toArray(new String[0]), labels.toArray(new String[0])};
    }

    /** The last position a moved tile can take: before the Default home tile, which stays last. */
    int lastMovable() {
        int last = items.size() - 1;
        while (last >= 0 && items.get(last).kind == Entry.HOME_SETTINGS) last--;
        return last;
    }

    /**
     * Moves the tile at {@code from} to {@code to}, the tiles between shifting to make room, and keeps
     * the new order. Returns false when it cannot go there.
     */
    boolean move(int from, int to) {
        if (from == to || !canMove(from) || to < 0 || to > lastMovable()) return false;
        // Dock apps move along the dock and grid apps within the grid; Add to dock / Remove from dock
        // cross between them. A dock app cannot be put on an empty slot (it would leave a gap).
        boolean fromDock = from < dockSize;
        if (fromDock != (to < dockSize)) return false;
        if (fromDock && !canMove(to)) return false;
        Entry e = items.remove(from);
        items.add(to, e);
        notifyItemMoved(from, to);
        // A tile crossing into or out of the dock shows or hides its name.
        int lo = Math.min(from, to), hi = Math.max(from, to);
        if (lo < dockSize && hi >= dockSize) notifyItemRangeChanged(lo, hi - lo + 1, DOCK_CHANGED);
        if (fromDock) {
            List<String> order = new ArrayList<>();
            for (int i = 0; i < dockSize && i < items.size(); i++) {
                if (items.get(i).kind != Entry.ADD_SLOT) order.add(items.get(i).pkg);
            }
            // Pinned apps that are not installed right now keep their place at the end.
            for (String pkg : pinned) if (!order.contains(pkg)) order.add(pkg);
            pinned.clear();
            pinned.addAll(order);
            savePinned();
            return true;
        }
        arranged.clear();
        for (Entry x : items) {
            if (x.kind != Entry.HOME_SETTINGS && x.kind != Entry.ADD_SLOT) arranged.add(x.component.flattenToShortString());
        }
        ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(ORDER_KEY, android.text.TextUtils.join("\n", arranged)).apply();
        return true;
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        SoundEffects.playSelect();
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        if (e.kind == Entry.ADD_SLOT) {
            if (listener != null) listener.onAddSlotClicked();
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            RecentAppsManager.recordAppLaunch(ctx, e.pkg);
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 10dp at the design width, less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 10f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final boolean reducedMotion;

        Holder(View v, TileListener listener, boolean reducedMotion) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            this.reducedMotion = reducedMotion;

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 6dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                view.animate().cancel();
                name.animate().cancel();
                if (reducedMotion) {
                    // No overshoot: the extra frames redraw the banner and the dock band on a weak GPU.
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 120 : 80)
                            .setInterpolator(ZOOM_REDUCED)
                            .withLayer()
                            .start();
                    name.animate().alpha(focused ? 1f : 0f).setDuration(100).start();
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(focused ? ZOOM_IN : ZOOM_OUT)
                            .withLayer()
                            .start();
                    name.animate().alpha(focused ? 1f : 0f)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
