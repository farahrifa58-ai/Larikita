package com.example.domain.tracker

import com.example.data.model.GpsPoint
import kotlin.math.*

object HaversineCalculator {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates distance in meters between two coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Filters candidate GPS point against previous point.
     * Returns true if point should be added to distance accumulation.
     */
    fun isValidRunMovement(
        prev: GpsPoint?,
        current: GpsPoint,
        maxAccuracyMeters: Float = 35.0f,
        minMovementMeters: Double = 2.5,
        maxSpeedMetersPerSecond: Double = 12.0 // ~43.2 km/h max sprint
    ): Pair<Boolean, Double> {
        // Discard points with poor accuracy
        if (current.accuracy > maxAccuracyMeters && current.accuracy > 0f) {
            return Pair(false, 0.0)
        }

        if (prev == null) {
            return Pair(true, 0.0)
        }

        val distance = calculateDistanceMeters(
            prev.latitude, prev.longitude,
            current.latitude, current.longitude
        )

        // Prevent stationary jitter
        if (distance < minMovementMeters) {
            return Pair(false, 0.0)
        }

        val timeDeltaSeconds = (current.timestamp - prev.timestamp) / 1000.0
        if (timeDeltaSeconds > 0) {
            val calculatedSpeed = distance / timeDeltaSeconds
            // Discard sudden teleports / unreasonable GPS jumps
            if (calculatedSpeed > maxSpeedMetersPerSecond) {
                return Pair(false, 0.0)
            }
        }

        return Pair(true, distance)
    }

    /**
     * Calculates pace in seconds per kilometer.
     */
    fun calculatePaceSecondsPerKm(durationSeconds: Long, distanceMeters: Double): Double {
        if (distanceMeters < 30.0 || durationSeconds <= 0) return 0.0
        val km = distanceMeters / 1000.0
        return durationSeconds / km
    }

    /**
     * Formats pace seconds into readable "M'SS\" /km".
     */
    fun formatPace(paceSecondsPerKm: Double): String {
        if (paceSecondsPerKm <= 0.0 || paceSecondsPerKm > 3600.0) {
            return "--'--\""
        }
        val minutes = (paceSecondsPerKm / 60).toInt()
        val seconds = (paceSecondsPerKm % 60).toInt()
        return "%d'%02d\"".format(minutes, seconds)
    }

    /**
     * Calculates speed in km/h.
     */
    fun calculateSpeedKmH(durationSeconds: Long, distanceMeters: Double): Double {
        if (durationSeconds <= 0 || distanceMeters <= 0.0) return 0.0
        val hours = durationSeconds / 3600.0
        val km = distanceMeters / 1000.0
        return km / hours
    }

    /**
     * Formats duration in seconds to "HH:MM:SS" or "MM:SS".
     */
    fun formatDuration(durationSeconds: Long): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Estimates calories burned.
     * Standard running formula: distance(km) * weight(kg) * 1.036
     */
    fun estimateCalories(distanceMeters: Double, weightKg: Double = 65.0): Int {
        val km = distanceMeters / 1000.0
        return (km * weightKg * 1.036).roundToInt().coerceAtLeast(0)
    }
}
