package com.spidertv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spider theme Home (layout activity_main_spider): binds the installed apps to the hanging row, the
 * title to the centre app, the greeting to the time of day, and the status pill to the same actions
 * as the tvOS header. SpiderHomeActivity owns the lifecycle and hands the pill actions in.
 */
final class SpiderHomeController implements SpiderCarousel.Listener {

    interface Actions {
        void inputs();

        void bluetooth();

        void network();

        void settings();

        void settingsMenu();

        void launcherSettings();
    }

    /** Design-only views in the layout: the static preview row, replaced at runtime by the live carousel. */
    private static final int[] PREVIEW = {
            R.id.spider_thread_spotify, R.id.spider_tile_spotify_shadow, R.id.spider_tile_spotify,
            R.id.spider_thread_vlc, R.id.spider_tile_vlc_shadow, R.id.spider_tile_vlc,
            R.id.spider_tile_youtube_glow, R.id.spider_tile_youtube,
            R.id.spider_thread_camera, R.id.spider_tile_camera_shadow, R.id.spider_tile_camera,
            R.id.spider_thread_appstore, R.id.spider_tile_appstore_shadow, R.id.spider_tile_appstore,
            R.id.spider_thread_hero, R.id.spider_hero, R.id.spider_rope};

    /** Remembered across activity recreation, like the tvOS grid's focus. */
    private static String lastFocusKey;

    private final Activity act;
    private final SpiderPrefs cfg;
    private final Actions actions;
    private final Set<String> hidden;
    private final FrameLayout root;
    private final SpiderCarousel row;
    private final TextView title, greeting;
    private final View btnInputs, btnWifi, btnBluetooth, btnSettings;

    SpiderHomeController(Activity act, SpiderPrefs cfg, Actions actions) {
        this.act = act;
        this.cfg = cfg;
        this.actions = actions;
        this.hidden = new HashSet<>(cfg.hiddenPackages);
        root = act.findViewById(R.id.spiderRoot);
        title = act.findViewById(R.id.spiderHeroName);
        greeting = act.findViewById(R.id.spiderGreeting);
        btnInputs = act.findViewById(R.id.spiderSourcesPill);
        btnWifi = act.findViewById(R.id.spiderNetworkPill);
        btnBluetooth = act.findViewById(R.id.btnBluetooth);
        btnSettings = act.findViewById(R.id.spiderSettingsPill);
        FlipClockView clock = act.findViewById(R.id.flipClock);
        clock.setAnimations(cfg.motionOn && !cfg.motionGentle);

        // The live row takes the preview's place in the drawing order: above the backdrop, under the text and pill.
        for (int id : PREVIEW) {
            View v = act.findViewById(id);
            if (v != null) root.removeView(v);
        }
        // Read after the preview is gone: the first view after it (the flip clock) is where the row goes.
        int at = root.indexOfChild(act.findViewById(R.id.flipClock));
        row = new SpiderCarousel(act);
        row.setListener(this);
        row.setMotion(cfg.motionOn, cfg.motionGentle);
        root.addView(row, Math.max(0, at), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btnInputs.setOnClickListener(v -> actions.inputs());
        btnWifi.setOnClickListener(v -> actions.network());
        btnBluetooth.setOnClickListener(v -> actions.bluetooth());
        btnSettings.setOnClickListener(v -> actions.settings());
        btnSettings.setOnLongClickListener(v -> {
            actions.settingsMenu();
            return true;
        });
        View[] pill = {btnInputs, btnWifi, btnBluetooth, btnSettings};
        for (View b : pill) {
            b.setFocusable(true);   // TextViews: reachable with D-pad Up from the row
            b.setOnFocusChangeListener((v, focused) -> {
                ((TextView) v).setTextColor(focused ? 0xFFFFFFFF : act.getColor(R.color.spider_text_pill));
                v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(140).start();
            });
            b.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    row.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v == firstVisible(pill);
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == pill[pill.length - 1];
            });
        }
    }

    void reload() {
        String key = lastFocusKey;
        SpiderCarousel.Item now = row.focused();
        if (now != null) key = now.key();
        row.setItems(loadApps(), key);
    }

    void onResume(boolean rebuild) {
        if (rebuild) reload();
        updateGreeting();
        if (!btnInputs.hasFocus() && !btnWifi.hasFocus() && !btnBluetooth.hasFocus() && !btnSettings.hasFocus()) row.requestFocus();
    }

    /** Home pressed while Home is in front: back to the first app. */
    void home() {
        row.focusFirst();
        row.requestFocus();
    }

    void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.spider_greet_night;
        else if (hour < 12) res = R.string.spider_greet_morning;
        else if (hour < 17) res = R.string.spider_greet_afternoon;
        else if (hour < 22) res = R.string.spider_greet_evening;
        else res = R.string.spider_greet_night;
        greeting.setText(res);
    }

    // ---- apps ------------------------------------------------------------------------------------

    private List<SpiderCarousel.Item> loadApps() {
        PackageManager pm = act.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<SpiderCarousel.Item> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(act.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            if (!seen.add(cn.flattenToShortString())) continue;
            String label = "org.smarttube.stable".equals(pkg) ? "YouTube"
                    : SpiderLabels.tileName(String.valueOf(ri.loadLabel(pm)), pkg);
            int art = designArt(pkg);
            Drawable icon = art != 0 ? act.getDrawable(art) : ri.loadIcon(pm);
            apps.add(new SpiderCarousel.Item(SpiderCarousel.Item.APP, label, cn, icon,
                    art != 0 ? designBox(art) : null, art == R.drawable.spider_icon_spotify));
        }
        List<String> fav = cfg.dockPackages;
        Collections.sort(apps, (a, b) -> {
            int fa = fav.indexOf(a.pkg), fb = fav.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        apps.add(new SpiderCarousel.Item(SpiderCarousel.Item.SETTINGS, act.getString(R.string.spider_settings_label), null, null,
                null, false));
        return apps;
    }

    /** Apps the design drew by hand get that art; everything else shows its own icon. */
    static int designArt(String pkg) {
        switch (pkg) {
            case "com.spotify.tv.android":
                return R.drawable.spider_icon_spotify;
            case "org.videolan.vlc":
                return R.drawable.spider_icon_vlc;
            case "com.google.android.youtube.tv":
            case "org.smarttube.stable":
            case "org.smarttube.beta":
            case "com.teamsmart.videomanager.tv":
            case "com.liskovsoft.smarttubetv":
            case "com.liskovsoft.smarttubetv.beta":
                return R.drawable.spider_icon_youtube;
            case "com.android.vending":
                return R.drawable.spider_icon_appstore;
            default:
                return 0;
        }
    }

    /** Where each icon vector's viewport sits on its tile, as fractions of the tile size (from the export). */
    private static float[] designBox(int art) {
        if (art == R.drawable.spider_icon_spotify) return new float[]{-0.00897f, -0.01128f, 1.01577f, 1.022f};
        if (art == R.drawable.spider_icon_vlc) return new float[]{-0.01059f, -0.00785f, 1.022f, 1.01577f};
        if (art == R.drawable.spider_icon_youtube) return new float[]{-0.02437f, -0.02486f, 1.04867f, 1.05272f};
        if (art == R.drawable.spider_icon_camera) return new float[]{-0.01159f, -0.00785f, 1.022f, 1.01577f};
        return new float[]{-0.01041f, -0.01128f, 1.022f, 1.022f};
    }

    // ---- carousel callbacks ----------------------------------------------------------------------

    @Override
    public void onFocusApp(SpiderCarousel.Item item, boolean animated) {
        lastFocusKey = item.key();
        row.setContentDescription(item.label);
        if (!animated || !cfg.motionOn) {
            title.animate().cancel();
            title.setText(item.label);
            title.setAlpha(1f);
            return;
        }
        title.animate().cancel();
        title.animate().alpha(0f).setDuration(cfg.motionGentle ? 60 : 110).withEndAction(() -> {
            title.setText(item.label);
            title.animate().alpha(1f).setDuration(cfg.motionGentle ? 120 : 300).start();
        }).start();
    }

    @Override
    public void onLaunch(SpiderCarousel.Item item) {
        if (item.kind == SpiderCarousel.Item.SETTINGS) {
            actions.launcherSettings();
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(item.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            act.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(act, act.getString(R.string.spider_cant_open, item.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLongPress(SpiderCarousel.Item item) {
        if (item.kind == SpiderCarousel.Item.SETTINGS) {
            actions.settingsMenu();
            return;
        }
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + item.pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    public void onUp() {
        btnWifi.requestFocus();
    }

    /** Inputs hides when the TV has no inputs, so Left stops at whichever pill button shows first. */
    private static View firstVisible(View[] pill) {
        for (View b : pill) if (b.getVisibility() == View.VISIBLE) return b;
        return pill[0];
    }
}
