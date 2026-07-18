package com.explapp.bikekidslegacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Lightweight animated cycling scene for old Android devices.
 * The view starts a child-friendly visual ride as soon as the activity begins
 * sending regular speed updates, even while GPS is still searching.
 */
public final class RoadView extends View {
    private static final long RIDING_COMMAND_TIMEOUT_MS = 1800L;
    private static final float PLAY_SPEED_KMH = 7.5f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float gpsSpeedKmh;
    private float sceneryOffset;
    private float wheelRotation;
    private float bouncePhase;
    private long lastFrameMs;
    private long lastSpeedCommandMs;
    private int speedCommandCount;

    public RoadView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * Called by the trip screen whenever the dashboard refreshes.
     * One isolated zero update at app launch keeps the scene still. Repeated updates
     * after pressing Start keep the visual ride moving while GPS searches.
     */
    public void setSpeed(float value) {
        gpsSpeedKmh = TripMath.clamp(value, 0f, TripMath.MAX_BICYCLE_SPEED_KMH);
        lastSpeedCommandMs = System.currentTimeMillis();
        speedCommandCount++;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        long now = System.currentTimeMillis();
        if (lastFrameMs == 0L) lastFrameMs = now;
        float seconds = Math.min(0.05f, Math.max(0f, (now - lastFrameMs) / 1000f));
        lastFrameMs = now;

        boolean receivingRideCommands = speedCommandCount > 1 &&
                now - lastSpeedCommandMs <= RIDING_COMMAND_TIMEOUT_MS;
        boolean riding = gpsSpeedKmh > 0.5f || receivingRideCommands;
        float visualSpeed = gpsSpeedKmh > 0.5f ? gpsSpeedKmh : (riding ? PLAY_SPEED_KMH : 0f);

        if (riding) {
            float motion = 34f + visualSpeed * 5.2f;
            sceneryOffset = (sceneryOffset + seconds * motion) % Math.max(1f, w);
            wheelRotation = (wheelRotation + seconds * motion * 3.0f) % 360f;
            bouncePhase += seconds * (5.2f + visualSpeed * 0.12f);
        }

        drawSky(canvas, w, h);
        drawCity(canvas, w, h);
        drawPark(canvas, w, h);
        drawRoad(canvas, w, h);

        float bounce = riding ? (float)Math.sin(bouncePhase) * h * 0.008f : 0f;
        drawCyclist(canvas, w * 0.50f, h * 0.80f + bounce,
                Math.min(w * 0.66f, h * 0.75f));
        if (riding) drawMotionLines(canvas, w, h, visualSpeed);

        if (riding) postInvalidateDelayed(33L);
    }

    private void drawSky(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, 0f, 0f, h * 0.54f,
                Color.rgb(42, 151, 224), Color.rgb(205, 238, 251), Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h * 0.58f, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(255, 222, 87));
        c.drawCircle(w * 0.82f, h * 0.14f, Math.min(w, h) * 0.055f, paint);
        drawCloud(c, wrap(w * 0.13f - sceneryOffset * 0.10f, w), h * 0.14f, w * 0.11f);
        drawCloud(c, wrap(w * 0.61f - sceneryOffset * 0.07f, w), h * 0.25f, w * 0.08f);
    }

    private void drawCloud(Canvas c, float x, float y, float size) {
        paint.setColor(0xE8FFFFFF);
        c.drawCircle(x, y, size * 0.23f, paint);
        c.drawCircle(x + size * 0.28f, y - size * 0.10f, size * 0.31f, paint);
        c.drawCircle(x + size * 0.58f, y, size * 0.24f, paint);
        c.drawRoundRect(new RectF(x - size * 0.03f, y, x + size * 0.73f, y + size * 0.23f),
                size * 0.11f, size * 0.11f, paint);
    }

    private void drawCity(Canvas c, float w, float h) {
        float base = h * 0.43f;
        float bw = w * 0.055f;
        for (int i = -1; i < 20; i++) {
            float x = wrap(i * bw * 1.35f - sceneryOffset * 0.17f, w + bw) - bw;
            float bh = h * (0.05f + ((i * 29) & 3) * 0.022f);
            paint.setColor(Color.rgb(91, 143, 177));
            c.drawRect(x, base - bh, x + bw, base, paint);
            paint.setColor(Color.rgb(188, 217, 232));
            c.drawRect(x + bw * 0.18f, base - bh + bh * 0.18f,
                    x + bw * 0.34f, base - bh + bh * 0.32f, paint);
            c.drawRect(x + bw * 0.58f, base - bh + bh * 0.45f,
                    x + bw * 0.74f, base - bh + bh * 0.59f, paint);
        }
        paint.setColor(Color.rgb(69, 166, 207));
        c.drawRect(0f, base, w, h * 0.52f, paint);
    }

    private void drawPark(Canvas c, float w, float h) {
        paint.setColor(Color.rgb(206, 207, 195));
        c.drawRect(0f, h * 0.50f, w, h * 0.62f, paint);
        float spacing = w * 0.30f;
        for (int i = -1; i < 7; i++) {
            float x = wrap(i * spacing - sceneryOffset * 0.48f, w + spacing) - spacing * 0.2f;
            drawTree(c, x, h * 0.58f, h * 0.19f, i);
        }
    }

    private void drawTree(Canvas c, float x, float ground, float size, int index) {
        paint.setColor(Color.rgb(92, 67, 45));
        c.drawRoundRect(new RectF(x - size * 0.045f, ground - size * 0.38f,
                x + size * 0.045f, ground), size * 0.02f, size * 0.02f, paint);
        paint.setColor(index % 2 == 0 ? Color.rgb(44, 143, 62) : Color.rgb(69, 163, 72));
        c.drawCircle(x, ground - size * 0.59f, size * 0.28f, paint);
        c.drawCircle(x - size * 0.20f, ground - size * 0.47f, size * 0.21f, paint);
        c.drawCircle(x + size * 0.20f, ground - size * 0.47f, size * 0.22f, paint);
    }

    private void drawRoad(Canvas c, float w, float h) {
        float top = h * 0.61f;
        paint.setShader(new LinearGradient(0f, top, 0f, h,
                Color.rgb(73, 80, 86), Color.rgb(36, 41, 46), Shader.TileMode.CLAMP));
        c.drawRect(0f, top, w, h, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(239, 239, 232));
        c.drawRect(0f, top, w, top + Math.max(2f, h * 0.009f), paint);

        float lineY = top + (h - top) * 0.58f;
        float dash = w * 0.12f;
        float gap = w * 0.08f;
        float cycle = dash + gap;
        float offset = sceneryOffset % cycle;
        for (float x = -cycle - offset; x < w + cycle; x += cycle) {
            c.drawRoundRect(new RectF(x, lineY, x + dash, lineY + h * 0.014f),
                    h * 0.007f, h * 0.007f, paint);
        }
        paint.setColor(Color.rgb(228, 181, 51));
        c.drawRect(0f, h * 0.93f, w, h * 0.94f, paint);
    }

    private void drawCyclist(Canvas c, float centerX, float groundY, float size) {
        float wheelR = size * 0.17f;
        float rearX = centerX - size * 0.31f;
        float frontX = centerX + size * 0.31f;
        float wheelY = groundY - wheelR;

        paint.setColor(0x43000000);
        c.drawOval(new RectF(rearX - wheelR * 1.2f, groundY - size * 0.03f,
                frontX + wheelR * 1.3f, groundY + size * 0.03f), paint);
        drawWheel(c, rearX, wheelY, wheelR);
        drawWheel(c, frontX, wheelY, wheelR);

        float crankX = centerX;
        float crankY = wheelY;
        float seatX = centerX - size * 0.15f;
        float seatY = wheelY - size * 0.27f;
        float handleX = centerX + size * 0.20f;
        float handleY = wheelY - size * 0.30f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, size * 0.035f));
        paint.setColor(Color.rgb(215, 48, 42));
        c.drawLine(rearX, wheelY, crankX, crankY, paint);
        c.drawLine(crankX, crankY, frontX, wheelY, paint);
        c.drawLine(crankX, crankY, seatX, seatY, paint);
        c.drawLine(seatX, seatY, frontX, wheelY, paint);
        c.drawLine(seatX, seatY, handleX, handleY, paint);
        c.drawLine(handleX, handleY, frontX, wheelY, paint);

        paint.setColor(Color.rgb(25, 30, 35));
        paint.setStrokeWidth(Math.max(3f, size * 0.026f));
        c.drawLine(seatX - size * 0.08f, seatY, seatX + size * 0.05f, seatY, paint);
        c.drawLine(handleX, handleY, handleX + size * 0.08f, handleY - size * 0.03f, paint);

        float hipX = seatX + size * 0.03f;
        float hipY = seatY;
        float shoulderX = centerX;
        float shoulderY = seatY - size * 0.31f;
        float neckX = shoulderX + size * 0.10f;
        float neckY = shoulderY - size * 0.02f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(30, 35, 40));
        path.reset();
        path.moveTo(hipX - size * 0.06f, hipY);
        path.lineTo(hipX + size * 0.08f, hipY);
        path.lineTo(neckX + size * 0.03f, neckY + size * 0.06f);
        path.lineTo(shoulderX - size * 0.08f, shoulderY);
        path.close();
        c.drawPath(path, paint);

        paint.setColor(Color.rgb(213, 52, 46));
        path.reset();
        path.moveTo(shoulderX - size * 0.07f, shoulderY);
        path.lineTo(neckX + size * 0.02f, neckY + size * 0.06f);
        path.lineTo(hipX + size * 0.04f, hipY);
        path.lineTo(hipX - size * 0.03f, hipY);
        path.close();
        c.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(5f, size * 0.045f));
        paint.setColor(Color.rgb(196, 126, 84));
        float elbowX = centerX + size * 0.11f;
        float elbowY = shoulderY + size * 0.13f;
        c.drawLine(neckX, shoulderY + size * 0.03f, elbowX, elbowY, paint);
        c.drawLine(elbowX, elbowY, handleX + size * 0.04f, handleY, paint);

        float pedalAngle = (float)Math.toRadians(wheelRotation);
        float pedal1X = crankX + (float)Math.cos(pedalAngle) * size * 0.075f;
        float pedal1Y = crankY + (float)Math.sin(pedalAngle) * size * 0.075f;
        float pedal2X = crankX - (float)Math.cos(pedalAngle) * size * 0.075f;
        float pedal2Y = crankY - (float)Math.sin(pedalAngle) * size * 0.075f;
        float knee1X = centerX + size * 0.09f;
        float knee1Y = crankY - size * 0.11f;
        float knee2X = centerX - size * 0.16f;
        float knee2Y = crankY - size * 0.05f;

        paint.setStrokeWidth(Math.max(7f, size * 0.06f));
        paint.setColor(Color.rgb(29, 34, 39));
        c.drawLine(hipX, hipY, knee1X, knee1Y, paint);
        c.drawLine(hipX - size * 0.03f, hipY, knee2X, knee2Y, paint);
        paint.setStrokeWidth(Math.max(5f, size * 0.043f));
        paint.setColor(Color.rgb(194, 124, 83));
        c.drawLine(knee1X, knee1Y, pedal1X, pedal1Y, paint);
        c.drawLine(knee2X, knee2Y, pedal2X, pedal2Y, paint);

        paint.setColor(Color.rgb(238, 241, 244));
        paint.setStrokeWidth(Math.max(4f, size * 0.034f));
        c.drawLine(pedal1X - size * 0.03f, pedal1Y, pedal1X + size * 0.05f, pedal1Y, paint);
        c.drawLine(pedal2X - size * 0.03f, pedal2Y, pedal2X + size * 0.05f, pedal2Y, paint);

        float headX = neckX + size * 0.035f;
        float headY = neckY - size * 0.09f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(196, 126, 84));
        c.drawCircle(headX, headY, size * 0.072f, paint);
        paint.setColor(Color.rgb(18, 23, 27));
        path.reset();
        path.moveTo(headX - size * 0.085f, headY - size * 0.015f);
        path.quadTo(headX - size * 0.025f, headY - size * 0.105f,
                headX + size * 0.095f, headY - size * 0.035f);
        path.lineTo(headX + size * 0.055f, headY + size * 0.005f);
        path.close();
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWheel(Canvas c, float x, float y, float r) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(18, 22, 25));
        paint.setStrokeWidth(Math.max(4f, r * 0.09f));
        c.drawCircle(x, y, r, paint);
        paint.setColor(Color.rgb(169, 177, 182));
        paint.setStrokeWidth(Math.max(1f, r * 0.018f));
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(wheelRotation + i * 22.5f);
            c.drawLine(x, y, x + (float)Math.cos(angle) * r * 0.91f,
                    y + (float)Math.sin(angle) * r * 0.91f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(35, 40, 44));
        c.drawCircle(x, y, r * 0.075f, paint);
    }

    private void drawMotionLines(Canvas c, float w, float h, float speed) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.006f));
        paint.setColor(speed > 12f ? 0x70FFFFFF : 0x48FFFFFF);
        for (int i = 0; i < 4; i++) {
            float y = h * (0.70f + i * 0.055f);
            float x = w * (0.06f + i * 0.025f);
            c.drawLine(x, y, x + w * (0.11f + speed * 0.002f), y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private float wrap(float value, float width) {
        if (width <= 0f) return value;
        float result = value % width;
        return result < 0f ? result + width : result;
    }
}
