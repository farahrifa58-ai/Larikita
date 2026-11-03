package com.example

import com.example.data.model.GpsPoint
import com.example.domain.tracker.HaversineCalculator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testHaversineDistanceAccuracy() {
        // Distance between Monas (-6.1754, 106.8272) and Bundaran HI (-6.1950, 106.8230) ~2.2 km
        val distance = HaversineCalculator.calculateDistanceMeters(
            -6.1754, 106.8272,
            -6.1950, 106.8230
        )
        assertTrue("Distance should be between 2100m and 2300m", distance in 2100.0..2300.0)
    }

    @Test
    fun testPaceFormatting() {
        // 5 km in 1500 seconds (25 minutes) => 300 sec/km => 5'00"
        val paceSec = HaversineCalculator.calculatePaceSecondsPerKm(1500, 5000.0)
        assertEquals(300.0, paceSec, 0.01)
        val formatted = HaversineCalculator.formatPace(paceSec)
        assertEquals("5'00\"", formatted)
    }

    @Test
    fun testDurationFormatting() {
        assertEquals("00:45", HaversineCalculator.formatDuration(45))
        assertEquals("05:30", HaversineCalculator.formatDuration(330))
        assertEquals("01:15:20", HaversineCalculator.formatDuration(4520))
    }

    @Test
    fun testSpeedCalculation() {
        // 10 km in 3600 seconds = 10.0 km/h
        val speed = HaversineCalculator.calculateSpeedKmH(3600, 10000.0)
        assertEquals(10.0, speed, 0.01)
    }

    @Test
    fun testMovementValidationFiltering() {
        val p1 = GpsPoint(-6.1754, 106.8272, 1000000, 5f, 2.5f)
        // Point with poor accuracy (> 35m) should be rejected
        val poorAccuracyPoint = GpsPoint(-6.1754, 106.8272, 1002000, 45f, 2.5f)
        val (isValid1, _) = HaversineCalculator.isValidRunMovement(p1, poorAccuracyPoint)
        assertFalse("Poor accuracy GPS point must be rejected", isValid1)

        // Point with tiny distance (< 2m) should be rejected (standing jitter)
        val jitterPoint = GpsPoint(-6.1754005, 106.8272005, 1002000, 4f, 0.1f)
        val (isValid2, _) = HaversineCalculator.isValidRunMovement(p1, jitterPoint)
        assertFalse("Tiny stationary jitter must be rejected", isValid2)

        // Point with reasonable movement (~10m in 3s => ~3.3 m/s) should be accepted
        val validPoint = GpsPoint(-6.1755, 106.8272, 1003000, 4f, 3.3f)
        val (isValid3, dist) = HaversineCalculator.isValidRunMovement(p1, validPoint)
        assertTrue("Valid running movement should be accepted", isValid3)
        assertTrue(dist > 5.0)
    }
}
