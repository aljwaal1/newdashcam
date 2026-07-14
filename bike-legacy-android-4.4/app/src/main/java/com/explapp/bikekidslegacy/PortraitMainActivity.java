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
import android.location.GpsSatellite;
import android.location.GpsStatus;
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
import java.util.Iterator;
import java.util.Locale;

public final class PortraitMainActivity extends Activity implements LocationListener, GpsStatus.Listener {
    private static final int LOCATION_REQUEST = 41;
    private static final String PREFS = "bike_trip_v3";
    private static final String HISTORY_KEY = "history";
    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int PAUSED = 2;
    private static final long SPEED_STALE_MS = 5000L;

    private final Handler handler = new Handler();
    private LocationManager locationManager;
    private RoadView roadView;
    private TextView speedView;
    private TextView maxSpeedView;
    private TextView distanceView;
    private TextView durationView;
    private TextView averageView;
    private TextView accuracyView;
    private TextView gpsStateView;
    private TextView satellitesView;
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
                if (roadView != null) roadView.setSpeed(0f);
                setGpsState("GPS: الإشارة متوقفة مؤقتًا", Color.rgb(198, 55, 49));
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
        buildPortraitScreen();
        refreshDashboard();
    }

    private void buildPortraitScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(6), dp(8), dp(7));
        root.setBackgroundColor(Color.rgb(229, 241, 242));
        if (Build.VERSION.SDK_INT >= 17) root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = text("مغامرة الدراجة", 22, Color.rgb(18, 48, 71), true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout gpsBar = new LinearLayout(this);
        gpsBar.setOrientation(LinearLayout.HORIZONTAL);
        gpsBar.setGravity(Gravity.CENTER);
        gpsBar.setPadding(dp(6), 0, dp(6), 0);
        gpsBar.setBackground(round(Color.WHITE, 12));
        gpsStateView = text("GPS: اضغط بدء الرحلة", 12, Color.rgb(92, 103, 110), true);
        gpsStateView.setGravity(Gravity.CENTER);
        satellitesView = text("الأقمار: 0", 12, Color.rgb(92, 103, 110), true);
        satellitesView.setGravity(Gravity.CENTER);
        gpsBar.addView(gpsStateView, new LinearLayout.LayoutParams(0, -1, 1.5f));
        gpsBar.addView(satellitesView, new LinearLayout.LayoutParams(0, -1, .7f));
        root.addView(gpsBar, new LinearLayout.LayoutParams(-1, dp(42)));

        roadView = new RoadView(this);
        roadView.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams roadParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        roadParams.topMargin = dp(6);
        roadParams.bottomMargin = dp(6);
        root.addView(roadView, roadParams);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER);

        LinearLayout speedCard = new LinearLayout(this);
        speedCard.setOrientation(LinearLayout.VERTICAL);
        speedCard.setGravity(Gravity.CENTER);
        speedCard.setBackground(round(Color.rgb(18, 48, 71), 14));
        TextView speedLabel = text("السرعة الحالية", 11, Color.rgb(178, 220, 233), false);
        speedLabel.setGravity(Gravity.CENTER);
        speedView = text("0.0 كم/س", 28, Color.WHITE, true);
        speedView.setGravity(Gravity.CENTER);
        maxSpeedView = text("الأعلى: 0.0 كم/س", 11, Color.rgb(178, 220, 233), false);
        maxSpeedView.setGravity(Gravity.CENTER);
        speedCard.addView(speedLabel, new LinearLayout.LayoutParams(-1, 0, .25f));
        speedCard.addView(speedView, new LinearLayout.LayoutParams(-1, 0, .5f));
        speedCard.addView(maxSpeedView, new LinearLayout.LayoutParams(-1, 0, .25f));
        speedRow.addView(speedCard, new LinearLayout.LayoutParams(0, dp(106), 1.15f));

        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setPadding(dp(6), 0, 0, 0);
        distanceView = stat("المسافة", "0 م");
        durationView = stat("المدة", "00:00:00");
        mini.addView(distanceView, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams durationLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        durationLp.topMargin = dp(5);
        mini.addView(durationView, durationLp);
        speedRow.addView(mini, new LinearLayout.LayoutParams(0, dp(106), .85f));
        root.addView(speedRow, new LinearLayout.LayoutParams(-1, dp(106)));

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        averageView = stat("المتوسط", "0.0 كم/س");
        accuracyView = stat("دقة GPS", "بانتظار الإشارة");
        statsRow.addView(averageView, new LinearLayout.LayoutParams(0, dp(62), 1f));
        LinearLayout.LayoutParams accuracyLp = new LinearLayout.LayoutParams(0, dp(62), 1f);
        accuracyLp.rightMargin = dp(6);
        statsRow.addView(accuracyView, accuracyLp);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(-1, dp(62));
        statsLp.topMargin = dp(6);
        root.addView(statsRow, statsLp);

        LinearLayout mainControls = new LinearLayout(this);
        mainControls.setOrientation(LinearLayout.HORIZONTAL);
        mainControls.setGravity(Gravity.CENTER);
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
        mainControls.addView(startPauseButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        finishLp.rightMargin = dp(7);
        mainControls.addView(finishButton, finishLp);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, dp(54));
        controlsLp.topMargin = dp(7);
        root.addView(mainControls, controlsLp);

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        Button settings = button("إعدادات GPS", Color.rgb(53, 94, 125));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
        Button history = button("سجل الرحلات", Color.rgb(55, 67, 78));
        history.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHistory(); }
        });
        secondary.addView(settings, new LinearLayout.LayoutParams(0, dp(43), 1f));
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(0, dp(43), 1f);
        historyLp.rightMargin = dp(7);
        secondary.addView(history, historyLp);
        LinearLayout.LayoutParams secondaryLp = new LinearLayout.LayoutParams(-1, dp(43));
        secondaryLp.topMargin = dp(6);
        root.addView(secondary, secondaryLp);

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
            setGpsState("GPS: الخدمة مغلقة", Color.rgb(198, 55, 49));
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
            setGpsState("GPS: الخدمة مغلقة", Color.rgb(198, 55, 49));
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
        setGpsState("GPS: جارٍ البحث عن الأقمار…", Color.rgb(218, 133, 25));
        refreshDashboard();
    }

    private void pauseTrip() {
        if (state != RUNNING) return;
        accumulatedMs = currentDurationMs();
        state = PAUSED;
        runningStartedElapsed = 0L;
        stopLocationUpdates();
        handler.removeCallbacks(ticker);
        displayedSpeedKmh = 0f;
        lastFix = null;
        distanceAnchor = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setGpsState("GPS: الرحلة متوقفة مؤقتًا", Color.rgb(53, 94, 125));
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
        lastAccuracy = 999f;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("state").remove("distance")
                .remove("maxSpeed").remove("duration").remove("startedWall").apply();
        setGpsState("GPS: تم حفظ الرحلة", Color.rgb(22, 156, 103));
        Toast.makeText(this, "تم حفظ الرحلة", Toast.LENGTH_SHORT).show();
        refreshDashboard();
    }

    private void requestLocationUpdates() {
        if (!hasPermission() || locationManager == null) return;
        boolean requested = false;
        try {
            locationManager.addGpsStatusListener(this);
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
        if (!requested) setGpsState("GPS: لا يوجد مزود موقع مفعّل", Color.rgb(198, 55, 49));
    }

    private void stopLocationUpdates() {
        if (locationManager == null) return;
        try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        try { locationManager.removeGpsStatusListener(this); } catch (Exception ignored) { }
    }

    @Override public void onGpsStatusChanged(int event) {
        if (locationManager == null || !hasPermission()) return;
        try {
            GpsStatus status = locationManager.getGpsStatus(null);
            int visible = 0;
            int used = 0;
            if (status != null) {
                Iterator<GpsSatellite> iterator = status.getSatellites().iterator();
                while (iterator.hasNext()) {
                    GpsSatellite satellite = iterator.next();
                    visible++;
                    if (satellite.usedInFix()) used++;
                }
            }
            satellitesView.setText("الأقمار: " + used + "/" + visible);
        } catch (Exception ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        if (state != RUNNING || location == null) return;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 999f;
        lastAccuracy = accuracy;
        if (!TripMath.isUsableFix(System.currentTimeMillis(), location.getTime(),
                location.hasAccuracy(), accuracy)) {
            accuracyView.setText("دقة GPS\n" + (location.hasAccuracy() ? Math.round(accuracy) + " م" : "غير متوفرة"));
            setGpsState("GPS: إشارة ضعيفة — النقطة لم تُحتسب", Color.rgb(218, 133, 25));
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
        setGpsState(accuracy <= 10f ? "GPS: إشارة ممتازة" : accuracy <= 22f ? "GPS: إشارة جيدة" : "GPS: إشارة مقبولة",
                Color.rgb(22, 156, 103));
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
        boolean cg = LocationManager.GPS_PROVIDER.equals(candidate.getProvider());
        boolean og = LocationManager.GPS_PROVIDER.equals(current.getProvider());
        if (cg && !og && ca <= oa + 12f) return true;
        if (!cg && og && dt < 7000L && ca >= oa) return false;
        if (dt > 10000L) return true;
        return ca <= oa + 6f && dt >= 0L;
    }

    private boolean ensurePermission() {
        if (hasPermission()) return true;
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        }
        setGpsState("GPS: صلاحية الموقع مطلوبة", Color.rgb(198, 55, 49));
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
        } catch (Exception ignored) { return false; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LOCATION_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startNewTrip();
        else setGpsState("GPS: لم تُمنح صلاحية الموقع", Color.rgb(198, 55, 49));
    }

    @Override public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) setGpsState("GPS: تم تشغيل الخدمة", Color.rgb(22, 156, 103));
    }

    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) setGpsState("GPS: الخدمة مغلقة", Color.rgb(198, 55, 49));
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private void setGpsState(String value, int color) {
        if (gpsStateView != null) {
            gpsStateView.setText(value);
            gpsStateView.setTextColor(color);
        }
    }

    private long currentDurationMs() {
        if (state == RUNNING && runningStartedElapsed > 0L) {
            return accumulatedMs + Math.max(0L, SystemClock.elapsedRealtime() - runningStartedElapsed);
        }
        return accumulatedMs;
    }

    private void refreshDashboard() {
        if (speedView == null) return;
        long duration = currentDurationMs();
        float average = TripMath.averageSpeedKmh(distanceMeters, duration);
        speedView.setText(String.format(Locale.US, "%.1f كم/س", state == RUNNING ? displayedSpeedKmh : 0f));
        maxSpeedView.setText(String.format(Locale.US, "الأعلى: %.1f كم/س", maxSpeedKmh));
        distanceView.setText("المسافة\n" + formatDistance(distanceMeters));
        durationView.setText("المدة\n" + formatDuration(duration));
        averageView.setText(String.format(Locale.US, "المتوسط\n%.1f كم/س", average));
        if (lastAccuracy >= 999f) accuracyView.setText("دقة GPS\nبانتظار الإشارة");
        roadView.setSpeed(state == RUNNING ? displayedSpeedKmh : 0f);
        startPauseButton.setText(state == RUNNING ? "إيقاف مؤقت" : state == PAUSED ? "متابعة الرحلة" : "بدء الرحلة");
        startPauseButton.setBackground(round(state == RUNNING ? Color.rgb(230, 145, 45) : Color.rgb(22, 156, 103), 14));
        finishButton.setEnabled(state != IDLE);
        finishButton.setAlpha(state == IDLE ? .45f : 1f);
    }

    private void saveCurrentTrip() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("state", state)
                .putFloat("distance", distanceMeters)
                .putFloat("maxSpeed", maxSpeedKmh)
                .putLong("duration", currentDurationMs())
                .putLong("startedWall", tripStartedWall)
                .apply();
    }

    private void loadInterruptedTrip() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int saved = p.getInt("state", IDLE);
        distanceMeters = p.getFloat("distance", 0f);
        maxSpeedKmh = p.getFloat("maxSpeed", 0f);
        accumulatedMs = p.getLong("duration", 0L);
        tripStartedWall = p.getLong("startedWall", 0L);
        state = saved == IDLE ? IDLE : PAUSED;
    }

    private void saveHistoryRecord(long start, long duration, float distance, float maxSpeed) {
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(HISTORY_KEY, "[]"));
            JSONArray updated = new JSONArray();
            JSONObject record = new JSONObject();
            record.put("start", start);
            record.put("duration", duration);
            record.put("distance", distance);
            record.put("maxSpeed", maxSpeed);
            updated.put(record);
            for (int i = 0; i < old.length() && i < 49; i++) updated.put(old.getJSONObject(i));
            p.edit().putString(HISTORY_KEY, updated.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showHistory() {
        StringBuilder body = new StringBuilder();
        try {
            JSONArray history = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(HISTORY_KEY, "[]"));
            SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm", new Locale("ar"));
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.getJSONObject(i);
                body.append(i + 1).append(". ").append(df.format(new Date(item.optLong("start")))).append("\n")
                        .append("المسافة: ").append(formatDistance((float)item.optDouble("distance")))
                        .append(" — المدة: ").append(formatDuration(item.optLong("duration"))).append("\n\n");
            }
        } catch (Exception ignored) { }
        if (body.length() == 0) body.append("لا توجد رحلات محفوظة بعد.");
        new AlertDialog.Builder(this).setTitle("سجل الرحلات").setMessage(body.toString())
                .setPositiveButton("إغلاق", null).show();
    }

    private TextView stat(String title, String value) {
        TextView view = text(title + "\n" + value, 12, Color.rgb(30, 62, 74), true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), dp(3), dp(4), dp(3));
        view.setBackground(round(Color.WHITE, 12));
        return view;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setBackground(round(color, 14));
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private String formatDistance(float meters) {
        return meters < 1000f ? Math.round(meters) + " م" : String.format(Locale.US, "%.2f كم", meters / 1000f);
    }

    private String formatDuration(long millis) {
        long total = Math.max(0L, millis / 1000L);
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600L, (total / 60L) % 60L, total % 60L);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onPause() {
        super.onPause();
        if (state == RUNNING) saveCurrentTrip();
    }

    @Override protected void onDestroy() {
        stopLocationUpdates();
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
