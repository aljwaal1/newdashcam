package com.explapp.bikekidslegacy;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 7001;
    private LocationManager locationManager;
    private BikeView bikeView;
    private SharedPreferences prefs;
    private Location lastLocation;
    private double distanceKm;
    private double bestSpeed;
    private int stars;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("bike_adventure", Context.MODE_PRIVATE);
        distanceKm = Double.longBitsToDouble(prefs.getLong("distance", Double.doubleToLongBits(0)));
        bestSpeed = Double.longBitsToDouble(prefs.getLong("best", Double.doubleToLongBits(0)));
        stars = prefs.getInt("stars", 0);
        bikeView = new BikeView(this);
        setContentView(bikeView);
        startGps();
    }

    private void startGps() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            bikeView.setGpsState("GPS يبحث عن الموقع");
        } catch (Exception e) {
            bikeView.setGpsState("GPS غير متاح");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGps();
            else bikeView.setGpsState("اسمح بالموقع لقياس السرعة");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        double speed = Math.max(0, location.getSpeed() * 3.6);
        if (speed < 0.8) speed = 0;
        if (speed > 60) speed = 60;

        if (lastLocation != null && location.getAccuracy() <= 35 && lastLocation.getAccuracy() <= 35) {
            float meters = lastLocation.distanceTo(location);
            if (meters > 0 && meters < 80) distanceKm += meters / 1000.0;
        }
        lastLocation = location;

        if (speed > bestSpeed) bestSpeed = speed;
        int newStars = (int)Math.floor(distanceKm * 10.0);
        if (newStars > stars) {
            stars = newStars;
            Toast.makeText(this, "نجمة جديدة! أحسنت يا بطل", Toast.LENGTH_SHORT).show();
        }
        prefs.edit()
                .putLong("distance", Double.doubleToLongBits(distanceKm))
                .putLong("best", Double.doubleToLongBits(bestSpeed))
                .putInt("stars", stars)
                .apply();
        bikeView.updateRide(speed, distanceKm, bestSpeed, stars, location.getAccuracy());
    }

    @Override public void onProviderEnabled(String provider) { bikeView.setGpsState("GPS متصل"); }
    @Override public void onProviderDisabled(String provider) { bikeView.setGpsState("GPS مغلق"); bikeView.updateRide(0, distanceKm, bestSpeed, stars, 0); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    protected void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private final class BikeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler animator = new Handler();
        private double speed;
        private double distance;
        private double best;
        private int starCount;
        private String gpsState = "GPS...";
        private float wheelAngle;
        private float roadOffset;
        private long lastFrame = System.currentTimeMillis();

        private final Runnable frame = new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
                lastFrame = now;
                float motion = (float)Math.max(0.25, speed / 8.0);
                wheelAngle = (wheelAngle + 260f * motion * dt) % 360f;
                roadOffset = (roadOffset + 160f * motion * dt) % 180f;
                invalidate();
                animator.postDelayed(this, 33);
            }
        };

        BikeView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            animator.post(frame);
        }

        void setGpsState(String value) { gpsState = value; invalidate(); }

        void updateRide(double s, double d, double b, int stars, float accuracy) {
            speed = s;
            distance = d;
            best = b;
            starCount = stars;
            gpsState = accuracy > 0 ? "GPS متصل ±" + Math.round(accuracy) + "م" : "GPS...";
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            animator.removeCallbacksAndMessages(null);
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && event.getX() < getWidth() * .22f && event.getY() > getHeight() * .72f) {
                distanceKm = 0;
                bestSpeed = 0;
                stars = 0;
                lastLocation = null;
                prefs.edit().clear().apply();
                updateRide(0, 0, 0, 0, 0);
                Toast.makeText(MainActivity.this, "بدأت مغامرة جديدة", Toast.LENGTH_SHORT).show();
                return true;
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            drawSky(c, w, h);
            drawRoad(c, w, h);
            drawBikeScene(c, w * .69f, h * .55f, Math.min(w, h) * .66f);
            drawGauge(c, w * .24f, h * .49f, Math.min(w, h) * .40f);
            drawStats(c, w, h);
            drawTopBar(c, w, h);
        }

        private void drawSky(Canvas c, float w, float h) {
            p.setColor(Color.rgb(124, 211, 252));
            c.drawRect(0, 0, w, h * .72f, p);
            p.setColor(Color.rgb(255, 244, 130));
            c.drawCircle(w * .86f, h * .16f, h * .075f, p);

            p.setColor(Color.WHITE);
            drawCloud(c, w * .58f - roadOffset * .18f, h * .16f, h * .045f);
            drawCloud(c, w * .33f - roadOffset * .11f, h * .23f, h * .035f);
            drawCloud(c, w * .80f - roadOffset * .09f, h * .28f, h * .040f);

            p.setColor(Color.rgb(34, 197, 94));
            Path hill = new Path();
            hill.moveTo(0, h * .58f);
            hill.quadTo(w * .18f, h * .37f, w * .36f, h * .58f);
            hill.quadTo(w * .54f, h * .40f, w * .72f, h * .58f);
            hill.quadTo(w * .88f, h * .43f, w, h * .58f);
            hill.lineTo(w, h * .75f);
            hill.lineTo(0, h * .75f);
            hill.close();
            c.drawPath(hill, p);
        }

        private void drawCloud(Canvas c, float x, float y, float s) {
            while (x < -s * 6) x += getWidth() + s * 8;
            c.drawCircle(x, y, s, p);
            c.drawCircle(x + s * 1.2f, y - s * .35f, s * 1.25f, p);
            c.drawCircle(x + s * 2.5f, y, s, p);
            c.drawRoundRect(new RectF(x - s, y, x + s * 3.5f, y + s * 1.1f), s, s, p);
        }

        private void drawRoad(Canvas c, float w, float h) {
            p.setColor(Color.rgb(65, 75, 85));
            c.drawRect(0, h * .70f, w, h, p);
            p.setColor(Color.rgb(244, 214, 92));
            for (float x = -180 + roadOffset; x < w + 180; x += 180) {
                c.drawRoundRect(new RectF(x, h * .84f, x + 95, h * .875f), 8, 8, p);
            }
            p.setColor(Color.rgb(250, 250, 250));
            c.drawRect(0, h * .70f, w, h * .715f, p);
        }

        private void drawBikeScene(Canvas c, float cx, float cy, float s) {
            float wheelR = s * .18f;
            float y = cy + s * .19f;
            float leftX = cx - s * .28f;
            float rightX = cx + s * .27f;

            drawWheel(c, leftX, y, wheelR);
            drawWheel(c, rightX, y, wheelR);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * .035f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(239, 68, 68));
            float seatX = cx - s * .06f;
            float seatY = cy - s * .02f;
            float crankX = cx + s * .02f;
            float crankY = cy + s * .13f;
            c.drawLine(leftX, y, seatX, seatY, p);
            c.drawLine(seatX, seatY, crankX, crankY, p);
            c.drawLine(crankX, crankY, leftX, y, p);
            c.drawLine(crankX, crankY, rightX, y, p);
            c.drawLine(seatX, seatY, rightX, y, p);
            c.drawLine(rightX, y, cx + s * .18f, cy - s * .12f, p);
            c.drawLine(cx + s * .14f, cy - s * .12f, cx + s * .26f, cy - s * .12f, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(30, 41, 59));
            c.drawRoundRect(new RectF(seatX - s * .08f, seatY - s * .035f, seatX + s * .04f, seatY + s * .01f), s * .02f, s * .02f, p);

            float pedal = (float)Math.toRadians(wheelAngle);
            float footX = crankX + (float)Math.cos(pedal) * s * .08f;
            float footY = crankY + (float)Math.sin(pedal) * s * .08f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * .028f);
            p.setColor(Color.rgb(30, 64, 175));
            c.drawLine(crankX, crankY, footX, footY, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 205, 148));
            c.drawCircle(seatX - s * .01f, cy - s * .28f, s * .085f, p);
            p.setColor(Color.rgb(37, 99, 235));
            c.drawCircle(seatX - s * .015f, cy - s * .33f, s * .09f, p);
            p.setColor(Color.WHITE);
            c.drawCircle(seatX + s * .02f, cy - s * .285f, s * .012f, p);
            p.setColor(Color.rgb(30, 41, 59));
            c.drawCircle(seatX + s * .024f, cy - s * .285f, s * .006f, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(s * .048f);
            p.setColor(Color.rgb(250, 204, 21));
            c.drawLine(seatX, cy - s * .20f, seatX, seatY - s * .02f, p);
            p.setColor(Color.rgb(255, 205, 148));
            p.setStrokeWidth(s * .027f);
            c.drawLine(seatX, cy - s * .18f, cx + s * .18f, cy - s * .12f, p);
            p.setColor(Color.rgb(30, 64, 175));
            p.setStrokeWidth(s * .038f);
            c.drawLine(seatX, seatY, footX, footY, p);
            c.drawLine(seatX, seatY, crankX - (footX - crankX), crankY - (footY - crankY), p);
            p.setStyle(Paint.Style.FILL);

            if (speed >= 10) {
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(s * .10f);
                p.setColor(Color.rgb(250, 204, 21));
                c.drawText("★", cx + s * .02f, cy - s * .44f, p);
            }
        }

        private void drawWheel(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * .13f);
            p.setColor(Color.rgb(30, 41, 59));
            c.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(r * .035f);
            p.setColor(Color.rgb(203, 213, 225));
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(wheelAngle + i * 45);
                c.drawLine(cx, cy, cx + (float)Math.cos(a) * r * .88f, cy + (float)Math.sin(a) * r * .88f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(71, 85, 105));
            c.drawCircle(cx, cy, r * .12f, p);
        }

        private void drawGauge(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setShadowLayer(18, 0, 8, Color.argb(80, 0, 0, 0));
            p.setColor(Color.argb(235, 255, 255, 255));
            c.drawCircle(cx, cy, r, p);
            p.clearShadowLayer();

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * .12f);
            p.setColor(Color.rgb(34, 197, 94));
            c.drawArc(new RectF(cx-r*.76f, cy-r*.76f, cx+r*.76f, cy+r*.76f), 135, 90, false, p);
            p.setColor(Color.rgb(250, 204, 21));
            c.drawArc(new RectF(cx-r*.76f, cy-r*.76f, cx+r*.76f, cy+r*.76f), 225, 70, false, p);
            p.setColor(Color.rgb(249, 115, 22));
            c.drawArc(new RectF(cx-r*.76f, cy-r*.76f, cx+r*.76f, cy+r*.76f), 295, 55, false, p);
            p.setColor(Color.rgb(239, 68, 68));
            c.drawArc(new RectF(cx-r*.76f, cy-r*.76f, cx+r*.76f, cy+r*.76f), 350, 55, false, p);

            float fraction = (float)Math.min(1, speed / 40.0);
            float angle = 135 + fraction * 270;
            double rad = Math.toRadians(angle);
            p.setStrokeWidth(r * .055f);
            p.setColor(Color.rgb(30, 41, 59));
            c.drawLine(cx, cy, cx + (float)Math.cos(rad) * r * .60f, cy + (float)Math.sin(rad) * r * .60f, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * .09f, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans", Typeface.BOLD));
            p.setColor(speed < 15 ? Color.rgb(22, 163, 74) : speed < 25 ? Color.rgb(234, 88, 12) : Color.rgb(220, 38, 38));
            p.setTextSize(r * .48f);
            c.drawText(String.valueOf(Math.round(speed)), cx, cy + r * .30f, p);
            p.setTextSize(r * .13f);
            p.setColor(Color.rgb(71, 85, 105));
            c.drawText("كم / ساعة", cx, cy + r * .48f, p);
            p.setTextSize(r * .11f);
            p.setColor(Color.rgb(67, 56, 202));
            c.drawText(gpsState, cx, cy - r * .42f, p);
        }

        private void drawStats(Canvas c, float w, float h) {
            float y = h * .92f;
            drawPill(c, w * .20f, y, w * .19f, h * .10f, Color.rgb(59, 130, 246), "المسافة  " + String.format(Locale.US, "%.2f كم", distance));
            drawPill(c, w * .48f, y, w * .19f, h * .10f, Color.rgb(139, 92, 246), "الأعلى  " + Math.round(best));
            drawPill(c, w * .76f, y, w * .19f, h * .10f, Color.rgb(245, 158, 11), "النجوم  ★ " + starCount);

            p.setColor(Color.argb(110, 255, 255, 255));
            c.drawRoundRect(new RectF(w*.012f, h*.76f, w*.15f, h*.98f), 18, 18, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(h*.045f);
            p.setColor(Color.WHITE);
            c.drawText("بدء جديد", w*.081f, h*.89f, p);
        }

        private void drawPill(Canvas c, float cx, float cy, float width, float height, int color, String text) {
            p.setColor(Color.argb(220, Color.red(color), Color.green(color), Color.blue(color)));
            c.drawRoundRect(new RectF(cx-width/2, cy-height/2, cx+width/2, cy+height/2), height/2, height/2, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(height*.34f);
            p.setColor(Color.WHITE);
            c.drawText(text, cx, cy + height*.12f, p);
        }

        private void drawTopBar(Canvas c, float w, float h) {
            p.setColor(Color.argb(160, 79, 70, 229));
            c.drawRoundRect(new RectF(w*.02f, h*.025f, w*.47f, h*.13f), 22, 22, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(h*.058f);
            p.setColor(Color.WHITE);
            String title = speed >= 30 ? "مذهل! بطل الدراجة" : speed >= 20 ? "رائع جدًا!" : speed >= 10 ? "أحسنت يا بطل" : speed > 0 ? "هيا يا بطل" : "مغامرة دراجتي";
            c.drawText(title, w*.245f, h*.095f, p);

            p.setColor(Color.argb(150, 255, 255, 255));
            c.drawRoundRect(new RectF(w*.72f, h*.025f, w*.98f, h*.13f), 22, 22, p);
            p.setTextSize(h*.044f);
            p.setColor(Color.rgb(30, 41, 59));
            c.drawText(speed >= 20 ? "🏅 ميدالية السرعة" : speed >= 10 ? "★ نجمة السرعة" : "البس الخوذة دائمًا", w*.85f, h*.092f, p);
        }
    }
}
