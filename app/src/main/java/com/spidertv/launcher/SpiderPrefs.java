package com.spidertv.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

/**
 * Immutable bundle of the Spider TV launcher knobs.
 *
 * Every knob starts from res/values/spider_config.xml. Placing a {@code config.json} file in the app's
 * external files folder replaces individual knobs; a key that is missing, or whose JSON value has the
 * wrong type, leaves the resource value in place.
 */
final class SpiderPrefs {

    /** Name of the optional JSON override file. */
    static final String OVERRIDE_FILE = "config.json";
    static final String STORE_NAME = "launcher_settings";

    private static final String TAG = "SpiderTv.Config";
    /** Override files larger than this are ignored outright. */
    private static final long MAX_OVERRIDE_BYTES = 64L * 1024L;
    private static final char UTF8_BOM = '\uFEFF';

    final List<String> dockPackages;
    final List<String> hiddenPackages;
    final String tunerPackage;
    final String tunerAction;
    final String panelComponent;
    final String panelIntentAction;
    final String sourceIntentAction;
    final boolean motionOn;
    final boolean motionGentle;
    final String vpnPackage;
    final int[] extraSettingsKeys;
    final boolean settingsKeyOpensPanel;
    /** Whether a config.json file was found and accepted. */
    final boolean fromJsonOverride;

    private SpiderPrefs(Knobs k, boolean fromJsonOverride) {
        this.fromJsonOverride = fromJsonOverride;
        settingsKeyOpensPanel = k.settingsPanel;
        extraSettingsKeys = k.settingsKeys;
        vpnPackage = k.vpnPkg;
        motionGentle = k.gentle;
        motionOn = k.motion;
        sourceIntentAction = k.sourceAction;
        panelIntentAction = k.panelAction;
        panelComponent = k.panelComponent;
        tunerAction = k.tunerAction;
        tunerPackage = k.tunerPkg;
        hiddenPackages = Collections.unmodifiableList(k.hidden);
        dockPackages = Collections.unmodifiableList(k.dock);
    }

    /** Where config.json may live, or null when the TV has no external files folder. */
    static File overrideFolder(Context context) {
        return context.getExternalFilesDir(null);
    }

    static SpiderPrefs read(Context context) {
        Knobs knobs = Knobs.defaults(context.getResources());
        File where = overrideFolder(context);
        JSONObject json = readOverride(where != null ? new File(where, OVERRIDE_FILE) : null);
        if (json != null) {
            knobs.override(json);
            return new SpiderPrefs(knobs, true);
        }
        return new SpiderPrefs(knobs, false);
    }

    /** Scratch holder used while resource defaults and the JSON override are merged. */
    private static final class Knobs {
        List<String> dock, hidden;
        String tunerPkg, tunerAction, panelComponent, panelAction, sourceAction, vpnPkg;
        boolean motion, gentle, settingsPanel;
        int[] settingsKeys;

        static Knobs defaults(android.content.res.Resources res) {
            Knobs k = new Knobs();
            k.dock = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.spider_dock_packages)));
            k.hidden = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.spider_hidden_packages)));
            k.tunerPkg = res.getString(R.string.spider_tuner_package).trim();
            k.tunerAction = res.getString(R.string.spider_tuner_action).trim();
            k.panelComponent = res.getString(R.string.spider_panel_service).trim();
            k.panelAction = res.getString(R.string.spider_panel_action).trim();
            k.sourceAction = res.getString(R.string.spider_source_intent_action).trim();
            k.motion = res.getBoolean(R.bool.spider_motion_enabled);
            k.gentle = res.getBoolean(R.bool.spider_motion_reduced);
            k.vpnPkg = res.getString(R.string.spider_vpn_package).trim();
            k.settingsKeys = res.getIntArray(R.array.spider_settings_keycodes);
            k.settingsPanel = res.getBoolean(R.bool.spider_settings_as_panel);
            return k;
        }

        /** JSON key names are part of the public config.json format and must not change. */
        void override(JSONObject json) {
            dock = asStrings(json.opt("dock_apps"), dock);
            hidden = asStrings(json.opt("hidden_apps"), hidden);
            tunerPkg = asString(json.opt("live_tv_package"), tunerPkg);
            tunerAction = asString(json.opt("live_tv_action"), tunerAction);
            panelComponent = asString(json.opt("source_panel_service"), panelComponent);
            panelAction = asString(json.opt("source_panel_action"), panelAction);
            sourceAction = asString(json.opt("input_action"), sourceAction);
            motion = asBool(json.opt("animations"), motion);
            gentle = asBool(json.opt("reduced_motion"), gentle);
            vpnPkg = asString(json.opt("vpn_app"), vpnPkg);
            settingsKeys = asKeyCodes(json.opt("settings_key_keycodes"), settingsKeys);
            settingsPanel = asBool(json.opt("settings_key_panel"), settingsPanel);
        }
    }

    /** The override parsed as a JSON object; null when absent, too big or unreadable. */
    private static JSONObject readOverride(File file) {
        if (file == null || !file.isFile()) return null;
        final long bytes = file.length();
        if (bytes > MAX_OVERRIDE_BYTES) {
            Log.w(TAG, "skipped " + file + " (over 64 KB)");
            return null;
        }
        try {
            String json = new String(head(file, (int) bytes), java.nio.charset.StandardCharsets.UTF_8);
            if (json.length() > 0 && json.charAt(0) == UTF8_BOM) json = json.substring(1);
            return new JSONObject(json);
        } catch (Exception why) {
            Log.w(TAG, "skipped " + file + ": " + why.getMessage());
            return null;
        }
    }

    /** Up to {@code limit} leading bytes of the file; a shorter array if the file ends first. */
    private static byte[] head(File file, int limit) throws IOException {
        final byte[] buf = new byte[limit];
        int used = 0;
        try (java.io.InputStream stream = new java.io.FileInputStream(file)) {
            int n;
            while (used < limit && (n = stream.read(buf, used, limit - used)) > 0) {
                used += n;
            }
        }
        if (used == limit) return buf;
        return Arrays.copyOf(buf, used);
    }

    // JSON readers. Each one hands back the fallback unless the value has the type it expects.

    private static String asString(Object raw, String fallback) {
        return raw instanceof String ? ((String) raw).trim() : fallback;
    }

    private static boolean asBool(Object raw, boolean fallback) {
        return raw instanceof Boolean ? (Boolean) raw : fallback;
    }

    /** All entries must be numbers of at least 1; a single bad entry discards the array. */
    private static int[] asKeyCodes(Object raw, int[] fallback) {
        if (!(raw instanceof JSONArray)) return fallback;
        final JSONArray list = (JSONArray) raw;
        final int[] out = new int[list.length()];
        for (int pos = 0; pos < out.length; pos++) {
            Object entry = list.opt(pos);
            if (!(entry instanceof Number)) return fallback;
            int value = ((Number) entry).intValue();
            if (value < 1) return fallback;
            out[pos] = value;
        }
        return out;
    }

    /** Entries are trimmed; empty ones and non-strings are dropped. */
    private static List<String> asStrings(Object raw, List<String> fallback) {
        if (!(raw instanceof JSONArray)) return fallback;
        final JSONArray list = (JSONArray) raw;
        final List<String> out = new ArrayList<>();
        for (int pos = 0; pos < list.length(); pos++) {
            Object entry = list.opt(pos);
            if (entry instanceof String) {
                String s = ((String) entry).trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }
}
