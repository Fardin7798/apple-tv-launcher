package com.spidertv.launcher;

import android.view.KeyEvent;

/**
 * Spider TV helper for the remote's Input button (KEYCODE_TV, found on e.g. the Chromecast voice remote).
 * With the stock Google TV home switched off that button does nothing; this service makes it open the
 * system source picker again, from any foreground app. It consumes KEYCODE_TV only, on both edges, and
 * launches the picker when the key is released. Off by default; tools/setup.sh turns it on when run with
 * INPUT_KEY_SERVICE=1.
 */
public class SpiderInputKeyService extends android.accessibilityservice.AccessibilityService {

    private static final String SOURCE_PICKER = "com.android.tv.action.VIEW_INPUTS";

    /** Whether this service takes the key with the given code. */
    static boolean isSourceKey(int code) {
        return code == KeyEvent.KEYCODE_TV;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent key) {
        if (!isSourceKey(key.getKeyCode())) {
            return false;
        }
        if (key.getAction() == KeyEvent.ACTION_UP) {
            try {
                startActivity(new android.content.Intent(SOURCE_PICKER)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (android.content.ActivityNotFoundException missing) {
                // No picker on this TV; the key is swallowed regardless.
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent ignored) {
        // Key filtering only.
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }
}
