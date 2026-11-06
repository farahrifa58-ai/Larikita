package com.example.domain.config

import android.content.Context
import android.content.SharedPreferences

enum class AppEditionType {
    TRIAL,
    FULL
}

/**
 * Manages App Edition configuration (Trial vs Full Version).
 *
 * Rules:
 * - Trial: 24-hour expiration window from first app launch, max 1.0 km distance per run,
 *   share feature in history is disabled with an upgrade prompt.
 * - Full: Unlimited duration, unlimited distance, full share capability enabled.
 *   "Full Version" badge is only displayed in "Tentang Aplikasi" (never in top bar).
 */
object EditionConfig {
    // Flag used during APK generation (can be overridden per APK build)
    var DEFAULT_BUILD_IS_TRIAL: Boolean = false

    private const val PREFS_NAME = "larikita_edition_prefs"
    private const val KEY_FIRST_LAUNCH_TIME = "larikita_first_launch_time"
    private const val KEY_OVERRIDE_EDITION = "larikita_override_edition"

    const val TRIAL_DURATION_MS = 24L * 60L * 60L * 1000L // 24 Hours in milliseconds
    const val MAX_TRIAL_DISTANCE_METERS = 1000.0 // 1.0 km distance limit

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTrial(context: Context): Boolean {
        val prefs = getPrefs(context)
        val override = prefs.getString(KEY_OVERRIDE_EDITION, null)
        if (override != null) {
            return override == AppEditionType.TRIAL.name
        }
        return DEFAULT_BUILD_IS_TRIAL
    }

    fun setEditionOverride(context: Context, isTrial: Boolean?) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            if (isTrial == null) {
                remove(KEY_OVERRIDE_EDITION)
            } else {
                putString(
                    KEY_OVERRIDE_EDITION,
                    if (isTrial) AppEditionType.TRIAL.name else AppEditionType.FULL.name
                )
            }
            apply()
        }
    }

    fun getFirstLaunchTime(context: Context): Long {
        val prefs = getPrefs(context)
        var firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        if (firstLaunch == 0L) {
            firstLaunch = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, firstLaunch).apply()
        }
        return firstLaunch
    }

    fun isTrialExpired(context: Context): Boolean {
        if (!isTrial(context)) return false
        val firstLaunch = getFirstLaunchTime(context)
        val elapsed = System.currentTimeMillis() - firstLaunch
        return elapsed >= TRIAL_DURATION_MS
    }

    fun getRemainingTrialTimeMs(context: Context): Long {
        if (!isTrial(context)) return Long.MAX_VALUE
        val firstLaunch = getFirstLaunchTime(context)
        val elapsed = System.currentTimeMillis() - firstLaunch
        val remaining = TRIAL_DURATION_MS - elapsed
        return if (remaining > 0) remaining else 0L
    }

    fun formatRemainingTrialTime(context: Context): String {
        if (!isTrial(context)) return "Akses Penuh Selamanya"
        val remainingMs = getRemainingTrialTimeMs(context)
        if (remainingMs <= 0L) return "Masa percobaan 24 jam telah berakhir"
        val totalMinutes = remainingMs / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "$hours jam $minutes menit tersisa"
            minutes > 0 -> "$minutes menit tersisa"
            else -> "Kurang dari 1 menit tersisa"
        }
    }

    fun checkCanStartRun(context: Context): Pair<Boolean, String?> {
        if (!isTrial(context)) return Pair(true, null)
        if (isTrialExpired(context)) {
            return Pair(
                false,
                "Masa uji coba 24 jam telah selesai. Silakan perbarui ke Full Version untuk menikmati fitur lari tanpa batas."
            )
        }
        return Pair(true, null)
    }

    fun getEditionName(context: Context): String {
        return if (isTrial(context)) "Trial Version (Uji Coba 24 Jam)" else "Full Version"
    }

    fun getEditionBadgeLabel(context: Context): String {
        return if (isTrial(context)) "Versi Trial" else "Full Version"
    }
}
