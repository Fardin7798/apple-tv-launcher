package com.appletv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Android TV "Recents" screen — matches the red Apple-TV-style Home design.
 * Remote: LEFT/RIGHT browse · OK open · DOWN -> "Close app" · BACK home.
 *
 * Manifest needs:
 *   <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
 *   <uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES"/>
 * Grant usage access once from the laptop:
 *   adb shell appops set com.appletv.launcher GET_USAGE_STATS allow
 */
public class RecentAppsActivity extends Activity {

    private static final int MAX_APPS = 10;
    private static final int GLOW_DEFAULT = 0xFFC8101A;   // Netflix-red, same as Home
    private static final int BG_DARK = 0xFF0B0A14;

    // card sizes in dp (design px / 2)
    private static final int CARD_W = 240, CARD_H = 135, FOCUS_W = 320, FOCUS_H = 180;

    private final List<RecentApp> apps = new ArrayList<>();
    private final List<View> cardViews = new ArrayList<>();
    private int selected = 0;
    private int glowColor = GLOW_DEFAULT;

    private View glowBg;
    private TextView txtSubtitle, btnClearAll, btnSettings, btnOpen, btnClose, btnInfo;
    private LinearLayout cardRow;
    private HorizontalScrollView scroller;
    private ActivityManager am;
    private PackageManager pm;
    private SharedPreferences prefs;

    static class RecentApp {
        String pkg, label;
        Drawable image;
        boolean isBanner;
        long lastUsed, fgTime;
        int color;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_apps);
        am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        pm = getPackageManager();
        prefs = getSharedPreferences("recents", MODE_PRIVATE);

        glowBg = findViewById(R.id.glowBg);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        btnClearAll = findViewById(R.id.btnClearAll);
        btnSettings = findViewById(R.id.btnSettings);
        btnOpen = findViewById(R.id.btnOpen);
        btnClose = findViewById(R.id.btnClose);
        btnInfo = findViewById(R.id.btnInfo);
        cardRow = findViewById(R.id.cardRow);
        scroller = findViewById(R.id.scroller);

        styleStatic();
        wireButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        buildCards();
    }

    // ------------------------------------------------------------------ styling

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void styleStatic() {
        setGlow(GLOW_DEFAULT);

        GradientDrawable vign = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xBF05040A});
        findViewById(R.id.vignette).setBackground(vign);

        stylePill(btnClearAll, android.R.drawable.ic_menu_delete);
        stylePill(btnSettings, android.R.drawable.ic_menu_preferences);

        styleAction(btnOpen, true);
        styleAction(btnClose, false);
        styleAction(btnInfo, false);

        int[] keys = {R.id.key1, R.id.key2, R.id.key3, R.id.key4};
        for (int id : keys) {
            findViewById(id).setBackground(round(0x24FFFFFF, dp(5), 0, 0));
        }
    }

    private GradientDrawable round(int fill, int radius, int strokeColor, int strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeW > 0) d.setStroke(strokeW, strokeColor);
        return d;
    }

    /** Glass pill: 10% white, 22% white border. Focused: solid white, dark text. */
    private void stylePill(final TextView v, int iconRes) {
        final Drawable icon = getDrawable(iconRes).mutate();
        icon.setBounds(0, 0, dp(14), dp(14));
        v.setCompoundDrawables(icon, null, null, null);
        applyPillState(v, icon, false, false);
        v.setOnFocusChangeListener((view, has) -> applyPillState(v, icon, has, false));
    }

    private void styleAction(final TextView v, final boolean primary) {
        applyPillState(v, null, false, primary);
        v.setOnFocusChangeListener((view, has) -> {
            applyPillState(v, null, has, primary);
            view.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
        });
    }

    private void applyPillState(TextView v, Drawable icon, boolean focused, boolean primary) {
        boolean white = focused || primary;
        if (white) {
            v.setBackground(round(Color.WHITE, dp(16), 0, 0));
            v.setTextColor(0xFF111111);
        } else {
            v.setBackground(round(0x1AFFFFFF, dp(16), 0x38FFFFFF, dp(1)));
            v.setTextColor(Color.WHITE);
        }
        if (icon != null) icon.setTint(white ? 0xFF111111 : Color.WHITE);
    }

    /** Radial glow like the Home screen: color -> 45% dark -> near-black. */
    private void setGlow(int color) {
        glowColor = color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[]{color, scale(color, 0.45f), BG_DARK});
        g.setGradientCenter(0.36f, 0.42f);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.62f);
        glowBg.setBackground(g);
    }
    private void animateGlowTo(final int target) {
        final int from = glowColor;
        if (from == target) return;
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), from, target);
        va.setDuration(350);
        va.addUpdateListener(a -> setGlow((Integer) a.getAnimatedValue()));
        va.start();
    }

    private static int scale(int c, float f) {
        return Color.rgb(Math.round(Color.red(c) * f), Math.round(Color.green(c) * f), Math.round(Color.blue(c) * f));
    }

    // ------------------------------------------------------------------ data

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private Intent launchIntent(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    private Set<String> homePackages() {
        Set<String> out = new HashSet<>();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : pm.queryIntentActivities(home, 0)) out.add(r.activityInfo.packageName);
        return out;
    }

    private void loadApps() {
        apps.clear();
        Set<String> skip = homePackages();
        skip.add(getPackageName());
        if (hasUsageAccess()) {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - DateUtils.DAY_IN_MILLIS, now);
            for (UsageStats s : stats.values()) {
                String pkg = s.getPackageName();
                if (skip.contains(pkg) || s.getLastTimeUsed() <= 0 || s.getTotalTimeInForeground() <= 0) continue;
                if (s.getLastTimeUsed() <= prefs.getLong(pkg, 0)) continue;
                if (launchIntent(pkg) == null) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    RecentApp a = new RecentApp();
                    a.pkg = pkg;
                    a.label = pm.getApplicationLabel(ai).toString();
                    Drawable banner = pm.getApplicationBanner(ai);
                    a.isBanner = banner != null;
                    a.image = banner != null ? banner : pm.getApplicationIcon(ai);
                    a.lastUsed = s.getLastTimeUsed();
                    a.fgTime = s.getTotalTimeInForeground();
                    a.color = dominantColor(pm.getApplicationIcon(ai));
                    apps.add(a);
                } catch (PackageManager.NameNotFoundException ignored) { }
            }
            apps.sort((x, y) -> Long.compare(y.lastUsed, x.lastUsed));
        }
        if (apps.isEmpty()) {
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            if (list == null || list.isEmpty()) {
                main.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                list = pm.queryIntentActivities(main, 0);
            }
            if (list != null) {
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (skip.contains(pkg) || pkg.equals(getPackageName())) continue;
                    if (launchIntent(pkg) == null) continue;
                    RecentApp a = new RecentApp();
                    a.pkg = pkg;
                    a.label = ri.loadLabel(pm).toString();
                    Drawable banner = ri.activityInfo.loadBanner(pm);
                    a.isBanner = banner != null;
                    a.image = banner != null ? banner : ri.loadIcon(pm);
                    a.color = dominantColor(ri.loadIcon(pm));
                    apps.add(a);
                    if (apps.size() >= MAX_APPS) break;
                }
            }
        }
        while (apps.size() > MAX_APPS) apps.remove(apps.size() - 1);
        updateSubtitle();
    }

    private void updateSubtitle() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        double gb = mi.availMem / (1024.0 * 1024 * 1024);
        txtSubtitle.setText(String.format(Locale.US, "%d apps open  ·  %.1f GB memory free", apps.size(), gb));
    }

    /** Most saturated colour of the icon, boosted so the glow looks like the design. */
    private int dominantColor(Drawable d) {
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 24, 24);
        d.draw(c);
        float[] hsv = new float[3];
        float bestScore = -1;
        int best = GLOW_DEFAULT;
        for (int x = 0; x < 24; x++) {
            for (int y = 0; y < 24; y++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 200) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[2] < 0.25f || hsv[1] < 0.25f) continue;              // skip black / white / grey
                float score = hsv[1] * hsv[2];
                if (score > bestScore) { bestScore = score; best = p; }
            }
        }
        b.recycle();
        if (bestScore < 0) return 0xFF3A3A48;                                   // neutral (e.g. YouTube-grey cards)
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.max(hsv[1], 0.85f);
        hsv[2] = 0.78f;
        return Color.HSVToColor(hsv);
    }

    // ------------------------------------------------------------------ cards

    private void buildCards() {
        cardRow.removeAllViews();
        cardViews.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        long maxFg = 1;
        for (RecentApp a : apps) maxFg = Math.max(maxFg, a.fgTime);

        for (int i = 0; i < apps.size(); i++) {
            final RecentApp a = apps.get(i);
            final View item = inf.inflate(R.layout.item_recent_app, cardRow, false);
            bindCard(item, a, (float) a.fgTime / maxFg);
            cardRow.addView(item);
            cardViews.add(item);
        }
        selected = 0;
        if (!cardViews.isEmpty()) {
            for (int i = 0; i < cardViews.size(); i++) setCardSize(cardViews.get(i), i == 0, false);
            cardViews.get(0).findViewById(R.id.cardHolder).requestFocus();
            animateGlowTo(apps.get(0).color);
        } else {
            btnClearAll.requestFocus();
        }
    }

    private void bindCard(final View item, final RecentApp a, float progress) {
        final View holder = item.findViewById(R.id.cardHolder);
        final View card = item.findViewById(R.id.card);
        final View ring = item.findViewById(R.id.ring);

        // rounded card, 26px radius, clipped
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(13));
            }
        });
        card.setClipToOutline(true);

        // brand gradient top-left dark -> bottom-right brand
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{scale(a.color, 0.25f), scale(a.color, 0.75f)});
        item.findViewById(R.id.cardBg).setBackground(bg);

        // bottom shade
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xB3000000});
        item.findViewById(R.id.shade).setBackground(shade);

        // ring: 5px white stroke, 34px radius
        ring.setBackground(round(Color.TRANSPARENT, dp(17), Color.WHITE, dp(2.5f)));

        ImageView bannerView = item.findViewById(R.id.appBanner);
        ImageView iconView = item.findViewById(R.id.appIcon);
        View cardBg = item.findViewById(R.id.cardBg);

        if (a.isBanner) {
            bannerView.setVisibility(View.VISIBLE);
            bannerView.setImageDrawable(a.image);
            iconView.setVisibility(View.GONE);
            cardBg.setVisibility(View.GONE);
        } else {
            bannerView.setVisibility(View.GONE);
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(a.image);
            cardBg.setVisibility(View.VISIBLE);
        }

        ((TextView) item.findViewById(R.id.line1)).setText("Used " + formatDuration(a.fgTime) + " today");
        ((TextView) item.findViewById(R.id.txtName)).setText(a.label);
        ((TextView) item.findViewById(R.id.txtWhen)).setText(relative(a.lastUsed));

        // progress = share of today's screen time
        View fill = item.findViewById(R.id.progFill);
        View rest = item.findViewById(R.id.progRest);
        fill.setBackground(round(boostRed(a.color), dp(1.5f), 0, 0));
        rest.setBackground(round(0x40FFFFFF, dp(1.5f), 0, 0));
        ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = Math.max(0.04f, progress);
        ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = 1f - Math.max(0.04f, progress);

        holder.setOnFocusChangeListener((v, has) -> {
            ring.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            if (has) select(cardViews.indexOf(item));
        });
        holder.setOnClickListener(v -> openApp(a));
        holder.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { btnClose.requestFocus(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { btnClearAll.requestFocus(); return true; }
            return false;
        });
    }

    private int boostRed(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        boolean reddish = hsv[0] < 20 || hsv[0] > 330;
        return reddish ? 0xFFFF4D57 : Color.WHITE;
    }

    private void select(int index) {
        if (index < 0 || index >= cardViews.size()) return;
        if (index != selected) {
            setCardSize(cardViews.get(selected), false, true);
            selected = index;
        }
        setCardSize(cardViews.get(index), true, true);
        animateGlowTo(apps.get(index).color);
        // keep the focused card at the left edge, like the design
        final View item = cardViews.get(index);
        scroller.post(() -> scroller.smoothScrollTo(Math.max(0, item.getLeft() - dp(54)), 0));
    }

    /** 240x135dp <-> 320x180dp, label 12sp <-> 15sp. */
    private void setCardSize(View item, boolean big, boolean animate) {
        final View card = item.findViewById(R.id.card);
        final TextView name = item.findViewById(R.id.txtName);
        final TextView when = item.findViewById(R.id.txtWhen);
        final TextView line1 = item.findViewById(R.id.line1);
        final int w0 = card.getLayoutParams().width, h0 = card.getLayoutParams().height;
        final int w1 = dp(big ? FOCUS_W : CARD_W), h1 = dp(big ? FOCUS_H : CARD_H);

        name.setTextSize(big ? 15 : 12);
        name.setAlpha(big ? 1f : 0.75f);
        when.setTextSize(big ? 11 : 9.5f);
        line1.setTextSize(big ? 13 : 10.5f);

        if (!animate) {
            card.getLayoutParams().width = w1;
            card.getLayoutParams().height = h1;
            card.requestLayout();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(220);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            lp.width = Math.round(w0 + (w1 - w0) * f);
            lp.height = Math.round(h0 + (h1 - h0) * f);
            card.setLayoutParams(lp);
            card.invalidateOutline();
        });
        va.start();
    }
    // ------------------------------------------------------------------ actions

    private void wireButtons() {
        btnOpen.setOnClickListener(v -> { if (current() != null) openApp(current()); });
        btnClose.setOnClickListener(v -> closeSelected());
        btnInfo.setOnClickListener(v -> {
            RecentApp a = current();
            if (a == null) return;
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
        });
        btnClearAll.setOnClickListener(v -> clearAll());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        View.OnKeyListener backToCards = (v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP && !cardViews.isEmpty()) {
                cardViews.get(selected).findViewById(R.id.cardHolder).requestFocus();
                return true;
            }
            return false;
        };
        btnOpen.setOnKeyListener(backToCards);
        btnClose.setOnKeyListener(backToCards);
        btnInfo.setOnKeyListener(backToCards);

        View.OnKeyListener downToCards = (v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !cardViews.isEmpty()) {
                cardViews.get(selected).findViewById(R.id.cardHolder).requestFocus();
                return true;
            }
            return false;
        };
        btnClearAll.setOnKeyListener(downToCards);
        btnSettings.setOnKeyListener(downToCards);
    }

    private RecentApp current() {
        return (selected >= 0 && selected < apps.size()) ? apps.get(selected) : null;
    }

    private void openApp(RecentApp a) {
        Intent i = launchIntent(a.pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void closeSelected() {
        final RecentApp a = current();
        if (a == null) return;
        am.killBackgroundProcesses(a.pkg);
        prefs.edit().putLong(a.pkg, System.currentTimeMillis()).apply();

        final int idx = selected;
        final View item = cardViews.get(idx);
        item.animate().translationY(dp(40)).alpha(0f).setDuration(220).withEndAction(() -> {
            cardRow.removeView(item);
            cardViews.remove(idx);
            apps.remove(idx);
            updateSubtitle();
            if (cardViews.isEmpty()) { animateGlowTo(GLOW_DEFAULT); btnClearAll.requestFocus(); return; }
            selected = Math.min(idx, cardViews.size() - 1);
            setCardSize(cardViews.get(selected), true, true);
            cardViews.get(selected).findViewById(R.id.cardHolder).requestFocus();
        }).start();
    }

    private void clearAll() {
        SharedPreferences.Editor ed = prefs.edit();
        long now = System.currentTimeMillis();
        for (RecentApp a : apps) {
            am.killBackgroundProcesses(a.pkg);
            ed.putLong(a.pkg, now);
        }
        ed.apply();
        apps.clear();
        cardViews.clear();
        cardRow.removeAllViews();
        updateSubtitle();
        animateGlowTo(GLOW_DEFAULT);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == KeyEvent.KEYCODE_APP_SWITCH || event.getScanCode() == 150 || event.getScanCode() == 580) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ helpers

    private String formatDuration(long ms) {
        long min = ms / 60000;
        if (min < 1) return "<1 min";
        if (min < 60) return min + " min";
        return (min / 60) + " hr " + (min % 60) + " min";
    }

    private CharSequence relative(long t) {
        long diff = System.currentTimeMillis() - t;
        if (diff < 60_000) return "Now";
        return DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }
}
