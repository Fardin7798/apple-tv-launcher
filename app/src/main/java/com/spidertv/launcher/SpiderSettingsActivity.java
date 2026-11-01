package com.spidertv.launcher;

import android.content.ActivityNotFoundException;

/**
 * Spider TV shortcut with no UI of its own: it jumps to the system "Default home app" chooser so Home can
 * be swapped without adb, toasts a hint when that worked or "not available" when the TV lacks the
 * chooser, and finishes in every case.
 */
public class SpiderSettingsActivity extends android.app.Activity {

    @Override
    protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        try {
            launchChooser();
        } finally {
            finish();
        }
    }

    private void launchChooser() {
        int message;
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
            message = R.string.spider_home_picker_hint;
        } catch (ActivityNotFoundException | SecurityException refused) {
            message = R.string.spider_unavailable;
        }
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }
}
