package com.spidertv.launcher;

import android.content.Intent;
import android.view.KeyEvent;

/**
 * Spider TV remote-key bridge for buttons that only the stock launcher used to answer.
 *
 * After the stock home is disabled some buttons go dead: Xiaomi remotes report Home as KEYCODE_ALL_APPS,
 * and the Settings button is handled by the stock home alone. This service watches key events and
 * redirects only these:
 * <ul>
 *   <li>Settings (KEYCODE_SETTINGS, or any code listed in spider_config.xml / config.json) to system Settings,</li>
 *   <li>the Mi button (KEYCODE_BUTTON_2, scancode 150) to the recent-apps screen,</li>
 *   <li>KEYCODE_ALL_APPS to this launcher.</li>
 * </ul>
 * Everything else passes through. Enable it over adb with
 * {@code settings put secure enabled_accessibility_services com.spidertv.launcher/.SpiderHomeKeyService}
 * and {@code settings put secure accessibility_enabled 1}.
 */
public class SpiderHomeKeyService extends android.accessibilityservice.AccessibilityService {

    /** Results of {@link #decide}. */
    static final int PASS = 0;
    static final int EAT = 1;
    static final int GO_SETTINGS = 2;
    static final int GO_RECENTS = 3;
    static final int GO_HOME = 4;

    /** Scancode sent by the Xiaomi Mi button. */
    private static final int XIAOMI_MI_SCAN = 150;

    private int[] settingsScans = new int[0];
    private int[] settingsCodes = new int[0];
    /** Loaded together with the other settings-key knobs; not acted on yet. */
    private boolean settingsOpensPanel;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScans = getResources().getIntArray(R.array.spider_settings_scancodes);
        // config.json can replace these; theme.sh restarts the service after editing it.
        SpiderPrefs prefs = SpiderPrefs.read(this);
        settingsCodes = prefs.extraSettingsKeys;
        settingsOpensPanel = prefs.settingsKeyOpensPanel;
    }

    private static boolean contains(int[] haystack, int needle) {
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i] == needle) return true;
        }
        return false;
    }

    /**
     * Decision for a single key event. Keys this service owns are eaten on both edges; the redirect runs
     * only on a release that was not cancelled.
     */
    static int decide(int code, int scan, boolean released, boolean cancelled, int[] scans, int[] codes) {
        final int action;
        if (code == KeyEvent.KEYCODE_SETTINGS || contains(scans, scan) || contains(codes, code)) {
            action = GO_SETTINGS;
        } else if (code == KeyEvent.KEYCODE_BUTTON_2 || scan == XIAOMI_MI_SCAN) {
            action = GO_RECENTS;
        } else if (code == KeyEvent.KEYCODE_ALL_APPS) {
            action = GO_HOME;
        } else {
            return PASS;
        }
        return (released && !cancelled) ? action : EAT;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent key) {
        android.util.Log.e("HomeKeyTest", "key: " + key.getKeyCode() + " action: " + key.getAction());
        final int result = decide(key.getKeyCode(), key.getScanCode(), key.getAction() == KeyEvent.ACTION_UP,
                key.isCanceled(), settingsScans, settingsCodes);
        if (result == PASS) return false;
        Intent next = null;
        if (result == GO_SETTINGS) {
            next = new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else if (result == GO_RECENTS) {
            next = new Intent(this, RecentAppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else if (result == GO_HOME) {
            next = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        if (next != null) startActivity(next);
        return true;
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent ignored) {
        // Only key events are used.
    }

    @Override
    public void onInterrupt() {
        // Nothing running to stop.
    }
}
