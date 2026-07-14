package com.explapp.bikekidslegacy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Locale;

public final class PortraitMainActivity extends Activity implements LocationListener {
    private static final int REQUEST_LOCATION = 7302;
    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int PAUSED = 2;

    private LocationManager locationManager;
    private SharedPreferences prefs;
    private AdventureView view;
    private Location lastFix;
    private Location distanceAnchor;
    private int state = IDLE;
    private double distanceKm;
    private double speedKmh;
    private double bestSpeed;
    private float accuracy = -1f;
    private long accumulatedMs;
    private long runStartedElapsed;
    private long tripStartedWall;
    private String gpsState = "جاهز لبدء المغامرة";
    private final Handler timer = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state == RUNNING) {
                view.invalidate();
                timer.postDelayed(this, 1000L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        prefs = getSharedPreferences("bike_adventure_portrait", Context.MODE_PRIVATE);
        restorePausedTrip();
        view = new AdventureView(this);
        setContentView(view);
    }

    private void startOrPause() {
        if (state == RUNNING) pauseRide();
        else startRide();
    }

    private void startRide() {
        if (!ensurePermission()) return;
        if (!hasProvider()) {
            gpsState = "خدمة الموقع متوقفة — اضغط إعدادات GPS";
            view.invalidate();
            return;
        }
        if (state == IDLE) {
            distanceKm = 0;
            speedKmh = 0;
            bestSpeed = 0;
            accuracy = -1f;
            accumulatedMs = 0L;
            tripStartedWall = System.currentTimeMillis();
        }
        state = RUNNING;
        runStartedElapsed = SystemClock.elapsedRealtime();
        lastFix = null;
        distanceAnchor = null;
        gpsState = "جاري البحث عن GPS… ابدأ في مكان مكشوف";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestUpdates();
        timer.removeCallbacks(tick);
        timer.post(tick);
        saveCurrent();
        view.invalidate();
    }

    private void pauseRide() {
        if (state != RUNNING) return;
        accumulatedMs = durationMs();
        runStartedElapsed = 0L;
        state = PAUSED;
        speedKmh = 0;
        stopUpdates();
        timer.removeCallbacks(tick);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gpsState = "المغامرة متوقفة مؤقتًا";
        saveCurrent();
        view.invalidate();
    }

    private void finishRide() {
        if (state == IDLE) {
            Toast.makeText(this, "ابدأ المغامرة أولًا", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("إنهاء المغامرة")
                .setMessage("هل تريد إنهاء الرحلة وحفظ نتيجتها؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إنهاء وحفظ", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        completeRide();
                    }
                }).show();
    }

    private void completeRide() {
        accumulatedMs = durationMs();
        state = IDLE;
        speedKmh = 0;
        stopUpdates();
        timer.removeCallbacks(tick);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs.edit()
                .putFloat("last_distance", (float) distanceKm)
                .putFloat("last_best", (float) bestSpeed)
                .putLong("last_duration", accumulatedMs)
                .remove("state").remove("distance").remove("best")
                .remove("duration").remove("started").apply();
        gpsState = "تم حفظ المغامرة بنجاح";
        Toast.makeText(this, "تم حفظ المغامرة", Toast.LENGTH_SHORT).show();
        view.invalidate();
    }

    private void openGpsSettings() {
        try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
        catch (Exception ignored) { Toast.makeText(this, "تعذر فتح إعدادات الموقع", Toast.LENGTH_SHORT).show(); }
    }

    @SuppressWarnings("MissingPermission")
    private void requestUpdates() {
        if (!hasPermission() || locationManager == null) return;
        boolean requested = false;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                seedLastLocation(lastGps);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
                requested = true;
            }
        } catch (Exception ignored) { }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                seedLastLocation(lastNetwork);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, this);
                requested = true;
            }
        } catch (Exception ignored) { }
        if (!requested) gpsState = "لا يوجد مزود موقع مفعّل";
    }

    private void seedLastLocation(Location location) {
        if (location == null) return;
        long age = Math.abs(System.currentTimeMillis() - location.getTime());
        if (age <= 120000L && (!location.hasAccuracy() || location.getAccuracy() <= 80f)) {
            lastFix = new Location(location);
        }
    }

    private void stopUpdates() {
        if (locationManager == null) return;
        try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        if (state != RUNNING || location == null) return;
        long age = location.getTime() > 0 ? Math.abs(System.currentTimeMillis() - location.getTime()) : 0L;
        if (age > 20000L) return;
        accuracy = location.hasAccuracy() ? location.getAccuracy() : 999f;
        if (accuracy > 70f) {
            gpsState = "إشارة ضعيفة ±" + Math.round(accuracy) + "م";
            view.invalidate();
            return;
        }

        double rawSpeed = location.hasSpeed() ? location.getSpeed() * 3.6 : 0;
        if (lastFix != null && !location.hasSpeed()) {
            long gap = location.getTime() - lastFix.getTime();
            if (gap > 0) rawSpeed = lastFix.distanceTo(location) / (gap / 1000.0) * 3.6;
        }
        if (rawSpeed < 0.8) rawSpeed = 0;
        speedKmh = Math.min(65, Math.max(0, speedKmh * 0.35 + rawSpeed * 0.65));
        bestSpeed = Math.max(bestSpeed, speedKmh);

        if (distanceAnchor == null) {
            distanceAnchor = new Location(location);
        } else {
            float meters = distanceAnchor.distanceTo(location);
            long gap = location.getTime() - distanceAnchor.getTime();
            float minimum = Math.max(1.5f, (distanceAnchor.getAccuracy() + accuracy) * 0.08f);
            float maximum = gap > 0 ? Math.max(18f, gap / 1000f * 18f) : 18f;
            if (gap > 0 && gap <= 20000L && meters >= minimum && meters <= maximum) {
                distanceKm += meters / 1000.0;
                distanceAnchor = new Location(location);
            } else if (gap > 20000L) {
                distanceAnchor = new Location(location);
            }
        }
        lastFix = new Location(location);
        String source = LocationManager.GPS_PROVIDER.equals(location.getProvider()) ? "GPS" : "الشبكة";
        gpsState = source + " متصل ±" + Math.round(accuracy) + "م";
        saveCurrent();
        view.invalidate();
    }

    private boolean ensurePermission() {
        if (hasPermission()) return true;
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
        gpsState = "اسمح بالموقع لقياس الرحلة";
        view.invalidate();
        return false;
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasProvider() {
        if (locationManager == null) return false;
        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) { return false; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startRide();
        else if (requestCode == REQUEST_LOCATION) { gpsState = "لم تُمنح صلاحية الموقع"; view.invalidate(); }
    }

    @Override public void onProviderEnabled(String provider) { if (state == RUNNING) requestUpdates(); }
    @Override public void onProviderDisabled(String provider) { gpsState = "GPS متوقف — فعّل الموقع"; speedKmh = 0; view.invalidate(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private long durationMs() {
        return accumulatedMs + (state == RUNNING && runStartedElapsed > 0
                ? Math.max(0L, SystemClock.elapsedRealtime() - runStartedElapsed) : 0L);
    }

    private void saveCurrent() {
        if (state == IDLE) return;
        prefs.edit().putInt("state", state).putFloat("distance", (float) distanceKm)
                .putFloat("best", (float) bestSpeed).putLong("duration", durationMs())
                .putLong("started", tripStartedWall).apply();
    }

    private void restorePausedTrip() {
        int saved = prefs.getInt("state", IDLE);
        if (saved != IDLE) {
            distanceKm = prefs.getFloat("distance", 0f);
            bestSpeed = prefs.getFloat("best", 0f);
            accumulatedMs = prefs.getLong("duration", 0L);
            tripStartedWall = prefs.getLong("started", 0L);
            state = PAUSED;
            gpsState = "تمت استعادة مغامرة متوقفة — تابع أو أنهِ";
        }
    }

    @Override protected void onPause() {
        if (state == RUNNING) pauseRide();
        super.onPause();
    }

    @Override protected void onDestroy() {
        timer.removeCallbacksAndMessages(null);
        stopUpdates();
        super.onDestroy();
    }

    private String formatDuration(long millis) {
        long sec = millis / 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600L, (sec % 3600L) / 60L, sec % 60L);
    }

    private final class AdventureView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF startRect = new RectF();
        private final RectF finishRect = new RectF();
        private final RectF settingsRect = new RectF();
        private float roadOffset;
        private long lastFrame = System.currentTimeMillis();

        AdventureView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            long now = System.currentTimeMillis();
            float delta = Math.min(.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            if (state == RUNNING) roadOffset = (roadOffset + (float)Math.max(2, speedKmh * 2.5) * delta) % 100f;

            p.setColor(Color.rgb(220, 242, 255)); c.drawRect(0, 0, w, h, p);
            p.setColor(Color.rgb(113, 188, 105)); c.drawRect(0, h * .42f, w, h * .57f, p);
            p.setColor(Color.rgb(56, 63, 72)); c.drawRect(0, h * .57f, w, h * .80f, p);
            p.setColor(Color.WHITE);
            for (float x = -100 + roadOffset; x < w + 100; x += 120) c.drawRoundRect(new RectF(x, h*.68f, x+60, h*.695f), 8, 8, p);

            text(c, "مغامرة الدراجة", w/2, h*.07f, Math.min(w*.075f, 34), Color.rgb(20,55,78), true);
            text(c, gpsState, w/2, h*.12f, Math.min(w*.039f, 18), Color.rgb(49,83,96), false);

            card(c, w*.04f, h*.16f, w*.47f, h*.34f, Color.WHITE);
            text(c, "السرعة الحالية", w*.255f, h*.205f, 16, Color.rgb(72,99,110), false);
            text(c, String.format(Locale.US, "%.1f كم/س", state == RUNNING ? speedKmh : 0), w*.255f, h*.285f, 30, Color.rgb(14,132,102), true);

            card(c, w*.53f, h*.16f, w*.96f, h*.34f, Color.WHITE);
            text(c, "المسافة", w*.745f, h*.205f, 16, Color.rgb(72,99,110), false);
            text(c, String.format(Locale.US, "%.2f كم", distanceKm), w*.745f, h*.285f, 28, Color.rgb(34,112,157), true);

            text(c, "الأعلى " + String.format(Locale.US, "%.1f كم/س", bestSpeed), w*.25f, h*.385f, 16, Color.rgb(32,64,76), true);
            text(c, formatDuration(durationMs()), w*.75f, h*.385f, 18, Color.rgb(32,64,76), true);

            drawBike(c, w*.50f, h*.56f, Math.min(w*.38f, h*.23f));

            float top = h*.82f, bottom = h*.91f;
            startRect.set(w*.04f, top, w*.48f, bottom);
            finishRect.set(w*.52f, top, w*.96f, bottom);
            button(c, startRect, state == RUNNING ? "إيقاف مؤقت" : state == PAUSED ? "متابعة الرحلة" : "بدء الرحلة",
                    state == RUNNING ? Color.rgb(232,145,42) : Color.rgb(16,151,111));
            button(c, finishRect, "إنهاء وحفظ", state == IDLE ? Color.rgb(170,175,178) : Color.rgb(202,70,65));
            settingsRect.set(w*.28f, h*.925f, w*.72f, h*.985f);
            button(c, settingsRect, "إعدادات GPS", Color.rgb(58,91,111));
            if (state == RUNNING) postInvalidateDelayed(33L);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            if (startRect.contains(e.getX(), e.getY())) startOrPause();
            else if (finishRect.contains(e.getX(), e.getY())) finishRide();
            else if (settingsRect.contains(e.getX(), e.getY())) openGpsSettings();
            return true;
        }

        private void drawBike(Canvas c, float cx, float cy, float size) {
            float r = size*.18f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(4, size*.025f)); p.setColor(Color.rgb(27,55,67));
            c.drawCircle(cx-size*.28f, cy, r, p); c.drawCircle(cx+size*.28f, cy, r, p);
            c.drawLine(cx-size*.28f, cy, cx, cy-size*.18f, p); c.drawLine(cx, cy-size*.18f, cx+size*.28f, cy, p);
            c.drawLine(cx-size*.28f, cy, cx+size*.07f, cy, p); c.drawLine(cx+size*.07f, cy, cx, cy-size*.18f, p);
            c.drawLine(cx+size*.28f, cy, cx+size*.18f, cy-size*.30f, p); c.drawLine(cx+size*.18f, cy-size*.30f, cx+size*.28f, cy-size*.32f, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(234,94,60)); c.drawCircle(cx-size*.02f, cy-size*.34f, size*.07f, p);
            p.setStrokeWidth(Math.max(5, size*.035f)); c.drawLine(cx, cy-size*.27f, cx+size*.07f, cy-size*.05f, p);
            c.drawLine(cx+size*.07f, cy-size*.05f, cx+size*.22f, cy-size*.22f, p);
            p.setStyle(Paint.Style.FILL);
        }

        private void card(Canvas c, float l, float t, float r, float b, int color) {
            p.setColor(color); p.setShadowLayer(8, 0, 3, 0x33000000); c.drawRoundRect(new RectF(l,t,r,b), 22,22,p); p.clearShadowLayer();
        }

        private void button(Canvas c, RectF rect, String label, int color) {
            p.setColor(color); c.drawRoundRect(rect, 18, 18, p);
            text(c, label, rect.centerX(), rect.centerY()+6, Math.min(getWidth()*.042f, 19), Color.WHITE, true);
        }

        private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
            p.setColor(color); p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); c.drawText(s, x, y, p);
        }
    }
}