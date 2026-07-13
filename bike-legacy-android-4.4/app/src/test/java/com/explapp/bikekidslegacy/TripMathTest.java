package com.explapp.bikekidslegacy;

import org.junit.Test;
import static org.junit.Assert.*;

public class TripMathTest {
    @Test public void rejectsStaleAndInaccurateFixes() {
        long now = 100000L;
        assertTrue(TripMath.isUsableFix(now, 95000L, true, 12f));
        assertFalse(TripMath.isUsableFix(now, 80000L, true, 12f));
        assertFalse(TripMath.isUsableFix(now, 99000L, true, 80f));
        assertFalse(TripMath.isUsableFix(now, 99000L, false, 0f));
    }

    @Test public void filtersGpsJitterAndImpossibleJumps() {
        assertEquals(3f, TripMath.movementThreshold(3f, 3f), .01f);
        assertEquals(18f, TripMath.movementThreshold(35f, 35f), .01f);
        assertTrue(TripMath.shouldCountSegment(10f, 3000L, 7f, 7f));
        assertFalse(TripMath.shouldCountSegment(2f, 2000L, 7f, 7f));
        assertFalse(TripMath.shouldCountSegment(100f, 1000L, 7f, 7f));
        assertFalse(TripMath.isPlausibleSegment(Float.NaN, 1000L));
    }

    @Test public void speedUsesSensorWhenValidAndFallsBackWhenCorrupt() {
        assertEquals(18f, TripMath.speedKmh(5f, true, 0f, 0L), .01f);
        assertEquals(36f, TripMath.speedKmh(0f, false, 10f, 1000L), .01f);
        assertEquals(18f,
                TripMath.speedKmh(Float.POSITIVE_INFINITY, true, 10f, 2000L), .01f);
        assertEquals(18f,
                TripMath.speedKmh(100f, true, 10f, 2000L), .01f);
        assertEquals(0f,
                TripMath.speedKmh(100f, true, 100f, 1000L), .01f);
        assertEquals(13f, TripMath.smoothSpeed(10f, 20f), .01f);
        assertEquals(4.5f, TripMath.smoothSpeed(10f, 0f), .01f);
    }

    @Test public void calculatesAverageSpeedSafely() {
        assertEquals(18f, TripMath.averageSpeedKmh(5000f, 1000000L), .01f);
        assertEquals(0f, TripMath.averageSpeedKmh(0f, 1000L), .01f);
        assertEquals(0f, TripMath.averageSpeedKmh(1000f, 0L), .01f);
        assertEquals(TripMath.MAX_BICYCLE_SPEED_KMH,
                TripMath.averageSpeedKmh(100000f, 1000L), .01f);
    }

    @Test public void simulatedBikeRideCountsMovementButNotStationaryJitter() {
        float countedDistance = 0f;
        float previousAccuracy = 7f;

        // Stationary GPS drift: none of these tiny movements should be counted.
        float[] stationaryJitter = {1.2f, 2.1f, 0.8f, 2.7f, 1.5f};
        for (int i = 0; i < stationaryJitter.length; i++) {
            if (TripMath.shouldCountSegment(stationaryJitter[i], 2000L,
                    previousAccuracy, 7f)) {
                countedDistance += stationaryJitter[i];
            }
        }
        assertEquals(0f, countedDistance, .01f);

        // A realistic 100 m bicycle ride split into ten GPS updates.
        float[] rideSegments = {9.7f, 10.4f, 9.9f, 10.1f, 10.2f,
                9.8f, 10.3f, 9.6f, 10.5f, 9.9f};
        for (int i = 0; i < rideSegments.length; i++) {
            if (TripMath.shouldCountSegment(rideSegments[i], 2500L,
                    previousAccuracy, 7f)) {
                countedDistance += rideSegments[i];
            }
        }

        assertTrue(countedDistance > 98f);
        assertTrue(countedDistance < 102f);
        assertEquals(14.4f, TripMath.averageSpeedKmh(countedDistance, 25000L), .5f);
    }
}
