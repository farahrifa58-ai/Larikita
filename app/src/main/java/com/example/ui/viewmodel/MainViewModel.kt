package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.RunEntity
import com.example.data.model.UserProfile
import com.example.data.repository.RunRepository
import com.example.data.repository.UserSettingsRepository
import com.example.domain.tracker.GpsTrackingManager
import com.example.domain.tracker.HaversineCalculator
import com.example.domain.tracker.TrackingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AppScreen {
    BERANDA,
    LARI,
    RINGKASAN,
    RIWAYAT,
    STATISTIK,
    PROFIL
}

enum class HistoryFilter {
    SEMUA,
    MINGGU_INI,
    BULAN_INI
}

data class WeeklyStats(
    val totalDistanceKm: Double = 0.0,
    val runCount: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val averagePace: String = "--'--\"",
    val targetKm: Double = 15.0,
    val progressPercent: Float = 0f,
    val dailyDistancesKm: List<Pair<String, Double>> = emptyList() // Day name to km
)

data class AllTimeStats(
    val totalDistanceKm: Double = 0.0,
    val totalRunsCount: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val averagePace: String = "--'--\"",
    val fastestPace: String = "--'--\"",
    val farthestDistanceKm: Double = 0.0,
    val totalCalories: Int = 0,
    val thisMonthDistanceKm: Double = 0.0,
    val thisWeekDistanceKm: Double = 0.0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val runRepository = RunRepository(database.runDao())
    val userSettingsRepository = UserSettingsRepository(application)
    val trackingManager = GpsTrackingManager(application, runRepository, viewModelScope)
    val smartSyncManager = com.example.domain.sync.SmartSyncManager(application, database.runDao(), viewModelScope)
    val networkMonitor = com.example.domain.sync.NetworkMonitor(application)

    // Online status & Smart Sync state flows
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val syncState: StateFlow<com.example.domain.sync.SyncState> = smartSyncManager.syncState
    val lastSyncTimestamp: StateFlow<Long> = smartSyncManager.lastSyncTimestamp
    val syncStatusMessage: StateFlow<String> = smartSyncManager.statusMessage
    val pendingSyncCount: StateFlow<Int> = database.runDao().getPendingSyncCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // Current Active Screen
    private val _currentScreen = MutableStateFlow(AppScreen.BERANDA)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Tracking state forwarded from GpsTrackingManager
    val trackingState: StateFlow<TrackingUiState> = trackingManager.uiState

    // User Profile settings
    val userProfile: StateFlow<UserProfile> = userSettingsRepository.userProfile

    // All Completed Runs
    val allCompletedRuns: StateFlow<List<RunEntity>> = runRepository.allCompletedRuns
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Last completed run
    val latestRun: StateFlow<RunEntity?> = runRepository.latestCompletedRun
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Unfinished run session for crash/restart recovery
    val unfinishedRun: StateFlow<RunEntity?> = runRepository.activeUnfinishedRun
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // In-progress or recently finished run for summary
    private val _summaryRun = MutableStateFlow<RunEntity?>(null)
    val summaryRun: StateFlow<RunEntity?> = _summaryRun.asStateFlow()

    // Selected run for detail viewing
    private val _selectedDetailRun = MutableStateFlow<RunEntity?>(null)
    val selectedDetailRun: StateFlow<RunEntity?> = _selectedDetailRun.asStateFlow()

    // History filter
    private val _historyFilter = MutableStateFlow(HistoryFilter.SEMUA)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    // Backup & Sync toast message
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        networkMonitor.setOnNetworkAvailable {
            triggerSmartSync()
        }
        viewModelScope.launch {
            if (networkMonitor.isOnline.value) {
                triggerSmartSync()
            }
        }
    }

    fun triggerSmartSync() {
        viewModelScope.launch {
            val uid = userProfile.value.email.ifBlank { "local_runner" }
            smartSyncManager.syncPendingActivities(uid)
            smartSyncManager.syncProfile(uid, userProfile.value)
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            val uid = userProfile.value.email.ifBlank { "local_runner" }
            val (imported, updated) = smartSyncManager.restoreFromCloud(uid)
            _syncMessage.value = "Pemulihan cloud: $imported aktivitas baru, $updated diperbarui."
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun selectDetailRun(run: RunEntity?) {
        _selectedDetailRun.value = run
    }

    fun startRun(isSimulation: Boolean = false) {
        trackingManager.startRun(isSimulation)
        _currentScreen.value = AppScreen.LARI
    }

    fun pauseRun() {
        trackingManager.pauseRun()
    }

    fun resumeRun() {
        trackingManager.resumeRun()
    }

    fun finishRun() {
        viewModelScope.launch {
            val completedRun = trackingManager.finishRun()
            if (completedRun != null) {
                _summaryRun.value = completedRun
                _currentScreen.value = AppScreen.RINGKASAN
                triggerSmartSync()
            } else {
                _currentScreen.value = AppScreen.BERANDA
            }
        }
    }

    fun cancelRun() {
        trackingManager.cancelRun()
        _currentScreen.value = AppScreen.BERANDA
    }

    fun updateSummaryRun(title: String, notes: String) {
        val current = _summaryRun.value ?: return
        val updated = current.copy(title = title.ifBlank { current.title }, notes = notes, syncStatus = RunEntity.SYNC_PENDING)
        _summaryRun.value = updated
        viewModelScope.launch {
            runRepository.updateRun(updated)
            triggerSmartSync()
        }
    }

    fun deleteRun(id: String) {
        viewModelScope.launch {
            database.runDao().softDeleteRunById(id)
            if (_selectedDetailRun.value?.id == id) {
                _selectedDetailRun.value = null
            }
            triggerSmartSync()
        }
    }

    fun populateSampleRuns() {
        viewModelScope.launch {
            runRepository.populateSampleRunsIfEmpty()
            _syncMessage.value = "Data contoh lari berhasil ditambahkan!"
        }
    }

    fun resumeUnfinishedRun(run: RunEntity) {
        trackingManager.restoreSession(run)
        _currentScreen.value = AppScreen.LARI
    }

    fun discardUnfinishedRun() {
        viewModelScope.launch {
            runRepository.clearActiveRuns()
            _syncMessage.value = "Sesi lari sebelumnya telah dibuang."
        }
    }

    fun saveUnfinishedRun(run: RunEntity) {
        viewModelScope.launch {
            val completed = run.copy(isCompleted = true, updatedAt = System.currentTimeMillis())
            runRepository.updateRun(completed)
            _summaryRun.value = completed
            _currentScreen.value = AppScreen.RINGKASAN
        }
    }

    suspend fun getExportJson(): String {
        return runRepository.exportRunsAsJson()
    }

    fun importRunsJson(jsonStr: String) {
        viewModelScope.launch {
            val (imported, updated) = runRepository.importRunsFromJson(jsonStr)
            _syncMessage.value = "Impor selesai: $imported lari baru, $updated diperbarui."
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            runRepository.deleteAllRuns()
            _selectedDetailRun.value = null
            _summaryRun.value = null
            _syncMessage.value = "Semua riwayat lari telah dibersihkan."
        }
    }

    fun performManualSync() {
        triggerSmartSync()
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun updateProfile(profile: UserProfile) {
        userSettingsRepository.updateProfile(profile)
    }

    fun toggleDarkMode(isDark: Boolean) {
        userSettingsRepository.setDarkMode(isDark)
    }

    fun updateTargets(daily: Double, weekly: Double, monthly: Double) {
        userSettingsRepository.setTargets(daily, weekly, monthly)
    }

    // Weekly calculations for Beranda
    fun calculateWeeklyStats(runs: List<RunEntity>, targetKm: Double): WeeklyStats {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startOfWeekMs = cal.timeInMillis

        val weeklyRuns = runs.filter { it.startTime >= startOfWeekMs }
        val totalDistanceKm = weeklyRuns.sumOf { it.distanceMeters } / 1000.0
        val totalDurationSec = weeklyRuns.sumOf { it.durationSeconds }
        val avgPaceSec = HaversineCalculator.calculatePaceSecondsPerKm(
            totalDurationSec,
            weeklyRuns.sumOf { it.distanceMeters }
        )

        val progress = if (targetKm > 0) {
            (totalDistanceKm / targetKm).toFloat().coerceIn(0f, 1f)
        } else 0f

        // 7 days breakdown (Sen, Sel, Rab, Kam, Jum, Sab, Min)
        val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        val dailyMap = mutableMapOf<Int, Double>()
        for (i in 0..6) dailyMap[i] = 0.0

        for (r in weeklyRuns) {
            val rCal = Calendar.getInstance().apply { timeInMillis = r.startTime }
            var dayIdx = rCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
            if (dayIdx < 0) dayIdx += 7
            dailyMap[dayIdx] = (dailyMap[dayIdx] ?: 0.0) + (r.distanceMeters / 1000.0)
        }

        val dailyList = (0..6).map { idx ->
            Pair(dayNames[idx], dailyMap[idx] ?: 0.0)
        }

        return WeeklyStats(
            totalDistanceKm = totalDistanceKm,
            runCount = weeklyRuns.size,
            totalDurationSeconds = totalDurationSec,
            averagePace = HaversineCalculator.formatPace(avgPaceSec),
            targetKm = targetKm,
            progressPercent = progress,
            dailyDistancesKm = dailyList
        )
    }

    // Comprehensive Stats for Statistik Screen
    fun calculateAllTimeStats(runs: List<RunEntity>): AllTimeStats {
        if (runs.isEmpty()) return AllTimeStats()

        val totalDistMeters = runs.sumOf { it.distanceMeters }
        val totalDistKm = totalDistMeters / 1000.0
        val totalDurationSec = runs.sumOf { it.durationSeconds }
        val avgPaceSec = HaversineCalculator.calculatePaceSecondsPerKm(totalDurationSec, totalDistMeters)

        // Best pace (lowest seconds per km, only for runs >= 1 km)
        val validPaces = runs.filter { it.distanceMeters >= 1000 && it.averagePaceSeconds > 120 }
        val fastestPaceSec = validPaces.minOfOrNull { it.averagePaceSeconds } ?: 0.0

        val farthestKm = runs.maxOfOrNull { it.distanceMeters / 1000.0 } ?: 0.0
        val totalCalories = runs.sumOf { it.calories }

        // This Month
        val monthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val startOfMonthMs = monthCal.timeInMillis
        val monthRuns = runs.filter { it.startTime >= startOfMonthMs }
        val monthDistKm = monthRuns.sumOf { it.distanceMeters } / 1000.0

        // This Week
        val weekCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val startOfWeekMs = weekCal.timeInMillis
        val weekRuns = runs.filter { it.startTime >= startOfWeekMs }
        val weekDistKm = weekRuns.sumOf { it.distanceMeters } / 1000.0

        return AllTimeStats(
            totalDistanceKm = totalDistKm,
            totalRunsCount = runs.size,
            totalDurationSeconds = totalDurationSec,
            averagePace = HaversineCalculator.formatPace(avgPaceSec),
            fastestPace = HaversineCalculator.formatPace(fastestPaceSec),
            farthestDistanceKm = farthestKm,
            totalCalories = totalCalories,
            thisMonthDistanceKm = monthDistKm,
            thisWeekDistanceKm = weekDistKm
        )
    }
}
