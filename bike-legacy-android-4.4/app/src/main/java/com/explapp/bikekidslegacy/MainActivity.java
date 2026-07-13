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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final long AUTOSAVE_INTERVAL_MS = 5000L;

    private final Handler handler = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (state == RUNNING) {
                long now = SystemClock.elapsedRealtime();
                if (lastAcceptedElapsed > 0L && now - lastAcceptedElapsed > SPEED_STALE_MS) {
                    displayedSpeedKmh = 0f;
                    if (roadView != null) roadView.setSpeed(0f);
                    if (statusView != null) {
                        statusView.setText("إشارة GPS متوقفة مؤقتًا — لم تُحتسب نقاط جديدة");
                    }
                }
                if (now - lastAutosaveElapsed >= AUTOSAVE_INTERVAL_MS) {
                    saveCurrentTrip();
                    lastAutosaveElapsed = now;
                }
                refreshDashboard();
                handler.postDelayed(this, 1000L);
            }
        }
    };

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
    private long lastAutosaveElapsed;
    private Location lastFix;
    private Location distanceAnchor;
    private boolean restoredTrip;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        loadInterruptedTrip();
        buildScreen();
        refreshDashboard();
        if (restoredTrip) {
            statusView.setText("تمت استعادة رحلة سابقة بحالة إيقاف مؤقت — تابعها أو أنهِها");
        }
    }

    private void buildScreen() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(9));
        root.setBackgroundColor(Color.rgb(235, 244, 245));
        if (Build.VERSION.SDK_INT >= 17) root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(4), dp(5));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("مغامرة الدراجة", 22, Color.rgb(18, 48, 71), true);
        TextView subtitle = text("متتبع رحلة خفيف ومتوافق مع Android 4.4", 11,
                Color.rgb(74, 100, 111), false);
        titleBlock.addView(title, match());
        titleBlock.addView(subtitle, match());
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1f));

        Button history = compactButton("سجل الرحلات", Color.rgb(40, 105, 139));
        history.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showHistory(); }
        });
        header.addView(history, new LinearLayout.LayoutParams(dp(122), dp(44)));
        root.addView(header, match());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 17) body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        roadView = new RoadView(this);
        roadView.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams roadParams = new LinearLayout.LayoutParams(0, -1, 1.55f);
        roadParams.rightMargin = dp(9);
        body.addView(roadView, roadParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 17) panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout speedCard = new LinearLayout(this);
        speedCard.setOrientation(LinearLayout.VERTICAL);
        speedCard.setGravity(Gravity.CENTER);
        speedCard.setPadding(dp(6), dp(6), dp(6), dp(6));
        speedCard.setBackground(round(Color.rgb(18, 48, 71), 16));

        TextView speedCaption = text("السرعة الحالية", 12, Color.rgb(181, 219, 231), false);
        speedCaption.setGravity(Gravity.CENTER);
        speedView = text("0.0 كم/س", 30, Color.WHITE, true);
        speedView.setGravity(Gravity.CENTER);
        maxSpeedView = text("الأعلى: 0.0 كم/س", 11, Color.rgb(181, 219, 231), false);
        maxSpeedView.setGravity(Gravity.CENTER);
        speedCard.addView(speedCaption, match());
        speedCard.addView(speedView, match());
        speedCard.addView(maxSpeedView, match());
        panel.addView(speedCard, new LinearLayout.LayoutParams(-1, dp(102)));

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        distanceView = stat("المسافة", "0 م");
        durationView = stat("المدة", "00:00:00");
        rowOne.addView(distanceView, weight());
        rowOne.addView(space(), new LinearLayout.LayoutParams(dp(6), 1));
        rowOne.addView(durationView, weight());
        panel.addView(rowOne, top(7));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        averageView = stat("المتوسط", "0.0 كم/س");
        accuracyView = stat("دقة GPS", "بانتظار الإشارة");
        rowTwo.addView(averageView, weight());
        rowTwo.addView(space(), new LinearLayout.LayoutParams(dp(6), 1));
        rowTwo.addView(accuracyView, weight());
        panel.addView(rowTwo, top(7));

        statusView = text("جاهز لبدء رحلة جديدة", 13, Color.rgb(30, 62, 74), false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(8), dp(8), dp(8), dp(8));
        statusView.setBackground(round(Color.rgb(212, 235, 232), 12));
        panel.addView(statusView, top(7));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        startPauseButton = button("بدء الرحلة", Color.rgb(19, 156, 118));
        startPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { onStartPausePressed(); }
        });
        finishButton = button("إنهاء وحفظ", Color.rgb(202, 72, 66));
        finishButton.setEnabled(false);
        finishButton.setAlpha(.45f);
        finishButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { confirmFinish(); }
        });
        controls.addView(startPauseButton, weight());
        controls.addView(space(), new LinearLayout.LayoutParams(dp(7), 1));
        controls.addView(finishButton, weight());
        panel.addView(controls, top(8));

        Button locationSettings = compactButton("إعدادات الموقع", Color.rgb(82, 105, 116));
        locationSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });
        panel.addView(locationSettings, top(6));

        body.addView(panel, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView footer = text("يبدأ احتساب المسافة بعد تثبيت GPS. النقاط الضعيفة والقفزات غير المنطقية لا تُضاف.", 11,
                Color.rgb(78, 104, 112), false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(5), dp(5), dp(5), 0);
        root.addView(footer, match());

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
            statusView.setText("شغّل خدمة الموقع أولًا، ثم اضغط بدء الرحلة");
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
        lastAutosaveElapsed = runningStartedElapsed;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestLocationUpdates();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        statusView.setText("جارٍ تثبيت إشارة GPS… ابدأ في مكان مكشوف");
        saveCurrentTrip();
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
        lastAcceptedElapsed = 0L;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        statusView.setText("الرحلة متوقفة مؤقتًا — يمكنك المتابعة أو الإنهاء والحفظ");
        saveCurrentTrip();
        refreshDashboard();
    }

    private void confirmFinish() {
        if (state == IDLE) return;
        final boolean veryShort = distanceMeters < 5f && currentDurationMs() < 20000L;
        String message = veryShort
                ? "الرحلة قصيرة جدًا. هل تريد حفظها في السجل أم تجاهلها؟"
                : "هل تريد إنهاء الرحلة وحفظها في السجل؟";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("إنهاء الرحلة")
                .setMessage(message)
                .setPositiveButton("إنهاء وحفظ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { finishTrip(true); }
                })
                .setNegativeButton("إلغاء", null);
        if (veryShort) {
            builder.setNeutralButton("تجاهل الرحلة", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { finishTrip(false); }
            });
        }
        builder.show();
    }

    private void finishTrip(boolean saveRecord) {
        long duration = currentDurationMs();
        if (state == RUNNING) {
            accumulatedMs = duration;
            stopLocationUpdates();
        }
        state = IDLE;
        handler.removeCallbacks(ticker);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (saveRecord) {
            saveHistoryRecord(tripStartedWall == 0L ? System.currentTimeMillis() - duration : tripStartedWall,
                    System.currentTimeMillis(), duration, distanceMeters, maxSpeedKmh);
            statusView.setText("تم إنهاء الرحلة وحفظها في السجل");
            Toast.makeText(this, "تم حفظ الرحلة", Toast.LENGTH_SHORT).show();
        } else {
            statusView.setText("تم تجاهل الرحلة القصيرة");
            Toast.makeText(this, "لم تُحفظ الرحلة", Toast.LENGTH_SHORT).show();
        }
        clearCurrentTrip();
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
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        if (state != RUNNING || location == null) return;
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 999f;
        lastAccuracy = accuracy;
        long nowWall = System.currentTimeMillis();

        if (!TripMath.isUsableFix(nowWall, location.getTime(), location.hasAccuracy(), accuracy)) {
            accuracyView.setText("دقة GPS\n" + (location.hasAccuracy() ? Math.round(accuracy) + " م" : "غير متوفرة"));
            statusView.setText(accuracy > TripMath.MAX_ACCURACY_METERS
                    ? "إشارة ضعيفة (" + Math.round(accuracy) + " م) — لم تُحتسب هذه النقطة"
                    : "بانتظار إشارة GPS حديثة");
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
        if (displayedSpeedKmh < 0.8f) displayedSpeedKmh = 0f;
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
        accuracyView.setText("دقة GPS\n" + Math.round(accuracy) + " م");
        statusView.setText(accuracy <= 10f ? "إشارة ممتازة — التسجيل دقيق"
                : accuracy <= 22f ? "إشارة جيدة — الرحلة قيد التسجيل"
                : "إشارة مقبولة — يفضّل مكان أكثر انفتاحًا");
        roadView.setSpeed(displayedSpeedKmh);
        refreshDashboard();
    }

    private boolean isBetterFix(Location candidate, Location current) {
        if (current == null) return true;
        long timeDelta = candidate.getTime() - current.getTime();
        if (timeDelta < -2000L) return false;
        float candidateAccuracy = candidate.hasAccuracy() ? candidate.getAccuracy() : 999f;
        float currentAccuracy = current.hasAccuracy() ? current.getAccuracy() : 999f;
        boolean candidateGps = LocationManager.GPS_PROVIDER.equals(candidate.getProvider());
        boolean currentGps = LocationManager.GPS_PROVIDER.equals(current.getProvider());

        if (candidateGps && !currentGps && candidateAccuracy <= currentAccuracy + 12f) return true;
        if (!candidateGps && currentGps && timeDelta < 7000L && candidateAccuracy >= currentAccuracy) return false;
        if (timeDelta > 10000L) return true;
        if (candidateAccuracy <= currentAccuracy + 6f && timeDelta >= 0L) return true;
        return candidateAccuracy < currentAccuracy - 5f;
    }

    private boolean ensurePermission() {
        if (hasPermission()) return true;
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
        }
        statusView.setText("صلاحية الموقع مطلوبة لقياس الرحلة");
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
        else statusView.setText("لم تُمنح صلاحية الموقع؛ لا يمكن تسجيل الرحلة");
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

        if (state == RUNNING) startPauseButton.setText("إيقاف مؤقت");
        else if (state == PAUSED) startPauseButton.setText("متابعة الرحلة");
        else startPauseButton.setText("بدء رحلة جديدة");

        startPauseButton.setBackground(round(state == RUNNING
                ? Color.rgb(230, 145, 45) : Color.rgb(19, 156, 118), 15));
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
        int savedState = p.getInt("state", IDLE);
        distanceMeters = p.getFloat("distance", 0f);
        maxSpeedKmh = p.getFloat("maxSpeed", 0f);
        accumulatedMs = p.getLong("duration", 0L);
        tripStartedWall = p.getLong("startedWall", 0L);
        restoredTrip = savedState != IDLE;
        state = restoredTrip ? PAUSED : IDLE;
    }

    private void clearCurrentTrip() {
        distanceMeters = 0f;
        maxSpeedKmh = 0f;
        displayedSpeedKmh = 0f;
        accumulatedMs = 0L;
        runningStartedElapsed = 0L;
        tripStartedWall = 0L;
        lastFix = null;
        distanceAnchor = null;
        lastAcceptedElapsed = 0L;
        lastAccuracy = 999f;
        restoredTrip = false;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove("state").remove("distance").remove("maxSpeed")
                .remove("duration").remove("startedWall").apply();
    }

    private void saveHistoryRecord(long start, long end, long duration, float distance, float maxSpeed) {
        try {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString(HISTORY_KEY, "[]"));
            JSONArray updated = new JSONArray();
            JSONObject record = new JSONObject();
            record.put("start", start);
            record.put("end", end);
            record.put("duration", duration);
            record.put("distance", Math.round(distance * 10f) / 10f);
            record.put("maxSpeed", Math.round(maxSpeed * 10f) / 10f);
            updated.put(record);
            for (int i = 0; i < old.length() && i < 99; i++) updated.put(old.getJSONObject(i));
            p.edit().putString(HISTORY_KEY, updated.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showHistory() {
        final SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        StringBuilder body = new StringBuilder();
        int count = 0;
        try {
            JSONArray history = new JSONArray(p.getString(HISTORY_KEY, "[]"));
            count = history.length();
            SimpleDateFormat date = new SimpleDateFormat("yyyy/MM/dd  HH:mm", new Locale("ar"));
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.getJSONObject(i);
                long duration = item.optLong("duration");
                float distance = (float)item.optDouble("distance");
                body.append(i + 1).append(". ")
                        .append(date.format(new Date(item.optLong("start")))).append("\n")
                        .append("المسافة: ").append(formatDistance(distance))
                        .append("  •  المدة: ").append(formatDuration(duration)).append("\n")
                        .append("المتوسط: ").append(String.format(Locale.US, "%.1f كم/س",
                                TripMath.averageSpeedKmh(distance, duration)))
                        .append("  •  الأعلى: ").append(String.format(Locale.US, "%.1f كم/س",
                                item.optDouble("maxSpeed"))).append("\n\n");
            }
        } catch (Exception ignored) { }
        if (count == 0) body.append("لا توجد رحلات محفوظة بعد.\nابدأ رحلة ثم اضغط «إنهاء وحفظ».");

        final String shareText = body.toString();
        final boolean hasItems = count > 0;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("سجل الرحلات (" + count + ")")
                .setMessage(shareText)
                .setPositiveButton("إغلاق", null);
        if (hasItems) {
            dialog.setNeutralButton("مشاركة", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { shareHistory(shareText); }
            });
            dialog.setNegativeButton("مسح السجل", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) { confirmClearHistory(); }
            });
        }
        dialog.show();
    }

    private void shareHistory(String body) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "سجل رحلات مغامرة الدراجة");
        share.putExtra(Intent.EXTRA_TEXT, "سجل رحلات مغامرة الدراجة\n\n" + body);
        try {
            startActivity(Intent.createChooser(share, "مشاركة السجل"));
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق متاح للمشاركة", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("مسح سجل الرحلات")
                .setMessage("سيتم حذف جميع الرحلات المحفوظة. لا يمكن التراجع.")
                .setPositiveButton("مسح", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(HISTORY_KEY).apply();
                        Toast.makeText(MainActivity.this, "تم مسح السجل", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    @Override public void onProviderEnabled(String provider) {
        if (state == RUNNING) statusView.setText("تم تفعيل الموقع — جارٍ تثبيت الإشارة");
    }

    @Override public void onProviderDisabled(String provider) {
        if (!hasLocationProvider() && state == RUNNING) {
            pauseTrip();
            statusView.setText("توقفت الرحلة مؤقتًا لأن خدمة الموقع أُغلقت");
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override protected void onPause() {
        super.onPause();
        if (state != IDLE) saveCurrentTrip();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopLocationUpdates();
        super.onDestroy();
    }

    private String formatDistance(float meters) {
        if (meters < 1000f) return Math.round(meters) + " م";
        return String.format(Locale.US, "%.2f كم", meters / 1000f);
    }

    private String formatDuration(long millis) {
        long total = Math.max(0L, millis / 1000L);
        return String.format(Locale.US, "%02d:%02d:%02d",
                total / 3600L, (total % 3600L) / 60L, total % 60L);
    }

    private TextView stat(String label, String value) {
        TextView v = text(label + "\n" + value, 12, Color.rgb(25, 64, 77), true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(3), dp(8), dp(3), dp(8));
        v.setBackground(round(Color.WHITE, 12));
        v.setMinHeight(dp(58));
        return v;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(color, 14));
        b.setPadding(dp(7), dp(7), dp(7), dp(7));
        b.setMinHeight(dp(48));
        return b;
    }

    private Button compactButton(String label, int color) {
        Button b = button(label, color);
        b.setTextSize(12);
        b.setMinHeight(dp(42));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }

    private View space() { return new View(this); }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = match();
        p.topMargin = dp(margin);
        return p;
    }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1f); }
}
