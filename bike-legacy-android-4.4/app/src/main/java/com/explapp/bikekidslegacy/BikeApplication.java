package com.explapp.bikekidslegacy;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

/** Preserves trip data when upgrading from the previous Android 4.4 build. */
public final class BikeApplication extends Application {
    private static final String OLD_PREFS = "bike_trip_v2";
    private static final String NEW_PREFS = "bike_trip_v3";

    @Override public void onCreate() {
        super.onCreate();
        migrateLegacyPreferences();
    }

    private void migrateLegacyPreferences() {
        SharedPreferences target = getSharedPreferences(NEW_PREFS, MODE_PRIVATE);
        if (!target.getAll().isEmpty()) return;

        SharedPreferences source = getSharedPreferences(OLD_PREFS, MODE_PRIVATE);
        Map<String, ?> values = source.getAll();
        if (values.isEmpty()) return;

        SharedPreferences.Editor editor = target.edit();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> strings = (Set<String>) value;
                editor.putStringSet(key, strings);
            }
        }
        editor.apply();
    }
}
