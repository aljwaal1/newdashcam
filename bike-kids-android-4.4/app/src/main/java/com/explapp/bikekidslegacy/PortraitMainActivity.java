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

public final class PortraitMainActivity extends Activity implements LocationListener {
    private static final int REQUEST_LOCATION = 7302;

    private LocationManager locationManager;
    private SharedPreferences prefs;
    private AdventureView adventureView;
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

        adventureView = new AdventureView(this);
        adventureView.updateRide(0, distanceKm, bestSpeed, stars, 0);
        setContentView(adventureView);
        startGps();
    }

    private void startGps() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                adventureView.setGpsState("GPS مغلق");
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
            adventureView.setGpsState("GPS يبحث عن الموقع");
        } catch (Exception e) {
            adventureView.setGpsState("GPS غير متاح");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGps();
            else adventureView.setGpsState("اسمح بالموقع لقياس السرعة");
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
        int earnedStars = (int)Math.floor(distanceKm * 10.0);
        if (earnedStars > stars) {
            stars = earnedStars;
            Toast.makeText(this, "نجمة جديدة! أحسنت يا بطل", Toast.LENGTH_SHORT).show();
        }

        prefs.edit()
                .putLong("distance", Double.doubleToLongBits(distanceKm))
                .putLong("best", Double.doubleToLongBits(bestSpeed))
                .putInt("stars", stars)
                .apply();

        adventureView.updateRide(speed, distanceKm, bestSpeed, stars, location.getAccuracy());
    }

    @Override public void onProviderEnabled(String provider) { startGps(); }

    @Override
    public void onProviderDisabled(String provider) {
        adventureView.setGpsState("GPS مغلق");
        adventureView.updateRide(0, distanceKm, bestSpeed, stars, 0);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private void resetAdventure() {
        distanceKm = 0;
        bestSpeed = 0;
        stars = 0;
        lastLocation = null;
        prefs.edit().clear().apply();
        adventureView.updateRide(0, 0, 0, 0, 0);
        Toast.makeText(this, "بدأت مغامرة جديدة", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    private final class AdventureView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler animator = new Handler();

        private double speed;
        private double distance;
        private double best;
        private int starCount;
        private String gpsState = "GPS...";
        private float wheelAngle;
        private float roadOffset;
        private float pedalBounce;
        private long lastFrame = System.currentTimeMillis();

        private final Runnable frame = new Runnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                float delta = Math.min(.05f, (now - lastFrame) / 1000f);
                lastFrame = now;
                float motion = speed < .5 ? 0f : (float)Math.max(.35, speed / 7.0);
                wheelAngle = (wheelAngle + 300f * motion * delta) % 360f;
                roadOffset = (roadOffset + 185f * motion * delta) % 170f;
                pedalBounce += delta * (motion > 0 ? 8f * motion : 1.4f);
                invalidate();
                animator.postDelayed(this, 33L);
            }
        };

        AdventureView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            animator.post(frame);
        }

        void setGpsState(String value) {
            gpsState = value;
            invalidate();
        }

        void updateRide(double currentSpeed, double currentDistance, double currentBest, int currentStars, float accuracy) {
            speed = currentSpeed;
            distance = currentDistance;
            best = currentBest;
            starCount = currentStars;
            if (accuracy > 0) gpsState = "GPS متصل ±" + Math.round(accuracy) + "م";
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            animator.removeCallbacksAndMessages(null);
            super.onDetachedFromWindow();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && event.getY() > getHeight() * .90f) {
                resetAdventure();
                return true;
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();

            drawBackground(canvas, w, h);
            drawTitle(canvas, w, h);
            drawTopStats(canvas, w, h);
            drawGpsCard(canvas, w, h);
            drawMilestones(canvas, w, h);

            float riderSize = Math.min(w * .88f, h * .40f);
            drawBikeRider(canvas, w * .50f, h * .405f, riderSize);

            float gaugeRadius = Math.min(w * .225f, h * .13f);
            drawGauge(canvas, w * .50f, h * .715f, gaugeRadius);
            drawEnergy(canvas, w, h);
            drawStartButton(canvas, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(72, 190, 245));
            c.drawRect(0, 0, w, h * .58f, paint);

            paint.setColor(Color.rgb(255, 222, 69));
            c.drawCircle(w * .84f, h * .19f, w * .075f, paint);
            drawSunFace(c, w * .84f, h * .19f, w * .075f);

            paint.setColor(Color.WHITE);
            drawCloud(c, w * .16f - roadOffset * .08f, h * .18f, w * .035f);
            drawCloud(c, w * .67f - roadOffset * .05f, h * .28f, w * .026f);

            paint.setColor(Color.rgb(34, 197, 94));
            Path hills = new Path();
            hills.moveTo(0, h * .48f);
            hills.quadTo(w * .20f, h * .37f, w * .40f, h * .49f);
            hills.quadTo(w * .60f, h * .37f, w * .82f, h * .49f);
            hills.quadTo(w * .93f, h * .43f, w, h * .48f);
            hills.lineTo(w, h * .66f);
            hills.lineTo(0, h * .66f);
            hills.close();
            c.drawPath(hills, paint);

            paint.setColor(Color.rgb(52, 64, 74));
            Path road = new Path();
            road.moveTo(w * .32f, h * .52f);
            road.lineTo(w * .68f, h * .52f);
            road.lineTo(w, h);
            road.lineTo(0, h);
            road.close();
            c.drawPath(road, paint);

            paint.setColor(Color.rgb(250, 204, 21));
            for (float y = h * .56f + roadOffset; y < h; y += h * .10f) {
                float ratio = (y - h * .52f) / (h * .48f);
                float half = w * (.015f + ratio * .045f);
                c.drawRoundRect(new RectF(w / 2f - half, y, w / 2f + half, y + h * .025f), 8, 8, paint);
            }
        }

        private void drawTitle(Canvas c, float w, float h) {
            paint.setShadowLayer(9, 0, 4, Color.argb(80, 0, 0, 0));
            paint.setColor(Color.rgb(20, 72, 155));
            c.drawRoundRect(new RectF(w * .22f, h * .018f, w * .78f, h * .082f), 24, 24, paint);
            paint.clearShadowLayer();
            drawText(c, "مغامرة دراجتي", w * .50f, h * .061f, h * .035f, Color.WHITE, true);
            drawMiniBike(c, w * .29f, h * .050f, w * .055f, Color.rgb(132, 204, 22));
        }

        private void drawTopStats(Canvas c, float w, float h) {
            float top = h * .095f;
            float bottom = h * .165f;
            float cardW = w * .29f;
            float gap = w * .02f;
            float left1 = w * .025f;
            float left2 = left1 + cardW + gap;
            float left3 = left2 + cardW + gap;

            drawStatCard(c, new RectF(left1, top, left1 + cardW, bottom), Color.rgb(10, 73, 145), 0, "أعلى سرعة", Math.round(best) + " km/h");
            drawStatCard(c, new RectF(left2, top, left2 + cardW, bottom), Color.rgb(91, 33, 182), 1, "النجوم", String.valueOf(starCount));
            drawStatCard(c, new RectF(left3, top, left3 + cardW, bottom), Color.rgb(3, 105, 161), 2, "المسافة", String.format(Locale.US, "%.2f km", distance));
        }

        private void drawStatCard(Canvas c, RectF rect, int color, int icon, String title, String value) {
            paint.setShadowLayer(8, 0, 4, Color.argb(65, 0, 0, 0));
            paint.setColor(color);
            c.drawRoundRect(rect, 20, 20, paint);
            paint.clearShadowLayer();

            float iconX = rect.left + rect.width() * .22f;
            float iconY = rect.centerY();
            float iconSize = rect.height() * .24f;
            if (icon == 0) drawTrophy(c, iconX, iconY, iconSize, Color.rgb(250, 204, 21));
            else if (icon == 1) drawStar(c, iconX, iconY, iconSize * 1.15f, Color.rgb(250, 204, 21));
            else drawPin(c, iconX, iconY, iconSize, Color.rgb(248, 113, 113));

            drawText(c, title, rect.left + rect.width() * .66f, rect.top + rect.height() * .34f, rect.height() * .18f, Color.WHITE, true);
            drawText(c, value, rect.left + rect.width() * .66f, rect.top + rect.height() * .72f, rect.height() * .25f, Color.WHITE, true);
        }

        private void drawGpsCard(Canvas c, float w, float h) {
            RectF rect = new RectF(w * .19f, h * .175f, w * .81f, h * .218f);
            int connected = gpsState.startsWith("GPS متصل") ? Color.rgb(22, 163, 74) : Color.rgb(234, 179, 8);
            paint.setColor(Color.argb(235, 255, 255, 255));
            c.drawRoundRect(rect, 18, 18, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(connected);
            c.drawRoundRect(rect, 18, 18, paint);
            paint.setStyle(Paint.Style.FILL);
            drawSatellite(c, rect.left + rect.height() * .70f, rect.centerY(), rect.height() * .23f, connected);
            drawText(c, gpsState, rect.centerX() + rect.width() * .05f, rect.centerY() + rect.height() * .04f, rect.height() * .34f, connected, true);
        }

        private void drawMilestones(Canvas c, float w, float h) {
            float y = h * .255f;
            drawMilestone(c, w * .22f, y, w * .055f, 10, speed >= 10, 0);
            drawMilestone(c, w * .50f, y, w * .055f, 20, speed >= 20, 1);
            drawMilestone(c, w * .78f, y, w * .055f, 30, speed >= 30, 2);
        }

        private void drawMilestone(Canvas c, float cx, float cy, float r, int target, boolean unlocked, int icon) {
            paint.setColor(unlocked ? Color.rgb(255, 247, 214) : Color.argb(205, 255, 255, 255));
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(unlocked ? Color.rgb(34, 197, 94) : Color.rgb(148, 163, 184));
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);

            if (icon == 0) drawStar(c, cx, cy - r * .12f, r * .34f, unlocked ? Color.rgb(250, 204, 21) : Color.rgb(203, 213, 225));
            else if (icon == 1) drawFirework(c, cx, cy - r * .12f, r * .36f, unlocked);
            else drawTrophy(c, cx, cy - r * .12f, r * .28f, unlocked ? Color.rgb(250, 204, 21) : Color.rgb(203, 213, 225));
            drawText(c, target + "", cx, cy + r * .58f, r * .32f, Color.rgb(30, 41, 59), true);
        }

        private void drawBikeRider(Canvas c, float cx, float cy, float s) {
            float bounce = speed > .5 ? (float)Math.sin(pedalBounce) * s * .012f : 0;
            cy += bounce;
            float wheelR = s * .17f;
            float wheelY = cy + s * .23f;
            float leftX = cx - s * .25f;
            float rightX = cx + s * .25f;

            drawWheel(c, leftX, wheelY, wheelR);
            drawWheel(c, rightX, wheelY, wheelR);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(s * .032f);
            paint.setColor(Color.rgb(239, 68, 68));
            float seatX = cx - s * .06f;
            float seatY = cy + s * .02f;
            float crankX = cx + s * .02f;
            float crankY = cy + s * .18f;
            c.drawLine(leftX, wheelY, seatX, seatY, paint);
            c.drawLine(seatX, seatY, crankX, crankY, paint);
            c.drawLine(crankX, crankY, leftX, wheelY, paint);
            c.drawLine(crankX, crankY, rightX, wheelY, paint);
            c.drawLine(seatX, seatY, rightX, wheelY, paint);
            c.drawLine(rightX, wheelY, cx + s * .17f, cy - s * .08f, paint);
            c.drawLine(cx + s * .12f, cy - s * .08f, cx + s * .27f, cy - s * .08f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(30, 41, 59));
            c.drawRoundRect(new RectF(seatX - s * .07f, seatY - s * .035f, seatX + s * .04f, seatY + s * .01f), 9, 9, paint);

            float pedal = (float)Math.toRadians(wheelAngle);
            float footX = crankX + (float)Math.cos(pedal) * s * .075f;
            float footY = crankY + (float)Math.sin(pedal) * s * .075f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(s * .042f);
            paint.setColor(Color.rgb(30, 64, 175));
            c.drawLine(seatX, seatY, footX, footY, paint);
            c.drawLine(seatX, seatY, crankX - (footX - crankX), crankY - (footY - crankY), paint);

            float headX = seatX - s * .005f;
            float headY = cy - s * .25f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 205, 148));
            c.drawCircle(headX, headY, s * .095f, paint);

            paint.setColor(Color.rgb(37, 99, 235));
            c.drawArc(new RectF(headX - s * .105f, headY - s * .12f, headX + s * .105f, headY + s * .065f), 185, 175, true, paint);
            paint.setColor(Color.rgb(29, 78, 216));
            c.drawRoundRect(new RectF(headX - s * .105f, headY - s * .02f, headX + s * .105f, headY + s * .01f), 6, 6, paint);

            paint.setColor(Color.WHITE);
            c.drawCircle(headX - s * .028f, headY - s * .006f, s * .019f, paint);
            c.drawCircle(headX + s * .035f, headY - s * .006f, s * .019f, paint);
            paint.setColor(Color.rgb(45, 35, 30));
            c.drawCircle(headX - s * .025f, headY - s * .004f, s * .009f, paint);
            c.drawCircle(headX + s * .038f, headY - s * .004f, s * .009f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * .010f);
            paint.setColor(Color.rgb(150, 55, 35));
            c.drawArc(new RectF(headX - s * .035f, headY + s * .018f, headX + s * .045f, headY + s * .075f), 10, 160, false, paint);

            paint.setStrokeWidth(s * .055f);
            paint.setColor(Color.rgb(37, 99, 235));
            c.drawLine(seatX, cy - s * .14f, seatX, seatY - s * .01f, paint);
            paint.setColor(Color.rgb(255, 205, 148));
            paint.setStrokeWidth(s * .025f);
            c.drawLine(seatX, cy - s * .13f, cx + s * .18f, cy - s * .08f, paint);
            paint.setStyle(Paint.Style.FILL);

            if (speed > 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3);
                paint.setColor(Color.argb(150, 255, 255, 255));
                for (int i = 0; i < 3; i++) {
                    float x = leftX - s * (.22f + i * .07f);
                    c.drawLine(x, wheelY - s * .12f, x - s * .08f, wheelY - s * .12f, paint);
                }
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawWheel(Canvas c, float cx, float cy, float r) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * .13f);
            paint.setColor(Color.rgb(30, 41, 59));
            c.drawCircle(cx, cy, r, paint);
            paint.setStrokeWidth(r * .035f);
            paint.setColor(Color.rgb(203, 213, 225));
            for (int i = 0; i < 10; i++) {
                double angle = Math.toRadians(wheelAngle + i * 36);
                c.drawLine(cx, cy, cx + (float)Math.cos(angle) * r * .88f, cy + (float)Math.sin(angle) * r * .88f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(71, 85, 105));
            c.drawCircle(cx, cy, r * .12f, paint);
        }

        private void drawGauge(Canvas c, float cx, float cy, float r) {
            paint.setShadowLayer(16, 0, 7, Color.argb(90, 0, 0, 0));
            paint.setColor(Color.rgb(8, 52, 94));
            c.drawCircle(cx, cy, r, paint);
            paint.clearShadowLayer();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(r * .12f);
            RectF arc = new RectF(cx - r * .82f, cy - r * .82f, cx + r * .82f, cy + r * .82f);
            paint.setColor(Color.rgb(34, 197, 94)); c.drawArc(arc, 135, 92, false, paint);
            paint.setColor(Color.rgb(250, 204, 21)); c.drawArc(arc, 229, 65, false, paint);
            paint.setColor(Color.rgb(249, 115, 22)); c.drawArc(arc, 297, 53, false, paint);
            paint.setColor(Color.rgb(239, 68, 68)); c.drawArc(arc, 353, 52, false, paint);

            float fraction = (float)Math.min(1, speed / 40.0);
            float angle = 135 + fraction * 270;
            double radians = Math.toRadians(angle);
            paint.setStrokeWidth(r * .045f);
            paint.setColor(Color.WHITE);
            c.drawLine(cx, cy, cx + (float)Math.cos(radians) * r * .62f, cy + (float)Math.sin(radians) * r * .62f, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * .07f, paint);

            drawMiniBike(c, cx, cy - r * .38f, r * .20f, Color.rgb(132, 204, 22));
            drawCloudIcon(c, cx - r * .52f, cy + r * .08f, r * .12f);
            drawCloudIcon(c, cx + r * .52f, cy + r * .08f, r * .12f);
            drawTree(c, cx - r * .43f, cy + r * .50f, r * .13f);
            drawTree(c, cx + r * .43f, cy + r * .50f, r * .13f);

            int speedColor = speed < 15 ? Color.rgb(163, 230, 53) : speed < 25 ? Color.rgb(250, 204, 21) : Color.rgb(248, 113, 113);
            drawText(c, String.valueOf(Math.round(speed)), cx, cy + r * .24f, r * .44f, Color.WHITE, true);
            drawText(c, "km/h", cx, cy + r * .49f, r * .15f, speedColor, true);
        }

        private void drawEnergy(Canvas c, float w, float h) {
            float y = h * .855f;
            drawText(c, "الطاقة", w * .20f, y - h * .025f, h * .021f, Color.WHITE, true);
            for (int i = 0; i < 5; i++) drawHeart(c, w * (.11f + i * .045f), y + h * .012f, w * .017f, Color.rgb(132, 204, 22));

            String encouragement = speed >= 30 ? "بطل الدراجة!" : speed >= 20 ? "رائع جدًا!" : speed >= 10 ? "أحسنت يا بطل!" : speed > 0 ? "استمر يا بطل" : "جاهز للمغامرة";
            RectF message = new RectF(w * .39f, h * .825f, w * .94f, h * .882f);
            paint.setColor(Color.argb(225, 255, 247, 214));
            c.drawRoundRect(message, 18, 18, paint);
            drawStar(c, message.left + message.height() * .55f, message.centerY(), message.height() * .25f, Color.rgb(250, 204, 21));
            drawText(c, encouragement, message.centerX() + message.width() * .05f, message.centerY() + message.height() * .05f, message.height() * .28f, Color.rgb(120, 53, 15), true);
        }

        private void drawStartButton(Canvas c, float w, float h) {
            RectF rect = new RectF(w * .12f, h * .905f, w * .88f, h * .974f);
            paint.setShadowLayer(10, 0, 5, Color.argb(90, 0, 0, 0));
            paint.setColor(Color.rgb(76, 175, 20));
            c.drawRoundRect(rect, 30, 30, paint);
            paint.clearShadowLayer();
            drawPlay(c, rect.left + rect.height() * .65f, rect.centerY(), rect.height() * .24f, Color.WHITE);
            drawText(c, "ابدأ مغامرة جديدة", rect.centerX() + rect.width() * .05f, rect.centerY() + rect.height() * .05f, rect.height() * .30f, Color.WHITE, true);
        }

        private void drawSunFace(Canvas c, float cx, float cy, float r) {
            paint.setColor(Color.rgb(80, 50, 20));
            c.drawCircle(cx - r * .25f, cy - r * .10f, r * .07f, paint);
            c.drawCircle(cx + r * .25f, cy - r * .10f, r * .07f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, r * .07f));
            c.drawArc(new RectF(cx - r * .35f, cy, cx + r * .35f, cy + r * .40f), 10, 160, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCloud(Canvas c, float x, float y, float s) {
            while (x < -s * 6) x += getWidth() + s * 10;
            c.drawCircle(x, y, s, paint);
            c.drawCircle(x + s * 1.2f, y - s * .35f, s * 1.25f, paint);
            c.drawCircle(x + s * 2.5f, y, s, paint);
            c.drawRoundRect(new RectF(x - s, y, x + s * 3.5f, y + s * 1.1f), s, s, paint);
        }

        private void drawCloudIcon(Canvas c, float cx, float cy, float s) {
            paint.setColor(Color.rgb(186, 230, 253));
            c.drawCircle(cx - s * .45f, cy, s * .50f, paint);
            c.drawCircle(cx, cy - s * .20f, s * .65f, paint);
            c.drawCircle(cx + s * .55f, cy, s * .48f, paint);
            c.drawRoundRect(new RectF(cx - s, cy, cx + s, cy + s * .48f), s * .30f, s * .30f, paint);
        }

        private void drawTree(Canvas c, float cx, float cy, float s) {
            paint.setColor(Color.rgb(120, 53, 15));
            c.drawRoundRect(new RectF(cx - s * .12f, cy, cx + s * .12f, cy + s * .70f), s * .07f, s * .07f, paint);
            paint.setColor(Color.rgb(34, 197, 94));
            c.drawCircle(cx, cy - s * .18f, s * .46f, paint);
            c.drawCircle(cx - s * .28f, cy + s * .02f, s * .34f, paint);
            c.drawCircle(cx + s * .28f, cy + s * .02f, s * .34f, paint);
        }

        private void drawMiniBike(Canvas c, float cx, float cy, float s, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, s * .11f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color);
            float left = cx - s * .75f;
            float right = cx + s * .75f;
            float wheelY = cy + s * .35f;
            c.drawCircle(left, wheelY, s * .38f, paint);
            c.drawCircle(right, wheelY, s * .38f, paint);
            c.drawLine(left, wheelY, cx - s * .10f, cy - s * .18f, paint);
            c.drawLine(cx - s * .10f, cy - s * .18f, cx + s * .10f, wheelY, paint);
            c.drawLine(cx + s * .10f, wheelY, left, wheelY, paint);
            c.drawLine(cx + s * .10f, wheelY, right, wheelY, paint);
            c.drawLine(right, wheelY, cx + s * .50f, cy - s * .20f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawTrophy(Canvas c, float cx, float cy, float s, int color) {
            paint.setColor(color);
            c.drawRoundRect(new RectF(cx - s * .55f, cy - s * .58f, cx + s * .55f, cy + s * .15f), s * .18f, s * .18f, paint);
            c.drawRect(cx - s * .12f, cy + s * .10f, cx + s * .12f, cy + s * .55f, paint);
            c.drawRoundRect(new RectF(cx - s * .42f, cy + s * .48f, cx + s * .42f, cy + s * .72f), s * .10f, s * .10f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, s * .16f));
            c.drawArc(new RectF(cx - s * .88f, cy - s * .48f, cx - s * .30f, cy + s * .12f), 75, 205, false, paint);
            c.drawArc(new RectF(cx + s * .30f, cy - s * .48f, cx + s * .88f, cy + s * .12f), -105, 205, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawStar(Canvas c, float cx, float cy, float r, int color) {
            Path star = new Path();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                float radius = i % 2 == 0 ? r : r * .43f;
                float x = cx + (float)Math.cos(angle) * radius;
                float y = cy + (float)Math.sin(angle) * radius;
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            star.close();
            paint.setColor(color);
            c.drawPath(star, paint);
        }

        private void drawPin(Canvas c, float cx, float cy, float r, int color) {
            paint.setColor(color);
            c.drawCircle(cx, cy - r * .25f, r * .58f, paint);
            Path pin = new Path();
            pin.moveTo(cx - r * .42f, cy);
            pin.lineTo(cx, cy + r * .85f);
            pin.lineTo(cx + r * .42f, cy);
            pin.close();
            c.drawPath(pin, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(cx, cy - r * .25f, r * .20f, paint);
        }

        private void drawSatellite(Canvas c, float cx, float cy, float s, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, s * .13f));
            paint.setColor(color);
            c.drawLine(cx - s * .65f, cy - s * .65f, cx + s * .65f, cy + s * .65f, paint);
            c.drawRect(cx - s * .20f, cy - s * .20f, cx + s * .20f, cy + s * .20f, paint);
            c.drawRect(cx - s * .85f, cy - s * .70f, cx - s * .32f, cy - s * .25f, paint);
            c.drawRect(cx + s * .32f, cy + s * .25f, cx + s * .85f, cy + s * .70f, paint);
            c.drawArc(new RectF(cx + s * .15f, cy - s * .80f, cx + s * .90f, cy - s * .05f), 110, 100, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawFirework(Canvas c, float cx, float cy, float r, boolean active) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, r * .12f));
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                paint.setColor(active ? new int[]{Color.rgb(239,68,68), Color.rgb(250,204,21), Color.rgb(59,130,246), Color.rgb(168,85,247)}[i % 4] : Color.rgb(203,213,225));
                c.drawLine(cx + (float)Math.cos(angle) * r * .22f, cy + (float)Math.sin(angle) * r * .22f, cx + (float)Math.cos(angle) * r, cy + (float)Math.sin(angle) * r, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawHeart(Canvas c, float cx, float cy, float r, int color) {
            Path heart = new Path();
            heart.moveTo(cx, cy + r * .85f);
            heart.cubicTo(cx - r * 1.30f, cy, cx - r * .78f, cy - r * 1.10f, cx, cy - r * .35f);
            heart.cubicTo(cx + r * .78f, cy - r * 1.10f, cx + r * 1.30f, cy, cx, cy + r * .85f);
            heart.close();
            paint.setColor(color);
            c.drawPath(heart, paint);
        }

        private void drawPlay(Canvas c, float cx, float cy, float r, int color) {
            Path play = new Path();
            play.moveTo(cx - r * .55f, cy - r);
            play.lineTo(cx + r, cy);
            play.lineTo(cx - r * .55f, cy + r);
            play.close();
            paint.setColor(color);
            c.drawPath(play, paint);
        }

        private void drawText(Canvas c, String text, float x, float y, float size, int color, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setTextSize(size);
            paint.setColor(color);
            c.drawText(text, x, y, paint);
        }
    }
}
