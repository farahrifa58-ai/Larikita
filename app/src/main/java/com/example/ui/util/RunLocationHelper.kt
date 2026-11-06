package com.example.ui.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.model.GpsPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object RunLocationHelper {

    /**
     * Resolves human-readable location from the first or mid GPS points of a run.
     * Uses Android Geocoder with graceful offline and parsing fallbacks.
     */
    suspend fun resolveLocation(context: Context, points: List<GpsPoint>): RunLocationInfo {
        return withContext(Dispatchers.IO) {
            val validPoint = points.firstOrNull { it.latitude != 0.0 && it.longitude != 0.0 }
                ?: return@withContext RunLocationInfo(
                    fullAddress = "Rute Lari",
                    cityOnly = "Indonesia"
                )

            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale("id", "ID"))

                    val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            geocoder.getFromLocation(validPoint.latitude, validPoint.longitude, 1)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(validPoint.latitude, validPoint.longitude, 1)
                    }

                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        val subLoc = addr.subLocality ?: addr.thoroughfare ?: ""
                        val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                        val city = addr.subAdminArea ?: addr.locality ?: addr.adminArea ?: "Indonesia"

                        val full = when {
                            subLoc.isNotBlank() && locality.isNotBlank() -> "$subLoc, $locality"
                            locality.isNotBlank() -> locality
                            subLoc.isNotBlank() -> subLoc
                            else -> city
                        }

                        return@withContext RunLocationInfo(
                            fullAddress = full,
                            cityOnly = city
                        )
                    }
                }
            } catch (e: Exception) {
                // Silently fallback on network or service error
            }

            // Fallback: approximate coordinate string or default area
            val latStr = "%.3f".format(Locale.US, validPoint.latitude)
            val lonStr = "%.3f".format(Locale.US, validPoint.longitude)
            RunLocationInfo(
                fullAddress = "Area Lari ($latStr, $lonStr)",
                cityOnly = "Area Lari"
            )
        }
    }
}
