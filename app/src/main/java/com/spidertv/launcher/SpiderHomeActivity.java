package com.spidertv.launcher;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import java.util.ArrayList;
import java.util.List;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Spider TV home screen activity.
 *
 * {@link SpiderHomeController} draws what is on screen (carousel, greeting, header pills). This activity
 * runs the lifecycle around it and everything a pill opens: the source picker, the network and Bluetooth
 * shortcuts, system Settings, and the recents screen (Mi / menu keys, or a quick second Home press).
 */
public class SpiderHomeActivity extends android.app.Activity {

    private static final String TAG = "SpiderTv.HomeKey";
    private static final long TICK_MS = 60_000L;
    /** A second Home press within this many ms of the first opens recents. */
    static final long HOME_TWICE_MS = 1200;
    private static final String CHANNEL_MIME = "vnd.android.cursor.item/channel";
    private static final String PAIR_REMOTE_ACTION = "com.google.android.intent.action.CONNECT_INPUT";
    private static final String MI_SETTINGS_PKG = "com.xiaomi.mitv.settings";
    private static final String TV_SETTINGS_PKG = "com.android.tv.settings";
    private static final ComponentName MI_SETTINGS =
            new ComponentName(MI_SETTINGS_PKG, MI_SETTINGS_PKG + ".entry.MainActivity");
    private static final ComponentName TV_PAIRING =
            new ComponentName(TV_SETTINGS_PKG, TV_SETTINGS_PKG + ".accessories.AddAccessoryActivity");
    private static final String HARDWARE_TUNER_PATTERN = ".*/HW\\d+$";
    /** Source-row state meaning "show no status". */
    private static final int NO_STATE = -1;

    /** Uptime of the previous Home press (or resume); static so every instance sees it. */
    private static long previousHomeAt = 0;

    private float dp;
    private String tunerPackage;
    private String tunerAction;
    private String sourceIntentAction;
    private String panelComponent;
    private String panelIntentAction;
    private String vpnPackage;

    private android.widget.FrameLayout screen;
    private View sourcesPill;
    private TextView networkPill;
    private SpiderHomeController home;
    private PopupWindow shownSheet;
    private boolean freshOnResume = true;

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Both the default-network watcher and the VPN watcher only repaint the network pill. */
    private final ConnectivityManager.NetworkCallback defaultNetWatcher = new NetPillWatcher();
    private final ConnectivityManager.NetworkCallback vpnWatcher = new NetPillWatcher();

    private final TvInputManager.TvInputCallback sourceWatcher = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String id) {
            refreshSourcesPill();
        }

        @Override
        public void onInputRemoved(String id) {
            refreshSourcesPill();
        }
    };

    private final android.content.IntentFilter appChanges = new android.content.IntentFilter();
    private final android.content.BroadcastReceiver appWatcher = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context ctx, Intent change) {
            // A replace sends REMOVED (replacing) and then ADDED; only the ADDED one reloads.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(change.getAction())
                    && change.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
            }
            if (home != null) home.reload();
        }
    };

    private final Runnable everyMinute = new Runnable() {
        @Override
        public void run() {
            if (home != null) home.updateGreeting();
            paintNetworkPill();
            ui.postDelayed(this, TICK_MS);
        }
    };

    private final class NetPillWatcher extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@androidx.annotation.NonNull Network net) {
            ui.post(SpiderHomeActivity.this::paintNetworkPill);
        }

        @Override
        public void onLost(@androidx.annotation.NonNull Network net) {
            ui.post(SpiderHomeActivity.this::paintNetworkPill);
        }
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    @Override
    protected void onCreate(android.os.Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main_spider);
        dp = getResources().getDisplayMetrics().density;

        final SpiderPrefs prefs = SpiderPrefs.read(this);
        tunerPackage = prefs.tunerPackage;
        tunerAction = prefs.tunerAction;
        sourceIntentAction = prefs.sourceIntentAction;
        panelComponent = prefs.panelComponent;
        panelIntentAction = prefs.panelIntentAction;
        vpnPackage = prefs.vpnPackage;

        screen = findViewById(R.id.spiderRoot);
        sourcesPill = findViewById(R.id.spiderSourcesPill);
        networkPill = findViewById(R.id.spiderNetworkPill);
        home = new SpiderHomeController(this, prefs, new PillHandlers());

        for (String a : new String[]{Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED}) {
            appChanges.addAction(a);
        }
        appChanges.addDataScheme("package");

        final ConnectivityManager net = getSystemService(ConnectivityManager.class);
        final TvInputManager sources = tvInputs();
        if (sources != null) sources.registerCallback(sourceWatcher, ui);
        if (net != null) {
            net.registerDefaultNetworkCallback(defaultNetWatcher);
            net.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                    .addTransportType(TRANSPORT_VPN)
                    .removeCapability(NET_CAPABILITY_NOT_VPN)
                    .build(), vpnWatcher);
        }
    }

    private TvInputManager tvInputs() {
        return (TvInputManager) getSystemService(TV_INPUT_SERVICE);
    }

    /** Pill selections, routed to this activity. */
    private final class PillHandlers implements SpiderHomeController.Actions {
        @Override
        public void inputs() {
            showSourceSheet();
        }

        @Override
        public void bluetooth() {
            launchPairing();
        }

        @Override
        public void network() {
            launchNetworkTarget();
        }

        @Override
        public void settings() {
            launchSettings();
        }

        @Override
        public void settingsMenu() {
            launchSettings();
        }

        @Override
        public void launcherSettings() {
            launchSettings();
        }
    }

    @Override
    protected void onNewIntent(Intent incoming) {
        super.onNewIntent(incoming);
        if (incoming == null || !incoming.hasCategory(Intent.CATEGORY_HOME)) return;
        final long at = android.os.SystemClock.uptimeMillis();
        final long gap = at - previousHomeAt;
        Log.e(TAG, "onNewIntent: delta=" + gap);
        if (gap < HOME_TWICE_MS) {
            previousHomeAt = 0;
            if (launchRecents()) return;
        }
        previousHomeAt = at;
        if (home != null) home.home();
    }

    /** Opens the recents screen; false if starting it threw. */
    private boolean launchRecents() {
        try {
            startActivity(new Intent(this, RecentAppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception any) {
            return false;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appWatcher, appChanges, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(appWatcher, appChanges);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning to Home counts as a first press: one more inside the window opens recents.
        previousHomeAt = android.os.SystemClock.uptimeMillis();
        if (home != null) {
            home.onResume(freshOnResume);
            freshOnResume = false;
        }
        paintNetworkPill();
        refreshSourcesPill();
        ui.postDelayed(everyMinute, TICK_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(everyMinute);
        if (shownSheet != null) shownSheet.dismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(appWatcher);
        freshOnResume = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final ConnectivityManager net = getSystemService(ConnectivityManager.class);
        final TvInputManager sources = tvInputs();
        if (sources != null) sources.unregisterCallback(sourceWatcher);
        if (net != null) {
            net.unregisterNetworkCallback(defaultNetWatcher);
            net.unregisterNetworkCallback(vpnWatcher);
        }
    }

    @Override
    public void onBackPressed() {
        // Home is the bottom of the stack; Back does nothing here.
    }

    // ---- remote keys ----------------------------------------------------------------------------

    /** Keys that bring up recents while Home is in front (Mi button, app switch, menu). */
    static boolean opensRecents(int code, int scan) {
        return code == KeyEvent.KEYCODE_BUTTON_2
                || code == KeyEvent.KEYCODE_APP_SWITCH
                || code == KeyEvent.KEYCODE_MENU
                || scan == 150
                || scan == 580;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent key) {
        final int code = key.getKeyCode();
        final int scan = key.getScanCode();
        Log.e(TAG, "dispatch: action=" + key.getAction() + " key=" + code + " scan=" + scan);
        if (!opensRecents(code, scan)) return super.dispatchKeyEvent(key);
        if (key.getAction() != KeyEvent.ACTION_UP) return true;
        try {
            startActivity(new Intent(this, RecentAppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception problem) {
            Log.e(TAG, "startActivity failed", problem);
        }
        return true;
    }

    // ---- network pill ---------------------------------------------------------------------------

    /** What the network scan found. */
    private static final class NetReading {
        String text;
        boolean tunnel;
        boolean reachable;
    }

    /** Display name for a reported SSID; null when the SSID is hidden or not known. */
    static String ssidOrNull(String raw) {
        if (raw == null) return null;
        final String clean = raw.replace("\"", "").trim();
        boolean useless = clean.isEmpty() || clean.contains("unknown") || clean.equals("0x");
        return useless ? null : clean;
    }

    private void scanNetworks(NetReading out) {
        final ConnectivityManager net = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        boolean wifi = false;
        boolean wired = false;
        for (Network each : net == null ? new Network[0] : net.getAllNetworks()) {
            final NetworkCapabilities nc = net.getNetworkCapabilities(each);
            if (nc == null) continue;
            if (nc.hasTransport(TRANSPORT_VPN)) {
                out.tunnel = true;
            } else if (nc.hasCapability(NET_CAPABILITY_INTERNET)) {
                out.reachable = true;
                wired |= nc.hasTransport(TRANSPORT_ETHERNET);
                wifi |= nc.hasTransport(TRANSPORT_WIFI);
            }
        }
        if (!out.reachable) {
            out.text = getString(R.string.spider_net_offline);
            out.tunnel = false;
        } else if (wired) {
            out.text = getString(R.string.spider_net_ethernet);
        } else if (wifi) {
            final android.net.wifi.WifiManager radio =
                    (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            final android.net.wifi.WifiInfo link = radio == null ? null : radio.getConnectionInfo();
            final String name = ssidOrNull(link == null ? null : link.getSSID());
            if (name != null) out.text = name;
        }
    }

    private boolean vpnConfigured() {
        return vpnPackage != null && !vpnPackage.isEmpty();
    }

    private void paintNetworkPill() {
        if (networkPill == null) return;
        final NetReading r = new NetReading();
        r.text = getString(R.string.spider_net_wifi);
        try {
            scanNetworks(r);
        } catch (Exception ignored) {
            // Whatever was gathered before the error is shown.
        }

        final boolean tunnelUp = r.tunnel;
        final boolean tunnelDown = !tunnelUp && r.reachable && vpnConfigured();
        if (!tunnelUp && !tunnelDown) {
            networkPill.setText(r.text);
            return;
        }
        final String tag = getString(tunnelUp ? R.string.spider_vpn_on : R.string.spider_vpn_off);
        final android.text.SpannableString label = new android.text.SpannableString(r.text + " " + tag);
        label.setSpan(new SpiderVpnBadge(tunnelUp ? getDrawable(R.drawable.spider_glyph_lock) : null, dp),
                r.text.length() + 1, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        networkPill.setText(label);
    }

    private void launchNetworkTarget() {
        Intent app = null;
        if (vpnConfigured()) {
            final PackageManager pm = getPackageManager();
            app = pm.getLeanbackLaunchIntentForPackage(vpnPackage);
            if (app == null) app = pm.getLaunchIntentForPackage(vpnPackage);
        }
        start(app == null ? settingsIntent(Settings.ACTION_WIFI_SETTINGS) : app);
    }

    // ---- launching ------------------------------------------------------------------------------

    private void launchSettings() {
        start(settingsIntent(Settings.ACTION_SETTINGS));
    }

    /**
     * The given Settings action when something answers it; else Xiaomi's settings app (China-market MIUI
     * TV) if installed; else the plain action again so the caller shows "not available".
     */
    private Intent settingsIntent(String action) {
        final PackageManager pm = getPackageManager();
        final Intent plain = new Intent(action);
        if (plain.resolveActivity(pm) != null) return plain;
        final Intent mi = new Intent().setComponent(MI_SETTINGS);
        return mi.resolveActivity(pm) != null ? mi : new Intent(action);
    }

    private void start(Intent target) {
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(target);
        } catch (ActivityNotFoundException none) {
            toastUnavailable();
        }
    }

    private void toastUnavailable() {
        Toast.makeText(this, R.string.spider_unavailable, Toast.LENGTH_SHORT).show();
    }

    /** Bluetooth pill: the TV's pairing search, then its pairing screen by name, then Bluetooth settings. */
    private void launchPairing() {
        final PackageManager pm = getPackageManager();
        Intent target = new Intent(PAIR_REMOTE_ACTION);
        if (target.resolveActivity(pm) == null) target = new Intent().setComponent(TV_PAIRING);
        if (target.resolveActivity(pm) == null) target = settingsIntent(Settings.ACTION_BLUETOOTH_SETTINGS);
        start(target);
    }

    // ---- source picker --------------------------------------------------------------------------

    private boolean panelConfigured() {
        return panelComponent != null && !panelComponent.isEmpty();
    }

    private void refreshSourcesPill() {
        if (sourcesPill == null) return;
        boolean visible = tunerLaunch() != null || panelConfigured();
        final TvInputManager sources = tvInputs();
        if (!visible && sources != null) {
            for (TvInputInfo each : sources.getTvInputList()) {
                if (each.isPassthroughInput()) {
                    visible = true;
                    break;
                }
            }
        }
        sourcesPill.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Live TV launch intent: leanback entry first, else the configured action if it resolves; else null. */
    private Intent tunerLaunch() {
        if (tunerPackage == null || tunerPackage.isEmpty()) return null;
        final PackageManager pm = getPackageManager();
        final Intent leanback = pm.getLeanbackLaunchIntentForPackage(tunerPackage);
        if (leanback != null) return leanback;
        if (tunerAction == null || tunerAction.isEmpty()) return null;
        final Intent byAction = new Intent(tunerAction).setPackage(tunerPackage);
        return pm.resolveActivity(byAction, 0) != null ? byAction : null;
    }

    private void startSourcePanel() {
        final Intent call = new Intent(panelIntentAction);
        final ComponentName target = ComponentName.unflattenFromString(panelComponent);
        if (target != null) call.setComponent(target);
        try {
            startService(call);
        } catch (Exception failure) {
            toastUnavailable();
        }
    }

    private void tuneTo(String sourceId) {
        final Uri uri = TvContract.buildChannelUriForPassthroughInput(sourceId);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, CHANNEL_MIME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (ActivityNotFoundException noViewer) {
            if (sourceIntentAction == null || sourceIntentAction.isEmpty()) {
                toastUnavailable();
                return;
            }
        }
        start(new Intent(sourceIntentAction).setDataAndType(uri, CHANNEL_MIME));
    }

    /**
     * Hardware tuners have ids ending in "/HW" and a number. Matched through String.matches on purpose, so
     * the platform (ICU) regex decides edge cases such as non-ASCII digits, as it always has.
     */
    static boolean hardwareTuner(String id) {
        return id.matches(HARDWARE_TUNER_PATTERN);
    }

    /** Which tuner the Live TV row uses: hardware over software, then the lower id. */
    static boolean preferTuner(String id, boolean hw, String bestId, boolean bestHw) {
        if (bestId == null) return true;
        return hw == bestHw ? id.compareTo(bestId) < 0 : hw;
    }

    /** Primary sort of the sheet: HDMI first; 0 when both or neither are HDMI. */
    static int hdmiRank(boolean left, boolean right) {
        return left == right ? 0 : (left ? -1 : 1);
    }

    /** Sheet order: HDMI first, then case-insensitive label (labels are loaded only to break a tie). */
    private int sheetOrder(TvInputInfo x, TvInputInfo y) {
        final int rank = hdmiRank(hdmiPort(x), hdmiPort(y));
        if (rank != 0) return rank;
        return String.valueOf(x.loadLabel(this)).compareToIgnoreCase(String.valueOf(y.loadLabel(this)));
    }

    /** Row caption: the label (or the id), with "HDMI " put in front of HDMI ports that lack it. */
    static String caption(CharSequence label, String id, boolean hdmi) {
        final String text = label != null ? label.toString() : id;
        if (hdmi && !text.toUpperCase(java.util.Locale.ROOT).contains("HDMI")) return "HDMI " + text;
        return text;
    }

    private static boolean hdmiPort(TvInputInfo source) {
        return source.getType() == TvInputInfo.TYPE_HDMI;
    }

    private void showSourceSheet() {
        final View pill = findViewById(R.id.spiderSourcesPill);
        if (pill == null) return;
        final View sheet = getLayoutInflater().inflate(R.layout.spider_inputs_popup, screen, false);
        final LinearLayout list = sheet.findViewById(R.id.spiderRows);
        final int narrowest = Math.max(pill.getWidth(), (int) (150 * dp));
        final PopupWindow window = makeSheetWindow(sheet, narrowest);

        final TvInputManager sources = tvInputs();
        final String tuner = sources == null ? null : fillSourceRows(sources, list, window);

        if (list.getChildCount() == 0 && panelConfigured()) {
            addRow(list, window, R.drawable.spider_glyph_input, getString(R.string.spider_sources_label), NO_STATE,
                    this::startSourcePanel);
        }

        Intent live = tunerLaunch();
        if (live != null && tuner != null) {
            live = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tuner), CHANNEL_MIME)
                    .setPackage(tunerPackage);
        }
        if (live != null) {
            if (list.getChildCount() > 0) addDivider(list);
            final Intent liveTarget = live;
            addRow(list, window, R.drawable.spider_glyph_livetv, getString(R.string.spider_tuner_label), NO_STATE,
                    () -> start(liveTarget));
        }

        if (list.getChildCount() == 0) {
            Toast.makeText(this, R.string.spider_no_sources, Toast.LENGTH_SHORT).show();
            return;
        }
        sheet.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        window.setWidth(Math.max(narrowest, sheet.getMeasuredWidth()));
        final int edge = getResources().getBoolean(R.bool.spider_menu_rtl)
                ? android.view.Gravity.END : android.view.Gravity.START;
        revealBelow(window, sheet, pill, edge);
        list.getChildAt(0).requestFocus();
    }

    /**
     * One row per pass-through source (HDMI first, then a divider and the other kinds). Returns the id of
     * the system tuner the Live TV row should open, or null.
     */
    private String fillSourceRows(TvInputManager sources, LinearLayout list, PopupWindow window) {
        final List<TvInputInfo> all = new ArrayList<>(sources.getTvInputList());
        java.util.Collections.sort(all, this::sheetOrder);

        // Some TVs claim every HDMI port is connected. With two or more ports that is impossible, so no
        // HDMI row shows a status then.
        int ports = 0;
        int claimed = 0;
        for (TvInputInfo each : all) {
            if (each.isPassthroughInput() && hdmiPort(each)) {
                ports++;
                if (sources.getInputState(each.getId()) == TvInputManager.INPUT_STATE_CONNECTED) claimed++;
            }
        }
        final boolean ignoreHdmiState = ports > 1 && claimed == ports;

        String bestTuner = null;
        boolean bestIsHw = false;
        boolean dividerDone = false;
        for (TvInputInfo each : all) {
            final String id = each.getId();
            if (each.getType() == TvInputInfo.TYPE_TUNER && (each.getServiceInfo().applicationInfo.flags
                    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                final boolean hw = hardwareTuner(id);
                if (preferTuner(id, hw, bestTuner, bestIsHw)) {
                    bestTuner = id;
                    bestIsHw = hw;
                }
            }
            if (!each.isPassthroughInput()) continue;

            final boolean hdmi = hdmiPort(each);
            final String text = caption(each.loadLabel(this), id, hdmi);
            final int state = (hdmi && ignoreHdmiState) ? NO_STATE : sources.getInputState(id);
            if (!hdmi) {
                if (!dividerDone && list.getChildCount() > 0) addDivider(list);
                dividerDone = true;
            }
            addRow(list, window, hdmi ? R.drawable.spider_glyph_input : R.drawable.spider_glyph_av, text, state,
                    () -> tuneTo(id));
        }
        return bestTuner;
    }

    private PopupWindow makeSheetWindow(View sheet, int width) {
        final PopupWindow w = new PopupWindow(sheet, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        w.setOutsideTouchable(true);
        w.setAnimationStyle(0);
        w.setElevation(0f);
        w.setOnDismissListener(() -> {
            if (shownSheet == w) shownSheet = null;
        });
        shownSheet = w;
        return w;
    }

    /** Drops the sheet under the pill with a quick fade, unfold and downward slide. */
    private void revealBelow(PopupWindow window, View sheet, View pill, int edge) {
        sheet.setAlpha(0f);
        sheet.setPivotY(0f);
        sheet.setScaleY(0.92f);
        sheet.setTranslationY(-8 * dp);
        window.showAsDropDown(pill, 0, (int) (10 * dp), edge);
        sheet.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addDivider(LinearLayout list) {
        final int v = (int) (6 * dp);
        final int h = (int) (12 * dp);
        final LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp);
        lp.setMargins(h, v, h, v);
        final View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        list.addView(line, lp);
    }

    private void addRow(LinearLayout list, PopupWindow window, int glyph, String text, int state, Runnable action) {
        final View row = getLayoutInflater().inflate(R.layout.spider_input_row, list, false);
        ((android.widget.ImageView) row.findViewById(R.id.spiderRowGlyph)).setImageResource(glyph);
        ((TextView) row.findViewById(R.id.spiderRowTitle)).setText(text);
        final TextView statusText = row.findViewById(R.id.spiderRowStatus);
        final View statusDot = row.findViewById(R.id.spiderStatusDot);
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            statusText.setText(R.string.spider_source_connected);
            statusDot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            statusText.setText(R.string.spider_source_disconnected);
            statusDot.setVisibility(View.GONE);
        } else {
            // Standby or unknown: hide both the text and the dot.
            statusText.setVisibility(View.GONE);
            statusDot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            window.dismiss();
            action.run();
        });
        list.addView(row);
    }
}
