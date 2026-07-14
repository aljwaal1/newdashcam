package com.explapp.bikekidslegacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Professional lightweight side-scrolling cycling scene.
 * Everything is drawn with Canvas so it stays fast on Android 4.4 devices.
 */
public final class RoadView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float speedKmh;
    private float sceneryOffset;
    private float wheelRotation;
    private long lastFrame;

    public RoadView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setSpeed(float value) {
        speedKmh = TripMath.clamp(value, 0f, TripMath.MAX_BICYCLE_SPEED_KMH);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        long now = System.currentTimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        float seconds = Math.min(0.05f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;

        float motion = speedKmh > 0.5f ? 26f + speedKmh * 4.8f : 0f;
        sceneryOffset = (sceneryOffset + seconds * motion) % Math.max(1f, w);
        wheelRotation = (wheelRotation + seconds * motion * 2.8f) % 360f;

        drawSky(canvas, w, h);
        drawCityAndWater(canvas, w, h);
        drawPark(canvas, w, h);
        drawRoad(canvas, w, h);
        drawCyclist(canvas, w * 0.50f, h * 0.78f, Math.min(w * 0.46f, h * 0.78f));
        drawMotionLines(canvas, w, h);

        if (speedKmh > 0.5f) postInvalidateDelayed(33L);
    }

    private void drawSky(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, 0f, 0f, h * 0.55f,
                Color.rgb(44, 139, 215), Color.rgb(195, 232, 249), Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h * 0.58f, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(255, 223, 111));
        c.drawCircle(w * 0.82f, h * 0.14f, Math.min(w, h) * 0.055f, paint);
        drawCloud(c, wrappedX(w * 0.13f - sceneryOffset * 0.10f, w), h * 0.14f, w * 0.085f);
        drawCloud(c, wrappedX(w * 0.59f - sceneryOffset * 0.07f, w), h * 0.25f, w * 0.065f);
    }

    private void drawCloud(Canvas c, float x, float y, float size) {
        paint.setColor(0xE6FFFFFF);
        c.drawCircle(x, y, size * 0.23f, paint);
        c.drawCircle(x + size * 0.27f, y - size * 0.10f, size * 0.31f, paint);
        c.drawCircle(x + size * 0.57f, y, size * 0.24f, paint);
        c.drawRoundRect(new RectF(x - size * 0.03f, y, x + size * 0.72f, y + size * 0.23f),
                size * 0.11f, size * 0.11f, paint);
    }

    private void drawCityAndWater(Canvas c, float w, float h) {
        float skylineBase = h * 0.43f;
        paint.setColor(Color.rgb(89, 142, 178));
        float buildingW = w * 0.045f;
        for (int i = -1; i < 24; i++) {
            float x = wrappedX(i * buildingW * 1.45f - sceneryOffset * 0.16f, w + buildingW) - buildingW;
            float bh = h * (0.05f + ((i * 37) & 3) * 0.018f);
            c.drawRect(x, skylineBase - bh, x + buildingW, skylineBase, paint);
            paint.setColor(Color.rgb(185, 215, 230));
            c.drawRect(x + buildingW * 0.20f, skylineBase - bh + bh * 0.18f,
                    x + buildingW * 0.34f, skylineBase - bh + bh * 0.31f, paint);
            c.drawRect(x + buildingW * 0.58f, skylineBase - bh + bh * 0.42f,
                    x + buildingW * 0.72f, skylineBase - bh + bh * 0.55f, paint);
            paint.setColor(Color.rgb(89, 142, 178));
        }

        paint.setColor(Color.rgb(72, 161, 205));
        c.drawRect(0f, skylineBase, w, h * 0.53f, paint);
        paint.setColor(0x55FFFFFF);
        for (int i = 0; i < 8; i++) {
            float y = skylineBase + h * (0.012f + i * 0.010f);
            c.drawRect((i % 2) * w * 0.08f, y, w * 0.48f, y + 1.5f, paint);
        }
    }

    private void drawPark(Canvas c, float w, float h) {
        float pathTop = h * 0.50f;
        paint.setColor(Color.rgb(209, 207, 194));
        c.drawRect(0f, pathTop, w, h * 0.61f, paint);
        paint.setColor(Color.rgb(160, 170, 167));
        c.drawRect(0f, h * 0.585f, w, h * 0.61f, paint);

        float treeSpacing = w * 0.29f;
        for (int i = -1; i < 7; i++) {
            float x = wrappedX(i * treeSpacing - sceneryOffset * 0.48f, w + treeSpacing) - treeSpacing * 0.2f;
            drawTree(c, x, h * 0.57f, h * 0.20f, i);
        }

        float lampSpacing = w * 0.39f;
        for (int i = -1; i < 5; i++) {
            float x = wrappedX(i * lampSpacing - sceneryOffset * 0.72f, w + lampSpacing);
            drawLamp(c, x, h * 0.58f, h * 0.18f);
        }
    }

    private void drawTree(Canvas c, float x, float ground, float size, int index) {
        paint.setColor(Color.rgb(91, 66, 45));
        c.drawRoundRect(new RectF(x - size * 0.045f, ground - size * 0.40f,
                x + size * 0.045f, ground), size * 0.02f, size * 0.02f, paint);
        int green = index % 2 == 0 ? Color.rgb(47, 139, 61) : Color.rgb(68, 158, 67);
        paint.setColor(green);
        c.drawCircle(x, ground - size * 0.60f, size * 0.27f, paint);
        c.drawCircle(x - size * 0.20f, ground - size * 0.48f, size * 0.21f, paint);
        c.drawCircle(x + size * 0.20f, ground - size * 0.48f, size * 0.22f, paint);
        paint.setColor(0x3340FF40);
        c.drawCircle(x - size * 0.08f, ground - size * 0.67f, size * 0.15f, paint);
    }

    private void drawLamp(Canvas c, float x, float ground, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, size * 0.035f));
        paint.setColor(Color.rgb(45, 56, 65));
        c.drawLine(x, ground, x, ground - size, paint);
        c.drawLine(x - size * 0.08f, ground - size, x + size * 0.08f, ground - size, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(252, 229, 151));
        c.drawCircle(x, ground - size, size * 0.055f, paint);
    }

    private void drawRoad(Canvas c, float w, float h) {
        float top = h * 0.61f;
        float bottom = h;
        paint.setShader(new LinearGradient(0f, top, 0f, bottom,
                Color.rgb(69, 76, 82), Color.rgb(37, 42, 47), Shader.TileMode.CLAMP));
        c.drawRect(0f, top, w, bottom, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(230, 230, 222));
        c.drawRect(0f, top, w, top + Math.max(2f, h * 0.009f), paint);

        float lineY = top + (bottom - top) * 0.58f;
        float dashW = w * 0.10f;
        float gapW = w * 0.065f;
        float cycle = dashW + gapW;
        float offset = sceneryOffset % cycle;
        paint.setColor(Color.rgb(244, 244, 237));
        for (float x = -cycle - offset; x < w + cycle; x += cycle) {
            c.drawRoundRect(new RectF(x, lineY, x + dashW, lineY + h * 0.014f),
                    h * 0.007f, h * 0.007f, paint);
        }

        paint.setColor(Color.rgb(226, 178, 52));
        c.drawRect(0f, h * 0.92f, w, h * 0.928f, paint);
    }

    private void drawCyclist(Canvas c, float centerX, float groundY, float size) {
        float wheelR = size * 0.165f;
        float rearX = centerX - size * 0.31f;
        float frontX = centerX + size * 0.31f;
        float wheelY = groundY - wheelR;

        paint.setColor(0x44000000);
        c.drawOval(new RectF(rearX - wheelR * 1.25f, groundY - size * 0.035f,
                frontX + wheelR * 1.35f, groundY + size * 0.035f), paint);

        drawWheel(c, rearX, wheelY, wheelR);
        drawWheel(c, frontX, wheelY, wheelR);

        float crankX = centerX - size * 0.01f;
        float crankY = wheelY - size * 0.015f;
        float seatX = centerX - size * 0.15f;
        float seatY = wheelY - size * 0.27f;
        float handleX = centerX + size * 0.20f;
        float handleY = wheelY - size * 0.30f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, size * 0.035f));
        paint.setColor(Color.rgb(209, 48, 42));
        c.drawLine(rearX, wheelY, crankX, crankY, paint);
        c.drawLine(crankX, crankY, frontX, wheelY, paint);
        c.drawLine(crankX, crankY, seatX, seatY, paint);
        c.drawLine(seatX, seatY, frontX, wheelY, paint);
        c.drawLine(seatX, seatY, handleX, handleY, paint);
        c.drawLine(handleX, handleY, frontX, wheelY, paint);

        paint.setColor(Color.rgb(26, 31, 35));
        paint.setStrokeWidth(Math.max(3f, size * 0.026f));
        c.drawLine(seatX - size * 0.08f, seatY, seatX + size * 0.05f, seatY, paint);
        c.drawLine(handleX, handleY, handleX + size * 0.08f, handleY - size * 0.03f, paint);

        // Rider torso: bent forward natural cycling posture.
        float hipX = seatX + size * 0.03f;
        float hipY = seatY - size * 0.02f;
        float shoulderX = centerX - size * 0.01f;
        float shoulderY = seatY - size * 0.31f;
        float neckX = shoulderX + size * 0.10f;
        float neckY = shoulderY - size * 0.02f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(24, 30, 35));
        path.reset();
        path.moveTo(hipX - size * 0.06f, hipY);
        path.lineTo(hipX + size * 0.08f, hipY + size * 0.01f);
        path.lineTo(neckX + size * 0.03f, neckY + size * 0.06f);
        path.lineTo(shoulderX - size * 0.08f, shoulderY - size * 0.02f);
        path.close();
        c.drawPath(path, paint);

        paint.setColor(Color.rgb(211, 54, 47));
        path.reset();
        path.moveTo(shoulderX - size * 0.07f, shoulderY);
        path.lineTo(neckX + size * 0.02f, neckY + size * 0.06f);
        path.lineTo(hipX + size * 0.04f, hipY - size * 0.01f);
        path.lineTo(hipX - size * 0.03f, hipY - size * 0.01f);
        path.close();
        c.drawPath(path, paint);

        // Arms.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(5f, size * 0.045f));
        paint.setColor(Color.rgb(196, 126, 84));
        float elbowX = centerX + size * 0.11f;
        float elbowY = shoulderY + size * 0.13f;
        c.drawLine(neckX, shoulderY + size * 0.03f, elbowX, elbowY, paint);
        c.drawLine(elbowX, elbowY, handleX + size * 0.04f, handleY, paint);

        // Bent legs with shorts.
        paint.setStrokeWidth(Math.max(7f, size * 0.060f));
        paint.setColor(Color.rgb(26, 31, 35));
        float kneeFrontX = centerX + size * 0.08f;
        float kneeFrontY = crankY - size * 0.10f;
        c.drawLine(hipX, hipY, kneeFrontX, kneeFrontY, paint);
        c.drawLine(hipX - size * 0.03f, hipY, centerX - size * 0.16f, crankY - size * 0.04f, paint);

        paint.setStrokeWidth(Math.max(5f, size * 0.043f));
        paint.setColor(Color.rgb(194, 124, 83));
        float pedalAngle = (float)Math.toRadians(wheelRotation);
        float pedal1X = crankX + (float)Math.cos(pedalAngle) * size * 0.07f;
        float pedal1Y = crankY + (float)Math.sin(pedalAngle) * size * 0.07f;
        float pedal2X = crankX - (float)Math.cos(pedalAngle) * size * 0.07f;
        float pedal2Y = crankY - (float)Math.sin(pedalAngle) * size * 0.07f;
        c.drawLine(kneeFrontX, kneeFrontY, pedal1X, pedal1Y, paint);
        c.drawLine(centerX - size * 0.16f, crankY - size * 0.04f, pedal2X, pedal2Y, paint);

        // Shoes.
        paint.setColor(Color.rgb(236, 239, 242));
        paint.setStrokeWidth(Math.max(4f, size * 0.035f));
        c.drawLine(pedal1X - size * 0.03f, pedal1Y, pedal1X + size * 0.05f, pedal1Y, paint);
        c.drawLine(pedal2X - size * 0.03f, pedal2Y, pedal2X + size * 0.05f, pedal2Y, paint);

        // Head and helmet.
        paint.setStyle(Paint.Style.FILL);
        float headX = neckX + size * 0.035f;
        float headY = neckY - size * 0.09f;
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

        // Helmet vents and glasses.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, size * 0.012f));
        paint.setColor(Color.rgb(102, 111, 118));
        c.drawLine(headX - size * 0.035f, headY - size * 0.068f,
                headX + size * 0.015f, headY - size * 0.071f, paint);
        paint.setColor(Color.rgb(17, 21, 24));
        c.drawLine(headX + size * 0.025f, headY - size * 0.010f,
                headX + size * 0.085f, headY - size * 0.002f, paint);

        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWheel(Canvas c, float x, float y, float radius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(18, 22, 25));
        paint.setStrokeWidth(Math.max(4f, radius * 0.09f));
        c.drawCircle(x, y, radius, paint);

        paint.setColor(Color.rgb(164, 173, 179));
        paint.setStrokeWidth(Math.max(1f, radius * 0.018f));
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(wheelRotation + i * 22.5f);
            c.drawLine(x, y, x + (float)Math.cos(angle) * radius * 0.91f,
                    y + (float)Math.sin(angle) * radius * 0.91f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(35, 40, 44));
        c.drawCircle(x, y, radius * 0.075f, paint);
    }

    private void drawMotionLines(Canvas c, float w, float h) {
        if (speedKmh < 8f) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.006f));
        paint.setColor(0x55FFFFFF);
        float y = h * 0.74f;
        for (int i = 0; i < 3; i++) {
            float x = w * (0.12f + i * 0.06f);
            c.drawLine(x, y + i * h * 0.035f, x + w * 0.09f, y + i * h * 0.035f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private float wrappedX(float value, float width) {
        if (width <= 0f) return value;
        float result = value % width;
        if (result < 0f) result += width;
        return result;
    }
}