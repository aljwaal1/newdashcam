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
        assertEquals(2.5f, TripMath.movementThreshold(3f, 3f), .01f);
        assertEquals(8.4f, TripMath.movementThreshold(35f, 35f), .01f);
        assertTrue(TripMath.isPlausibleSegment(10f, 3000L));
        assertFalse(TripMath.isPlausibleSegment(100f, 1000L));
        assertFalse(TripMath.isPlausibleSegment(Float.NaN, 1000L));
    }

    @Test public void speedIsFiniteClampedAndSmoothed() {
        assertEquals(18f, TripMath.speedKmh(5f, true, 0f, 0L), .01f);
        assertEquals(36f, TripMath.speedKmh(0f, false, 10f, 1000L), .01f);
        assertEquals(TripMath.MAX_BICYCLE_SPEED_KMH,
                TripMath.speedKmh(Float.POSITIVE_INFINITY, true, 100f, 1000L), .01f);
        float smoothed = TripMath.smoothSpeed(10f, 20f);
        assertEquals(13.2f, smoothed, .01f);
        assertFalse(Float.isNaN(smoothed));
        assertFalse(Float.isInfinite(smoothed));
    }
}
