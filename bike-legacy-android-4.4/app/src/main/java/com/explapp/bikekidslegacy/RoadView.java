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
 * Lightweight side-scrolling road scene drawn entirely with Canvas.
 * The bicycle travels across the street horizontally, which is clearer in landscape
 * and keeps memory usage low on Android 4.4 devices.
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
        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0f || height <= 0f) return;

        long now = System.currentTimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        float seconds = Math.min(0.05f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;

        float motion = speedKmh > 0.5f ? 22f + speedKmh * 4.2f : 0f;
        sceneryOffset = (sceneryOffset + seconds * motion) % Math.max(1f, width);
        wheelRotation = (wheelRotation + seconds * motion * 2.4f) % 360f;

        drawSky(canvas, width, height);
        drawFarLandscape(canvas, width, height);
        drawStreet(canvas, width, height);
        drawRoadsideObjects(canvas, width, height);
        drawBicycleAndRider(canvas, width * 0.45f, height * 0.72f,
                Math.min(width * 0.34f, height * 0.62f));
        drawSpeedLines(canvas, width, height);

        if (speedKmh > 0.5f) postInvalidateDelayed(33L);
    }

    private void drawSky(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, 0f, 0f, height * 0.58f,
                Color.rgb(73, 181, 232), Color.rgb(220, 244, 251), Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height * 0.58f, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(255, 220, 86));
        canvas.drawCircle(width * 0.83f, height * 0.16f,
                Math.min(width, height) * 0.065f, paint);

        drawCloud(canvas, wrappedX(width * 0.15f - sceneryOffset * 0.16f, width),
                height * 0.16f, width * 0.10f);
        drawCloud(canvas, wrappedX(width * 0.64f - sceneryOffset * 0.11f, width),
                height * 0.28f, width * 0.075f);
    }

    private void drawCloud(Canvas canvas, float x, float y, float size) {
        paint.setColor(0xEFFFFFFF);
        canvas.drawCircle(x, y, size * 0.24f, paint);
        canvas.drawCircle(x + size * 0.28f, y - size * 0.08f, size * 0.31f, paint);
        canvas.drawCircle(x + size * 0.58f, y, size * 0.23f, paint);
        canvas.drawRoundRect(new RectF(x - size * 0.04f, y,
                x + size * 0.74f, y + size * 0.24f),
                size * 0.12f, size * 0.12f, paint);
    }

    private void drawFarLandscape(Canvas canvas, float width, float height) {
        float horizon = height * 0.43f;

        paint.setColor(Color.rgb(93, 151, 132));
        path.reset();
        path.moveTo(0f, horizon);
        for (int i = 0; i <= 8; i++) {
            float x = i * width / 8f;
            float wave = (i % 2 == 0) ? height * 0.13f : height * 0.05f;
            path.lineTo(x, horizon - wave);
        }
        path.lineTo(width, height * 0.60f);
        path.lineTo(0f, height * 0.60f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(Color.rgb(87, 183, 91));
        canvas.drawRect(0f, horizon, width, height * 0.61f, paint);

        float spacing = width * 0.18f;
        for (int i = -1; i < 8; i++) {
            float x = wrappedX(i * spacing - sceneryOffset * 0.55f, width + spacing) - spacing * 0.5f;
            drawTree(canvas, x, horizon + height * 0.015f, height * 0.11f, i);
        }
    }

    private void drawTree(Canvas canvas, float x, float groundY, float size, int index) {
        paint.setColor(Color.rgb(109, 75, 45));
        canvas.drawRect(x - size * 0.08f, groundY - size * 0.10f,
                x + size * 0.08f, groundY + size * 0.46f, paint);
        paint.setColor(index % 2 == 0 ? Color.rgb(39, 133, 63) : Color.rgb(47, 151, 71));
        canvas.drawCircle(x, groundY - size * 0.10f, size * 0.34f, paint);
        canvas.drawCircle(x - size * 0.22f, groundY + size * 0.02f, size * 0.27f, paint);
        canvas.drawCircle(x + size * 0.22f, groundY + size * 0.02f, size * 0.27f, paint);
    }

    private void drawStreet(Canvas canvas, float width, float height) {
        float top = height * 0.58f;
        float bottom = height * 0.94f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(188, 199, 198));
        canvas.drawRect(0f, height * 0.53f, width, top, paint);

        paint.setColor(Color.rgb(91, 99, 106));
        canvas.drawRect(0f, top, width, bottom, paint);

        paint.setColor(Color.rgb(63, 70, 76));
        canvas.drawRect(0f, bottom, width, height, paint);

        paint.setColor(Color.rgb(244, 244, 234));
        canvas.drawRect(0f, top, width, top + Math.max(2f, height * 0.012f), paint);
        canvas.drawRect(0f, bottom - Math.max(2f, height * 0.012f),
                width, bottom, paint);

        paint.setColor(Color.rgb(249, 224, 78));
        float stripeY = top + (bottom - top) * 0.57f;
        float stripeH = Math.max(3f, height * 0.014f);
        float dash = width * 0.105f;
        float gap = width * 0.065f;
        float cycle = dash + gap;
        float offset = sceneryOffset % cycle;
        for (float x = -cycle - offset; x < width + cycle; x += cycle) {
            canvas.drawRoundRect(new RectF(x, stripeY, x + dash, stripeY + stripeH),
                    stripeH * 0.5f, stripeH * 0.5f, paint);
        }
    }

    private void drawRoadsideObjects(Canvas canvas, float width, float height) {
        float groundY = height * 0.58f;
        float spacing = width * 0.42f;
        for (int i = -1; i < 5; i++) {
            float x = wrappedX(i * spacing - sceneryOffset, width + spacing) - spacing * 0.25f;
            drawLamp(canvas, x, groundY, height * 0.18f);
        }

        float signX = wrappedX(width * 0.72f - sceneryOffset * 0.88f, width + width * 0.4f);
        drawDistanceSign(canvas, signX, groundY, height * 0.14f);
    }

    private void drawLamp(Canvas canvas, float x, float groundY, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, size * 0.055f));
        paint.setColor(Color.rgb(57, 68, 76));
        canvas.drawLine(x, groundY, x, groundY - size, paint);
        canvas.drawLine(x, groundY - size, x + size * 0.25f, groundY - size, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 226, 112));
        canvas.drawCircle(x + size * 0.28f, groundY - size, size * 0.08f, paint);
    }

    private void drawDistanceSign(Canvas canvas, float x, float groundY, float size) {
        paint.setColor(Color.rgb(52, 126, 89));
        canvas.drawRoundRect(new RectF(x, groundY - size, x + size * 0.74f,
                groundY - size * 0.48f), size * 0.08f, size * 0.08f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(size * 0.22f);
        canvas.drawText("رحلة", x + size * 0.37f, groundY - size * 0.67f, paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(65, 70, 74));
        canvas.drawRect(x + size * 0.33f, groundY - size * 0.48f,
                x + size * 0.41f, groundY, paint);
    }

    private void drawBicycleAndRider(Canvas canvas, float centerX, float groundY, float size) {
        float wheelRadius = size * 0.20f;
        float leftX = centerX - size * 0.34f;
        float rightX = centerX + size * 0.34f;
        float wheelY = groundY - wheelRadius;

        drawWheel(canvas, leftX, wheelY, wheelRadius);
        drawWheel(canvas, rightX, wheelY, wheelRadius);

        float crankX = centerX - size * 0.02f;
        float crankY = wheelY - size * 0.02f;
        float seatX = centerX - size * 0.18f;
        float seatY = wheelY - size * 0.31f;
        float handleX = centerX + size * 0.20f;
        float handleY = wheelY - size * 0.35f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, size * 0.045f));
        paint.setColor(Color.rgb(231, 74, 59));
        canvas.drawLine(leftX, wheelY, crankX, crankY, paint);
        canvas.drawLine(crankX, crankY, rightX, wheelY, paint);
        canvas.drawLine(crankX, crankY, seatX, seatY, paint);
        canvas.drawLine(seatX, seatY, rightX, wheelY, paint);
        canvas.drawLine(crankX, crankY, handleX, handleY, paint);
        canvas.drawLine(handleX, handleY, rightX, wheelY, paint);

        paint.setColor(Color.rgb(38, 48, 56));
        paint.setStrokeWidth(Math.max(3f, size * 0.035f));
        canvas.drawLine(seatX - size * 0.09f, seatY,
                seatX + size * 0.08f, seatY, paint);
        canvas.drawLine(handleX, handleY,
                handleX + size * 0.10f, handleY - size * 0.01f, paint);

        float bodyX = centerX - size * 0.10f;
        float shoulderY = seatY - size * 0.31f;
        float headX = bodyX + size * 0.02f;
        float headY = shoulderY - size * 0.17f;

        paint.setStrokeWidth(Math.max(7f, size * 0.09f));
        paint.setColor(Color.rgb(255, 176, 55));
        canvas.drawLine(seatX, seatY - size * 0.02f, bodyX, shoulderY, paint);

        paint.setStrokeWidth(Math.max(5f, size * 0.065f));
        paint.setColor(Color.rgb(39, 104, 191));
        canvas.drawLine(bodyX, shoulderY + size * 0.05f, handleX, handleY, paint);
        canvas.drawLine(seatX, seatY, crankX, crankY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(210, 142, 98));
        canvas.drawCircle(headX, headY, size * 0.105f, paint);

        paint.setColor(Color.rgb(34, 84, 166));
        path.reset();
        path.moveTo(headX - size * 0.12f, headY - size * 0.01f);
        path.quadTo(headX, headY - size * 0.16f,
                headX + size * 0.15f, headY - size * 0.01f);
        path.lineTo(headX + size * 0.05f, headY + size * 0.02f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0x26000000);
        canvas.drawOval(new RectF(leftX - wheelRadius, groundY - size * 0.02f,
                rightX + wheelRadius, groundY + size * 0.035f), paint);
    }

    private void drawWheel(Canvas canvas, float centerX, float centerY, float radius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, radius * 0.15f));
        paint.setColor(Color.rgb(35, 43, 50));
        canvas.drawCircle(centerX, centerY, radius, paint);

        paint.setStrokeWidth(Math.max(1.5f, radius * 0.05f));
        paint.setColor(Color.rgb(213, 226, 231));
        double rotation = Math.toRadians(wheelRotation);
        for (int i = 0; i < 10; i++) {
            double angle = rotation + Math.PI * 2.0 * i / 10.0;
            canvas.drawLine(centerX, centerY,
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(68, 78, 86));
        canvas.drawCircle(centerX, centerY, Math.max(2f, radius * 0.10f), paint);
    }

    private void drawSpeedLines(Canvas canvas, float width, float height) {
        if (speedKmh < 5f) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, height * 0.006f));
        paint.setColor(0x55FFFFFF);
        float factor = TripMath.clamp(speedKmh / 35f, 0.25f, 1f);
        for (int i = 0; i < 5; i++) {
            float y = height * (0.63f + i * 0.055f);
            float length = width * (0.04f + i * 0.012f) * factor;
            float x = width * (0.12f + i * 0.045f);
            canvas.drawLine(x, y, x + length, y, paint);
        }
    }

    private float wrappedX(float value, float range) {
        if (range <= 0f) return value;
        float result = value % range;
        return result < 0f ? result + range : result;
    }
}
