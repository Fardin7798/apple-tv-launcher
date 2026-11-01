package com.spidertv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Handles the physical "Mi" remote button (KEYCODE_BUTTON_2 / scanCode 150/580)
 * sent via android.intent.action.GLOBAL_BUTTON on Xiaomi TVs without needing
 * an AccessibilityService (which breaks Bluetooth volume repeat acceleration).
 */
public class MiKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "MiKeyReceiver";
    public static final String ACTION_GLOBAL_BUTTON = "android.intent.action.GLOBAL_BUTTON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_GLOBAL_BUTTON.equals(intent.getAction())) return;
        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null) return;
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        Log.i(TAG, "GLOBAL_BUTTON: action=" + event.getAction() + " key=" + keyCode + " scan=" + scanCode);
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || scanCode == 150 || scanCode == 580) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                Intent recents = new Intent(context, RecentAppsActivity.class);
                recents.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(recents);
            }
        }
    }
}
