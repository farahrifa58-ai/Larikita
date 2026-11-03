package com.example.data.model

data class UserProfile(
    val name: String = "Pelari Hebat",
    val email: String = "pelari@larikita.app",
    val avatarUrl: String = "",
    val dailyTargetKm: Double = 3.0,
    val weeklyTargetKm: Double = 15.0,
    val monthlyTargetKm: Double = 60.0,
    val weightKg: Double = 65.0,
    val isDarkMode: Boolean = false,
    val isAutoSync: Boolean = true,
    val languageCode: String = "id",
    val isLoggedIn: Boolean = false,
    val distanceUnit: String = "km"
)
