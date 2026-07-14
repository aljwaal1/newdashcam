package com.explapp.bikekidslegacy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements LocationListener {
    private static final int LOCATION_REQUEST = 41;
    private static final String PREFS = "bike_trip_v3";
    private static final String HISTORY_KEY = "history";
    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int PAUSED = 2;
    private static final long SPEED_STALE_MS = 4500L;

    private final Handler handler = new Handler();
    private LocationManager locationManager;
    private RoadView roadView;
    private TextView speedView;
    private TextView maxSpeedView;
    private TextView distanceView;
    private TextView durationView;
    private TextView averageView;
    private TextView accuracyView;
    private TextView statusView;
    private Button startPauseButton;
    private Button finishButton;

    private int state = IDLE;
    private float distanceMeters;
    private float maxSpeedKmh;
    private float displayedSpeedKmh;
    private float lastAccuracy = 999f;
    private long accumulatedMs;
    private long runningStartedElapsed;
    private long tripStartedWall;
    private long lastAcceptedElapsed;
    private Location lastFix;
    private Location distanceAnchor;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (state != RUNNING) return;
            long now = SystemClock.elapsedRealtime();
            if (lastAcceptedElapsed > 0L && now - lastAcceptedElapsed > SPEED_STALE_MS) {
                displayedSpeedKmh = 0f;
                roadView.setSpeed(0f);
                statusView.setText("إشارة GPS متوقفة مؤقتًا");
            }
            saveCurrentTrip();
            refreshDashboard();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        loadInterruptedTrip();
        buildCompactScreen();
        refreshDashboard();
    }

    private void buildCompactScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(4), dp(5), dp(4));
        root.setBackgroundColor(Color.rgb(229, 241, 242));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (Build.VERSION.SDK_INT >= 17) top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = text("مغامرة الدراجة", 17, Color.rgb(18, 48, 71), true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));

        statusView = text("اضغط بدء الرحلة لتشغيل GPS", 11, Color.WHITE, true);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(8), 0, dp(8), 0);
        statusView.setBackground(round(Color.rgb(21, 78, 112), 12));
        top.addView(statusView, new LinearLayout.LayoutParams(dp(205), dp(34)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 17) center.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        roadView = new RoadView(this);
        roadView.setBackground(round(Color.WHITE, 10));
        LinearLayout.LayoutParams roadLp = new LinearLayout.LayoutParams(0, -1, 1.65f);
        roadLp.rightMargin = dp(5);
        center.addView(roadView, roadLp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 17) panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout speedCard = new LinearLayout(this);
        speedCard.setOrientation(LinearLayout.VERTICAL);
        speedCard.setGravity(Gravity.CENTER);
        speedCard.setBackground(round(Color.rgb(18, 48, 71), 12));
        TextView speedLabel = text("السرعة الحالية", 10, Color.rgb(178, 220, 233), false);
        speedLabel.setGravity(Gravity.CENTER);
        speedView = text("0.0 كم/س", 27, Color.WHITE, true);
        speedView.setGravity(Gravity.CENTER);
        maxSpeedView = text("الأعلى 0.0 كم/س", 10, Color.rgb(178, 220, 233), false);
        maxSpeedView.setGravity(Gravity.CENTER);
        speedCard.addView(speedLabel, new LinearLayout.LayoutParams(-1, 0, .25f));
        speedCard.addView(speedView, new LinearLayout.LayoutParams(-1, 0, .5f));
        speedCard.addView(maxSpeedView, new LinearLayout.LayoutParams(-1, 0, .25f));
        panel.addView(speedCard, new LinearLayout.LayoutParams(-1, 0, 1.15f));

        LinearLayout stats1 = new LinearLayout(this);
        stats1.setOrientation(LinearLayout.HORIZONTAL);
        distanceView = stat("المسافة", "0 م");
        durationView = stat("المدة", "00:00");
        stats1.addView(distanceView, weight());
        stats1.addView(space(), new LinearLayout.LayoutParams(dp(4), 1));
        stats1.addView(durationView, weight());
        panel.addView(stats1, top(4, .7f));

        LinearLayout stats2 = new LinearLayout(this);
        stats2.setOrientation(LinearLayout.HORIZONTAL);
        averageView = stat("المتوسط", "0.0 كم/س");
        accuracyView = stat("دقة GPS", "بانتظار");
        stats2.addView(averageView, weight());
        stats2.addView(space(), new LinearLayout.LayoutParams(dp(4), 1));
        stats2.addView(accuracyView, weight());
        panel.addView(stats2, top(4, .7f));

        center.addView(panel, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 17) controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        startPauseButton = button("بدء الرحلة", Color.rgb(22, 156, 103));
        startPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onStartPausePressed(); }
        });
        finishButton = button("إنهاء وحفظ", Color.rgb(205, 62, 58));
        finishButton.setEnabled(false);
        finishButton.setAlpha(.45f);
        finishButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmFinish(); }
        });
        Button settings = button("إعدادات GPS", Color.rgb(53, 94, 125));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
        Button history = button("السجل", Color.rgb(55, 67, 78));
        history.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHistory(); }
        });

        controls.addView(startPauseButton, controlWeight(1.3f));
        controls.addView(space(), new LinearLayout.LayoutParams(dp(4), 1));
        controls.addView(finishButton, controlWeight(1.15f));
        controls.addView(space(), new LinearLayout.LayoutParams(dp(4), 1));
        controls.addView(settings, controlWeight(1f));
        controls.addView(space(), new LinearLayout.LayoutParams(dp(4), 1));
        controls.addView(history, controlWeight(.7f));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(52)));

        setContentView(root);
    }

    private void onStartPausePressed() {
        if (state == RUNNING) pauseTrip();
        else if (state == PAUSED) resumeTrip();
        else startNewTrip();
    }

    private void startNewTrip() {
        if (!ensurePermission()) return;
        if (!hasLocationProvider()) {
            statusView.setText("شغّل GPS من زر الإعدادات");
            return;
        }
        distanceMeters = 0f;
        maxSpeedKmh = 0f;
        displayedSpeedKmh = 0f;
        accumulatedMs = 0L;
        tripStartedWall = System.currentTimeMillis();
        lastFix = null;
        distanceAnchor = null;
        lastAcceptedElapsed = 0L;
        beginRunning();
    }

    private void resumeTrip() {
        if (!ensurePermission()) return;
        if (!hasLocationProvider()) {
            statusView.setText("خدمة الموقع متوقفة");
            return;
        }
        lastFix = null;
        distanceAnchor = null;
        lastAcceptedElapsed = 0L;
        beginRunning();
    }

    private void beginRunning() {
        state = RUNNING;
        runningStartedElapsed = SystemClock.elapsedRealtime();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestLocationUpdates();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        statusView.setText("جارٍ تثبيت إشارة GPS…");
        refreshDashboard();
    }

    private void pauseTrip() {
        accumulatedMs = currentDurationMs();
        state = PAUSED;
        runningStartedElapsed = 0L;
        stopLocationUpdates();
        handler.removeCallbacks(ticker);
        displayedSpeedKmh = 0f;
        lastFix = null;
        distanceAnchor = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        statusView.setText("متوقفة مؤقتًا");
        saveCurrentTrip();
        refreshDashboard();
    }

    private void confirmFinish() {
        if (state == IDLE) return;
        new AlertDialog.Builder(this)
                .setTitle("إنهاء الرحلة")
                .setMessage("هل تريد إنهاء الرحلة وحفظها؟")
                .setPositiveButton("إنهاء وحفظ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { finishTrip(); }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void finishTrip() {
        long duration = currentDurationMs();
        stopLocationUpdates();
        handler.removeCallbacks(ticker);
        saveHistoryRecord(tripStartedWall == 0L ? System.currentTimeMillis() - duration : tripStartedWall,
                duration, distanceMeters, maxSpeedKmh);
        state = IDLE;
        accumulatedMs = 0L;
        runningStartedElapsed = 0L;
        displayedSpeedKmh = 0f;
        lastFix = null;
        distanceAnchor = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
        statusView.setText("تم حفظ الرحلة");
        Toast.makeText(this, "تم حفظ الرحلة", Toast.LENGTH_SHORT).show();
        refreshDashboard();
    }

    private void requestLocationUpdates() {
        if (!hasPermission() || locationManager == null) return;
        boolean requested = false;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
                requested = true;
            }
        } catch (Exception ignored) { }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f, this);
                requested = true;
            }
        } catch (Exception ignored) { }
        if (!requested) statusView.setText("لا يوجد مزود موقع مفعّل");
    }

    private void stopLocationUpdates() {
        if (locationManager == null) return;
        try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        if (state != RUNNING || location == null) return;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 999f;
        lastAccuracy = accuracy;
        if (!TripMath.isUsableFix(System.currentTimeMillis(), location.getTime(),
                location.hasAccuracy(), accuracy)) {
            accuracyView.setText("دقة GPS\n" + (location.hasAccuracy() ? Math.round(accuracy) + " م" : "غير متوفرة"));
            statusView.setText("إشارة GPS ضعيفة");
            return;
        }
        if (!isBetterFix(location, lastFix)) return;

        float segmentFromLast = 0f;
        long segmentTime = 0L;
        if (lastFix != null) {
            segmentFromLast = lastFix.distanceTo(location);
            segmentTime = location.getTime() - lastFix.getTime();
        }
        float rawSpeed = TripMath.speedKmh(location.getSpeed(), location.hasSpeed(),
                segmentFromLast, segmentTime);
        displayedSpeedKmh = TripMath.smoothSpeed(displayedSpeedKmh, rawSpeed);
        if (displayedSpeedKmh < .8f) displayedSpeedKmh = 0f;
        maxSpeedKmh = Math.max(maxSpeedKmh, displayedSpeedKmh);

        if (distanceAnchor == null) {
            distanceAnchor = new Location(location);
        } else {
            float segment = distanceAnchor.distanceTo(location);
            long elapsed = location.getTime() - distanceAnchor.getTime();
            if (TripMath.shouldCountSegment(segment, elapsed,
                    distanceAnchor.getAccuracy(), accuracy)) {
                distanceMeters += segment;
                distanceAnchor = new Location(location);
            } else if (elapsed > 20000L) {
                distanceAnchor = new Location(location);
            }
        }
        lastFix = new Location(location);
        lastAcceptedElapsed = SystemClock.elapsedRealtime();
        statusView.setText(accuracy <= 10f ? "إشارة GPS ممتازة" : accuracy <= 22f ? "إشارة GPS جيدة" : "إشارة GPS مقبولة");
        accuracyView.setText("دقة GPS\n" + Math.round(accuracy) + " م");
        roadView.setSpeed(displayedSpeedKmh);
        refreshDashboard();
    }

    private boolean isBetterFix(Location candidate, Location current) {
        if (current == null) return true;
        long dt = candidate.getTime() - current.getTime();
        if (dt < -2000L) return false;
        float ca = candidate.hasAccuracy() ? candidate.getAccuracy() : 999f;
        float oa = current.hasAccuracy() ? current.getAccuracy() : 999f;
        if (dt > 10000L) return true;
        return ca <= oa + 6f && dt >= 0L;
    }

    private boolean ensurePermission() {
        if (hasPermission()) return true;
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        }
        statusView.setText("امنح صلاحية الموقع");
        return false;
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationProvider() {
        if (locationManager == null) return false;
        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) { return false; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0 &&
                results[0] == PackageManager.PERMISSION_GRANTED) startNewTrip();
        else if (requestCode == LOCATION_REQUEST) statusView.setText("صلاحية الموقع مرفوضة");
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) statusView.setText("تم إيقاف GPS");
    }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private long currentDurationMs() {
        if (state == RUNNING && runningStartedElapsed > 0L)
            return accumulatedMs + Math.max(0L, SystemClock.elapsedRealtime() - runningStartedElapsed);
        return accumulatedMs;
    }

    private void refreshDashboard() {
        if (speedView == null) return;
        long duration = currentDurationMs();
        speedView.setText(String.format(Locale.US, "%.1f كم/س", state == RUNNING ? displayedSpeedKmh : 0f));
        maxSpeedView.setText(String.format(Locale.US, "الأعلى %.1f كم/س", maxSpeedKmh));
        distanceView.setText("المسافة\n" + formatDistance(distanceMeters));
        durationView.setText("المدة\n" + formatDuration(duration));
        averageView.setText(String.format(Locale.US, "المتوسط\n%.1f كم/س",
                TripMath.averageSpeedKmh(distanceMeters, duration)));
        if (lastAccuracy >= 999f) accuracyView.setText("دقة GPS\nبانتظار");
        startPauseButton.setText(state == RUNNING ? "إيقاف مؤقت" : state == PAUSED ? "متابعة" : "بدء الرحلة");
        startPauseButton.setBackground(round(state == RUNNING ? Color.rgb(232, 139, 35) : Color.rgb(22, 156, 103), 10));
        finishButton.setEnabled(state != IDLE);
        finishButton.setAlpha(state == IDLE ? .45f : 1f);
    }

    private void saveCurrentTrip() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("state", state).putFloat("distance", distanceMeters)
                .putFloat("maxSpeed", maxSpeedKmh).putLong("duration", currentDurationMs())
                .putLong("startedWall", tripStartedWall).apply();
    }

    private void loadInterruptedTrip() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int saved = p.getInt("state", IDLE);
        if (saved != IDLE) {
            state = PAUSED;
            distanceMeters = p.getFloat("distance", 0f);
            maxSpeedKmh = p.getFloat("maxSpeed", 0f);
            accumulatedMs = p.getLong("duration", 0L);
            tripStartedWall = p.getLong("startedWall", 0L);
        }
    }

    private void saveHistoryRecord(long start, long duration, float distance, float maxSpeed) {
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(HISTORY_KEY, "[]"));
            JSONArray out = new JSONArray();
            JSONObject r = new JSONObject();
            r.put("start", start); r.put("duration", duration);
            r.put("distance", distance); r.put("maxSpeed", maxSpeed);
            out.put(r);
            for (int i = 0; i < old.length() && i < 49; i++) out.put(old.getJSONObject(i));
            p.edit().putString(HISTORY_KEY, out.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showHistory() {
        StringBuilder body = new StringBuilder();
        try {
            JSONArray a = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);
            for (int i = 0; i < a.length(); i++) {
                JSONObject r = a.getJSONObject(i);
                body.append(i + 1).append(". ").append(f.format(new Date(r.optLong("start"))))
                        .append("\nالمسافة: ").append(formatDistance((float)r.optDouble("distance")))
                        .append("  المدة: ").append(formatDuration(r.optLong("duration"))).append("\n\n");
            }
        } catch (Exception ignored) { }
        if (body.length() == 0) body.append("لا توجد رحلات محفوظة");
        new AlertDialog.Builder(this).setTitle("سجل الرحلات").setMessage(body.toString())
                .setPositiveButton("إغلاق", null).show();
    }

    @Override protected void onDestroy() {
        stopLocationUpdates();
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView stat(String title, String value) {
        TextView v = text(title + "\n" + value, 10, Color.rgb(31, 61, 74), true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(Color.WHITE, 9));
        v.setPadding(dp(2), dp(2), dp(2), dp(2));
        return v;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackground(round(color, 10));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -1, 1f); }
    private LinearLayout.LayoutParams top(int margin, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 0, weight);
        p.topMargin = dp(margin); return p;
    }
    private LinearLayout.LayoutParams controlWeight(float w) { return new LinearLayout.LayoutParams(0, -1, w); }
    private View space() { return new View(this); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private String formatDistance(float meters) {
        return meters < 1000f ? Math.round(meters) + " م" : String.format(Locale.US, "%.2f كم", meters / 1000f);
    }

    private String formatDuration(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long h = total / 3600L, m = (total % 3600L) / 60L, s = total % 60L;
        return h > 0 ? String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }
}