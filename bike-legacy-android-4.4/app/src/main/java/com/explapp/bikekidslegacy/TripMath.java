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
        return clamp(combined * 0.35f, 3f, 18f);
    }

    public static boolean isPlausibleSegment(float meters, long elapsedMs) {
        if (Float.isNaN(meters) || Float.isInfinite(meters) || meters < 0f || elapsedMs <= 0L) return false;
        float kmh = meters / (elapsedMs / 1000f) * 3.6f;
        return kmh <= MAX_BICYCLE_SPEED_KMH && meters <= 180f;
    }

    public static boolean shouldCountSegment(float meters, long elapsedMs,
                                             float previousAccuracy, float currentAccuracy) {
        if (!isPlausibleSegment(meters, elapsedMs)) return false;
        return meters >= movementThreshold(previousAccuracy, currentAccuracy);
    }

    public static float speedKmh(float sensorMetersPerSecond, boolean hasSensorSpeed,
                                 float segmentMeters, long elapsedMs) {
        float segmentSpeed = 0f;
        if (elapsedMs > 0L && segmentMeters >= 0f &&
                !Float.isNaN(segmentMeters) && !Float.isInfinite(segmentMeters)) {
            segmentSpeed = segmentMeters / (elapsedMs / 1000f) * 3.6f;
            if (segmentSpeed < 0f || segmentSpeed > MAX_BICYCLE_SPEED_KMH) segmentSpeed = 0f;
        }

        if (hasSensorSpeed && !Float.isNaN(sensorMetersPerSecond) &&
                !Float.isInfinite(sensorMetersPerSecond) && sensorMetersPerSecond >= 0f) {
            float sensorSpeed = sensorMetersPerSecond * 3.6f;
            if (sensorSpeed <= MAX_BICYCLE_SPEED_KMH) return sensorSpeed;
        }

        return segmentSpeed;
    }

    public static float smoothSpeed(float oldSpeed, float newSpeed) {
        if (Float.isNaN(oldSpeed) || Float.isInfinite(oldSpeed)) oldSpeed = 0f;
        if (Float.isNaN(newSpeed) || Float.isInfinite(newSpeed)) newSpeed = 0f;
        if (oldSpeed <= 0.1f) return newSpeed;
        if (newSpeed < 0.8f) return oldSpeed * 0.45f;
        return oldSpeed * 0.70f + newSpeed * 0.30f;
    }

    public static float averageSpeedKmh(float distanceMeters, long durationMs) {
        if (distanceMeters <= 0f || durationMs <= 0L) return 0f;
        float hours = durationMs / 3600000f;
        if (hours <= 0f) return 0f;
        return clamp((distanceMeters / 1000f) / hours, 0f, MAX_BICYCLE_SPEED_KMH);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
