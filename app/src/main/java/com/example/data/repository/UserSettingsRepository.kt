package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class UserSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("larikita_user_prefs", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString("user_name", "Pelari Hebat") ?: "Pelari Hebat",
            email = prefs.getString("user_email", "") ?: "",
            avatarUrl = prefs.getString("user_avatar", "") ?: "",
            dailyTargetKm = prefs.getFloat("daily_target_km", 3.0f).toDouble(),
            weeklyTargetKm = prefs.getFloat("weekly_target_km", 15.0f).toDouble(),
            monthlyTargetKm = prefs.getFloat("monthly_target_km", 60.0f).toDouble(),
            weightKg = prefs.getFloat("weight_kg", 65.0f).toDouble(),
            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            isAutoSync = prefs.getBoolean("is_auto_sync", false),
            languageCode = prefs.getString("app_language_code", "id") ?: "id",
            isLoggedIn = false,
            distanceUnit = prefs.getString("distance_unit", "km") ?: "km"
        )
    }

    fun updateProfile(profile: UserProfile) {
        prefs.edit().apply {
            putString("user_name", profile.name)
            putString("user_avatar", profile.avatarUrl)
            putFloat("daily_target_km", profile.dailyTargetKm.toFloat())
            putFloat("weekly_target_km", profile.weeklyTargetKm.toFloat())
            putFloat("monthly_target_km", profile.monthlyTargetKm.toFloat())
            putFloat("weight_kg", profile.weightKg.toFloat())
            putBoolean("is_dark_mode", profile.isDarkMode)
            putBoolean("is_auto_sync", profile.isAutoSync)
            putString("app_language_code", profile.languageCode)
            putString("distance_unit", profile.distanceUnit)
            apply()
        }
        _userProfile.value = profile
    }

    fun updateUserName(name: String) {
        val current = _userProfile.value
        updateProfile(current.copy(name = name.trim()))
    }

    fun saveAvatarPhoto(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
            val avatarFile = File(context.filesDir, "user_avatar.jpg")
            val outputStream = FileOutputStream(avatarFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            val path = avatarFile.absolutePath
            val current = _userProfile.value
            updateProfile(current.copy(avatarUrl = path))
            path
        } catch (_: Exception) {
            ""
        }
    }

    fun getLastDriveBackupTime(): Long {
        return prefs.getLong("last_drive_backup_time", 0L)
    }

    fun setLastDriveBackupTime(time: Long) {
        prefs.edit().putLong("last_drive_backup_time", time).apply()
    }

    fun setLanguage(languageCode: String) {
        val current = _userProfile.value
        updateProfile(current.copy(languageCode = languageCode))
    }

    fun setDistanceUnit(unit: String) {
        val current = _userProfile.value
        updateProfile(current.copy(distanceUnit = unit))
    }

    fun setDarkMode(isDark: Boolean) {
        val current = _userProfile.value
        updateProfile(current.copy(isDarkMode = isDark))
    }

    fun setTargets(daily: Double, weekly: Double, monthly: Double) {
        val current = _userProfile.value
        updateProfile(current.copy(
            dailyTargetKm = daily,
            weeklyTargetKm = weekly,
            monthlyTargetKm = monthly
        ))
    }
}
