package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("larikita_user_prefs", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString("user_name", "Pelari Hebat") ?: "Pelari Hebat",
            email = prefs.getString("user_email", "pelari@larikita.app") ?: "pelari@larikita.app",
            avatarUrl = prefs.getString("user_avatar", "") ?: "",
            dailyTargetKm = prefs.getFloat("daily_target_km", 3.0f).toDouble(),
            weeklyTargetKm = prefs.getFloat("weekly_target_km", 15.0f).toDouble(),
            monthlyTargetKm = prefs.getFloat("monthly_target_km", 60.0f).toDouble(),
            weightKg = prefs.getFloat("weight_kg", 65.0f).toDouble(),
            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            isAutoSync = prefs.getBoolean("is_auto_sync", true)
        )
    }

    fun updateProfile(profile: UserProfile) {
        prefs.edit().apply {
            putString("user_name", profile.name)
            putString("user_email", profile.email)
            putString("user_avatar", profile.avatarUrl)
            putFloat("daily_target_km", profile.dailyTargetKm.toFloat())
            putFloat("weekly_target_km", profile.weeklyTargetKm.toFloat())
            putFloat("monthly_target_km", profile.monthlyTargetKm.toFloat())
            putFloat("weight_kg", profile.weightKg.toFloat())
            putBoolean("is_dark_mode", profile.isDarkMode)
            putBoolean("is_auto_sync", profile.isAutoSync)
            apply()
        }
        _userProfile.value = profile
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
