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

/** Lightweight code-drawn street scene; no bitmap memory cost on old phones. */
public final class RoadView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float speedKmh;
    private float laneOffset;
    private long lastFrame;

    public RoadView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setSpeed(float value) {
        speedKmh = TripMath.clamp(value, 0f, TripMath.MAX_BICYCLE_SPEED_KMH);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = System.currentTimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        float seconds = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        laneOffset = (laneOffset + seconds * (14f + speedKmh * 2.2f)) % 90f;

        drawSky(canvas, w, h);
        drawLandscape(canvas, w, h);
        drawRoad(canvas, w, h);
        drawRider(canvas, w * 0.5f, h * 0.77f, Math.min(w, h) * 0.18f);

        if (speedKmh > 0.5f) postInvalidateDelayed(32L);
    }

    private void drawSky(Canvas c, float w, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.62f,
                Color.rgb(73, 181, 232), Color.rgb(207, 239, 250), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.64f, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(255, 220, 86));
        paint.setShadowLayer(16f, 0, 0, 0x66FFF1A0);
        c.drawCircle(w * 0.82f, h * 0.16f, Math.min(w, h) * 0.075f, paint);
        paint.clearShadowLayer();

        drawCloud(c, w * 0.18f, h * 0.17f, w * 0.12f);
        drawCloud(c, w * 0.62f, h * 0.29f, w * 0.09f);
    }

    private void drawCloud(Canvas c, float x, float y, float size) {
        paint.setColor(0xEFFFFFFF);
        c.drawCircle(x, y, size * 0.28f, paint);
        c.drawCircle(x + size * 0.3f, y - size * 0.08f, size * 0.36f, paint);
        c.drawCircle(x + size * 0.65f, y, size * 0.27f, paint);
        c.drawRoundRect(new RectF(x - size * 0.05f, y, x + size * 0.82f, y + size * 0.28f),
                size * 0.14f, size * 0.14f, paint);
    }

    private void drawLandscape(Canvas c, float w, float h) {
        float horizon = h * 0.43f;
        paint.setColor(Color.rgb(80, 139, 119));
        path.reset();
        path.moveTo(0, horizon);
        path.lineTo(w * .18f, h * .28f);
        path.lineTo(w * .37f, horizon);
        path.lineTo(w * .57f, h * .25f);
        path.lineTo(w * .78f, horizon);
        path.lineTo(w, h * .31f);
        path.lineTo(w, h * .57f);
        path.lineTo(0, h * .57f);
        path.close();
        c.drawPath(path, paint);

        paint.setColor(Color.rgb(76, 177, 84));
        c.drawRect(0, horizon, w, h, paint);

        for (int i = 0; i < 7; i++) {
            float x = (i + 0.35f) * w / 7f;
            float y = horizon + (i % 2) * h * .035f;
            float s = w * .035f;
            paint.setColor(Color.rgb(104, 70, 42));
            c.drawRect(x - s * .12f, y, x + s * .12f, y + s, paint);
            paint.setColor(i % 2 == 0 ? Color.rgb(32, 124, 58) : Color.rgb(42, 146, 64));
            c.drawCircle(x, y, s * .72f, paint);
        }
    }

    private void drawRoad(Canvas c, float w, float h) {
        float horizon = h * .46f;
        paint.setColor(Color.rgb(120, 126, 133));
        path.reset();
        path.moveTo(w * .41f, horizon);
        path.lineTo(w * .59f, horizon);
        path.lineTo(w * 1.03f, h);
        path.lineTo(-w * .03f, h);
        path.close();
        c.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * .012f));
        paint.setColor(Color.rgb(241, 241, 232));
        path.reset(); path.moveTo(w * .41f, horizon); path.lineTo(-w * .03f, h); c.drawPath(path, paint);
        path.reset(); path.moveTo(w * .59f, horizon); path.lineTo(w * 1.03f, h); c.drawPath(path, paint);

        paint.setColor(Color.rgb(250, 229, 83));
        paint.setStrokeWidth(Math.max(2f, w * .009f));
        for (int i = -1; i < 7; i++) {
            float t1 = (i * 90f + laneOffset) / Math.max(1f, h);
            float t2 = ((i * 90f + laneOffset) + 42f) / Math.max(1f, h);
            t1 = TripMath.clamp(t1, 0f, 1f);
            t2 = TripMath.clamp(t2, 0f, 1f);
            float y1 = horizon + (h - horizon) * t1;
            float y2 = horizon + (h - horizon) * t2;
            float x1 = w * .5f;
            float x2 = w * .5f;
            c.drawLine(x1, y1, x2, y2, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRider(Canvas c, float x, float groundY, float size) {
        float wheelR = size * .25f;
        float leftX = x - size * .48f;
        float rightX = x + size * .48f;
        float wheelY = groundY - wheelR;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, size * .055f));
        paint.setColor(Color.rgb(35, 43, 50));
        c.drawCircle(leftX, wheelY, wheelR, paint);
        c.drawCircle(rightX, wheelY, wheelR, paint);
        paint.setStrokeWidth(Math.max(2f, size * .035f));
        paint.setColor(Color.rgb(213, 226, 231));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            c.drawLine(leftX, wheelY, leftX + (float)Math.cos(a) * wheelR,
                    wheelY + (float)Math.sin(a) * wheelR, paint);
            c.drawLine(rightX, wheelY, rightX + (float)Math.cos(a) * wheelR,
                    wheelY + (float)Math.sin(a) * wheelR, paint);
        }

        paint.setColor(Color.rgb(238, 80, 62));
        paint.setStrokeWidth(Math.max(4f, size * .07f));
        float crankX = x - size * .05f;
        float crankY = wheelY - size * .05f;
        float seatX = x - size * .23f;
        float seatY = wheelY - size * .37f;
        float handleX = x + size * .30f;
        float handleY = wheelY - size * .43f;
        c.drawLine(leftX, wheelY, crankX, crankY, paint);
        c.drawLine(crankX, crankY, rightX, wheelY, paint);
        c.drawLine(crankX, crankY, seatX, seatY, paint);
        c.drawLine(seatX, seatY, rightX, wheelY, paint);
        c.drawLine(crankX, crankY, handleX, handleY, paint);
        c.drawLine(handleX, handleY, rightX, wheelY, paint);

        paint.setColor(Color.rgb(38, 48, 56));
        paint.setStrokeWidth(Math.max(3f, size * .05f));
        c.drawLine(seatX - size * .12f, seatY, seatX + size * .08f, seatY, paint);
        c.drawLine(handleX, handleY, handleX + size * .12f, handleY - size * .02f, paint);

        // Child body is anchored to the frame, so the rider visually sits on the road.
        paint.setColor(Color.rgb(255, 176, 55));
        paint.setStrokeWidth(Math.max(6f, size * .12f));
        c.drawLine(seatX, seatY - size * .03f, x - size * .11f, seatY - size * .47f, paint);
        paint.setColor(Color.rgb(39, 104, 191));
        paint.setStrokeWidth(Math.max(5f, size * .09f));
        c.drawLine(x - size * .11f, seatY - size * .27f, handleX, handleY, paint);
        c.drawLine(seatX, seatY, crankX, crankY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(210, 142, 98));
        c.drawCircle(x - size * .10f, seatY - size * .63f, size * .17f, paint);
        paint.setColor(Color.rgb(34, 84, 166));
        path.reset();
        path.moveTo(x - size * .29f, seatY - size * .68f);
        path.quadTo(x - size * .10f, seatY - size * .86f, x + size * .08f, seatY - size * .65f);
        path.lineTo(x - size * .04f, seatY - size * .58f);
        path.close();
        c.drawPath(path, paint);

        paint.setColor(0x33000000);
        c.drawOval(new RectF(leftX - wheelR, groundY - size * .03f,
                rightX + wheelR, groundY + size * .05f), paint);
    }
}
