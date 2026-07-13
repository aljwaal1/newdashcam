package com.explapp.bikekidslegacy;

/** Pure Java rules used by GPS tracking and covered by unit tests. */
public final class TripMath {
    public static final float MAX_ACCURACY_METERS = 35f;
    public static final long MAX_LOCATION_AGE_MS = 15000L;
    public static final float MAX_BICYCLE_SPEED_KMH = 65f;

    private TripMath() {}

    public static boolean isUsableFix(long nowMs, long fixTimeMs, boolean hasAccuracy, float accuracyMeters) {
        if (!hasAccuracy || Float.isNaN(accuracyMeters) || Float.isInfinite(accuracyMeters)) return false;
        if (accuracyMeters <= 0f || accuracyMeters > MAX_ACCURACY_METERS) return false;
        long age = nowMs - fixTimeMs;
        return age >= -2000L && age <= MAX_LOCATION_AGE_MS;
    }

    public static float movementThreshold(float previousAccuracy, float currentAccuracy) {
        float combined = Math.max(0f, previousAccuracy) + Math.max(0f, currentAccuracy);
        return clamp(combined * 0.12f, 2.5f, 12f);
    }

    public static boolean isPlausibleSegment(float meters, long elapsedMs) {
        if (Float.isNaN(meters) || Float.isInfinite(meters) || meters < 0f || elapsedMs <= 0L) return false;
        float kmh = meters / (elapsedMs / 1000f) * 3.6f;
        return kmh <= MAX_BICYCLE_SPEED_KMH && meters <= 180f;
    }

    public static float speedKmh(float sensorMetersPerSecond, boolean hasSensorSpeed,
                                 float segmentMeters, long elapsedMs) {
        float speed = 0f;
        if (hasSensorSpeed && !Float.isNaN(sensorMetersPerSecond) &&
                !Float.isInfinite(sensorMetersPerSecond) && sensorMetersPerSecond >= 0f) {
            speed = sensorMetersPerSecond * 3.6f;
        } else if (elapsedMs > 0L && segmentMeters >= 0f) {
            speed = segmentMeters / (elapsedMs / 1000f) * 3.6f;
        }
        if (Float.isNaN(speed) || Float.isInfinite(speed)) return 0f;
        return clamp(speed, 0f, MAX_BICYCLE_SPEED_KMH);
    }

    public static float smoothSpeed(float oldSpeed, float newSpeed) {
        if (oldSpeed <= 0.1f) return newSpeed;
        return oldSpeed * 0.68f + newSpeed * 0.32f;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
