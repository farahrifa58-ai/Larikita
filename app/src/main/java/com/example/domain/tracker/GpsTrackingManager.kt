package com.example.domain.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.example.data.db.RunEntity
import com.example.data.model.GpsPoint
import com.example.data.repository.RunRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class TrackingUiState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentPace: String = "--'--\"",
    val averagePace: String = "--'--\"",
    val currentSpeedKmH: Double = 0.0,
    val averageSpeedKmH: Double = 0.0,
    val maxSpeedKmH: Double = 0.0,
    val calories: Int = 0,
    val gpsAccuracyMeters: Float = 0f,
    val gpsStatusMessage: String = "GPS Siap",
    val isGpsWeak: Boolean = false,
    val currentPoint: GpsPoint? = null,
    val points: List<GpsPoint> = emptyList(),
    val isSimulationMode: Boolean = false,
    val activeRunId: String = "",
    val isTrialLimitReached: Boolean = false,
    val isTrialExpired: Boolean = false,
    val trialNoticeMessage: String? = null
)

class GpsTrackingManager(
    private val context: Context,
    private val runRepository: RunRepository,
    private val scope: CoroutineScope
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private var simulationJob: Job? = null
    private var autosaveJob: Job? = null

    private var sessionStartTime: Long = 0L
    private var accumulatedActiveTimeMs: Long = 0L
    private var lastResumeTimestamp: Long = 0L

    private var lastRecordedPoint: GpsPoint? = null
    private val recordedPoints = mutableListOf<GpsPoint>()
    private var totalDistanceMeters: Double = 0.0
    private var maxSpeedKmH: Double = 0.0
    private var activeRunId: String = ""
    private var consecutiveRejects: Int = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    private val nativeLocationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        // Setup wake lock
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LariKita:GpsTrackingWakeLock"
            )
        } catch (_: Exception) {}
    }

    fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    @SuppressLint("MissingPermission")
    fun restoreSession(run: RunEntity) {
        activeRunId = run.id
        recordedPoints.clear()
        recordedPoints.addAll(run.getGpsPoints())
        totalDistanceMeters = run.distanceMeters
        maxSpeedKmH = run.maxSpeed
        lastRecordedPoint = recordedPoints.lastOrNull()
        sessionStartTime = run.startTime
        accumulatedActiveTimeMs = run.durationSeconds * 1000L
        lastResumeTimestamp = SystemClock.elapsedRealtime()

        acquireWakeLock()
        _uiState.value = TrackingUiState(
            isTracking = true,
            isPaused = true,
            durationSeconds = run.durationSeconds,
            distanceMeters = run.distanceMeters,
            currentPace = run.averagePace,
            averagePace = run.averagePace,
            currentSpeedKmH = run.averageSpeed,
            averageSpeedKmH = run.averageSpeed,
            maxSpeedKmH = run.maxSpeed,
            calories = run.calories,
            gpsStatusMessage = "Sesi Dipulihkan (Dijeda)",
            isGpsWeak = false,
            currentPoint = recordedPoints.lastOrNull(),
            points = recordedPoints.toList(),
            isSimulationMode = false,
            activeRunId = run.id
        )

        startTimer()
        startAutosave()
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    fun startRun(isSimulation: Boolean = false) {
        if (_uiState.value.isTracking) {
            if (!isSimulation) {
                startLocationUpdates()
            }
            return
        }

        if (com.example.domain.config.EditionConfig.isTrial(context) &&
            com.example.domain.config.EditionConfig.isTrialExpired(context)
        ) {
            _uiState.value = _uiState.value.copy(
                isTracking = false,
                isTrialExpired = true,
                gpsStatusMessage = "Masa Percobaan Habis",
                trialNoticeMessage = "Masa percobaan 24 jam telah selesai. Silakan perbarui ke Full Version untuk terus berlari tanpa batas waktu dan jarak."
            )
            return
        }

        activeRunId = UUID.randomUUID().toString()
        recordedPoints.clear()
        totalDistanceMeters = 0.0
        maxSpeedKmH = 0.0
        lastRecordedPoint = null

        // Check if pre-warmed GPS point is fresh and accurate
        val warmedPoint = _uiState.value.currentPoint
        val initialPoint = if (warmedPoint != null && warmedPoint.accuracy <= 25f && (System.currentTimeMillis() - warmedPoint.timestamp) < 20000) {
            recordedPoints.add(warmedPoint)
            lastRecordedPoint = warmedPoint
            warmedPoint
        } else {
            null
        }

        sessionStartTime = System.currentTimeMillis()
        accumulatedActiveTimeMs = 0L
        lastResumeTimestamp = SystemClock.elapsedRealtime()

        acquireWakeLock()

        _uiState.value = TrackingUiState(
            isTracking = true,
            isPaused = false,
            durationSeconds = 0L,
            distanceMeters = 0.0,
            currentPace = "--'--\"",
            averagePace = "--'--\"",
            currentSpeedKmH = 0.0,
            averageSpeedKmH = 0.0,
            maxSpeedKmH = 0.0,
            calories = 0,
            gpsStatusMessage = if (isSimulation) "Mode Simulasi Aktif" else if (initialPoint != null) "GPS Terkunci (±${initialPoint.accuracy.toInt()}m)" else "Mencari Sinyal GPS...",
            isGpsWeak = false,
            currentPoint = initialPoint,
            points = if (initialPoint != null) listOf(initialPoint) else emptyList(),
            isSimulationMode = isSimulation,
            activeRunId = activeRunId,
            isTrialLimitReached = false,
            isTrialExpired = false,
            trialNoticeMessage = null
        )

        startTimer()
        startAutosave()

        if (isSimulation) {
            startSimulation()
        } else {
            startLocationUpdates()
        }
    }

    fun dismissTrialNotice() {
        _uiState.value = _uiState.value.copy(
            trialNoticeMessage = null
        )
    }

    fun pauseRun() {
        if (!_uiState.value.isTracking || _uiState.value.isPaused) return

        // Accumulate active duration accurately
        val nowElapsed = SystemClock.elapsedRealtime()
        accumulatedActiveTimeMs += (nowElapsed - lastResumeTimestamp)

        _uiState.value = _uiState.value.copy(
            isPaused = true,
            gpsStatusMessage = "Aktivitas Dijeda"
        )
    }

    fun resumeRun() {
        if (!_uiState.value.isTracking || !_uiState.value.isPaused) return

        if (com.example.domain.config.EditionConfig.isTrial(context) &&
            totalDistanceMeters >= com.example.domain.config.EditionConfig.MAX_TRIAL_DISTANCE_METERS
        ) {
            _uiState.value = _uiState.value.copy(
                isTrialLimitReached = true,
                trialNoticeMessage = "Batas maksimal 1.0 km untuk Versi Trial telah tercapai. Lari Anda siap disimpan. Dapatkan Full Version untuk berlari tanpa batas jarak!"
            )
            return
        }

        lastResumeTimestamp = SystemClock.elapsedRealtime()

        _uiState.value = _uiState.value.copy(
            isPaused = false,
            gpsStatusMessage = if (_uiState.value.isSimulationMode) "Mode Simulasi Berjalan" else "GPS Merekam..."
        )
    }

    suspend fun finishRun(): RunEntity? {
        if (!_uiState.value.isTracking) return null

        if (!_uiState.value.isPaused) {
            val nowElapsed = SystemClock.elapsedRealtime()
            accumulatedActiveTimeMs += (nowElapsed - lastResumeTimestamp)
        }

        stopLocationUpdates()
        stopTimer()
        stopAutosave()
        releaseWakeLock()

        val finalDurationSec = accumulatedActiveTimeMs / 1000
        val finalDistMeters = totalDistanceMeters
        val avgPaceSec = HaversineCalculator.calculatePaceSecondsPerKm(finalDurationSec, finalDistMeters)
        val avgPaceStr = HaversineCalculator.formatPace(avgPaceSec)
        val avgSpeed = HaversineCalculator.calculateSpeedKmH(finalDurationSec, finalDistMeters)
        val calories = HaversineCalculator.estimateCalories(finalDistMeters)

        val run = RunEntity(
            id = activeRunId,
            userId = "local_runner",
            title = generateRunTitle(sessionStartTime),
            startTime = sessionStartTime,
            endTime = System.currentTimeMillis(),
            durationSeconds = finalDurationSec,
            distanceMeters = finalDistMeters,
            averagePace = avgPaceStr,
            averagePaceSeconds = avgPaceSec,
            averageSpeed = avgSpeed,
            maxSpeed = maxSpeedKmH,
            calories = calories,
            gpsPointsJson = GpsPoint.listToJsonString(recordedPoints),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = RunEntity.SYNC_STATUS_LOCAL,
            isCompleted = true,
            notes = if (_uiState.value.isSimulationMode) "Lari disimulasikan" else "Lari luar ruangan"
        )

        // Save completed run
        runRepository.saveRun(run)

        _uiState.value = TrackingUiState(
            isTracking = false,
            isPaused = false,
            gpsStatusMessage = "Lari Selesai"
        )

        return run
    }

    fun cancelRun() {
        stopLocationUpdates()
        stopTimer()
        stopAutosave()
        releaseWakeLock()

        scope.launch {
            runRepository.clearActiveRuns()
        }

        _uiState.value = TrackingUiState(
            isTracking = false,
            isPaused = false,
            gpsStatusMessage = "Aktivitas Dibatalkan"
        )
    }

    private fun handleNewLocation(location: Location) {
        if (!_uiState.value.isTracking || _uiState.value.isPaused) return

        val accuracy = location.accuracy
        val isWeak = accuracy > 25.0f
        val gpsStatus = when {
            accuracy <= 8.0f -> "GPS Sangat Akurat (±${accuracy.toInt()}m)"
            accuracy <= 20.0f -> "GPS Akurat (±${accuracy.toInt()}m)"
            accuracy <= 35.0f -> "GPS Cukup (±${accuracy.toInt()}m)"
            else -> "Sinyal GPS Lemah (±${accuracy.toInt()}m)"
        }

        val speed = if (location.hasSpeed()) location.speed else 0f
        val candidate = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis(),
            accuracy = accuracy,
            speed = speed,
            altitude = location.altitude
        )

        // For the very first anchor point, only accept if accuracy is reasonable (<= 35m)
        if (lastRecordedPoint == null) {
            if (accuracy <= 35.0f) {
                recordedPoints.add(candidate)
                lastRecordedPoint = candidate
                consecutiveRejects = 0
            }
            updateMetrics(gpsStatus, isWeak, candidate)
            return
        }

        val (isValid, deltaDistance) = HaversineCalculator.isValidRunMovement(
            prev = lastRecordedPoint,
            current = candidate,
            maxAccuracyMeters = 35f,
            minMovementMeters = 2.0
        )

        if (isValid) {
            consecutiveRejects = 0

            val isTrial = com.example.domain.config.EditionConfig.isTrial(context)
            if (isTrial && totalDistanceMeters + deltaDistance >= com.example.domain.config.EditionConfig.MAX_TRIAL_DISTANCE_METERS) {
                totalDistanceMeters = com.example.domain.config.EditionConfig.MAX_TRIAL_DISTANCE_METERS
                recordedPoints.add(candidate)
                lastRecordedPoint = candidate
                val nowElapsed = SystemClock.elapsedRealtime()
                accumulatedActiveTimeMs += (nowElapsed - lastResumeTimestamp)
                _uiState.value = _uiState.value.copy(
                    isPaused = true,
                    isTrialLimitReached = true,
                    distanceMeters = 1000.0,
                    gpsStatusMessage = "Batas 1 km Trial Tercapai",
                    trialNoticeMessage = "Batas maksimal 1.0 km untuk Versi Trial telah tercapai. Lari Anda siap disimpan. Dapatkan Full Version untuk lari tanpa batas!"
                )
                updateMetrics(gpsStatus, isWeak, candidate)
                return
            }

            totalDistanceMeters += deltaDistance
            recordedPoints.add(candidate)
            lastRecordedPoint = candidate

            // Instantaneous speed
            val instSpeedKmH = (candidate.speed * 3.6).toDouble()
            if (instSpeedKmH > maxSpeedKmH && instSpeedKmH < 45.0) {
                maxSpeedKmH = instSpeedKmH
            }
        } else {
            // Self-healing recovery: If candidate is accurate (<= 25m) but was rejected,
            // the initial anchor or previous point may have been an early jitter/teleport anomaly.
            if (accuracy in 0.1f..25.0f) {
                consecutiveRejects++
                if (consecutiveRejects >= 2 || recordedPoints.size <= 1) {
                    if (recordedPoints.size <= 1) {
                        recordedPoints.clear()
                    }
                    recordedPoints.add(candidate)
                    lastRecordedPoint = candidate
                    consecutiveRejects = 0
                }
            }
        }

        updateMetrics(gpsStatus, isWeak, candidate)
    }

    private fun updateMetrics(gpsStatus: String, isWeak: Boolean, latestPoint: GpsPoint?) {
        val activeSeconds = if (!_uiState.value.isPaused) {
            val nowElapsed = SystemClock.elapsedRealtime()
            (accumulatedActiveTimeMs + (nowElapsed - lastResumeTimestamp)) / 1000
        } else {
            accumulatedActiveTimeMs / 1000
        }

        val avgPaceSec = HaversineCalculator.calculatePaceSecondsPerKm(activeSeconds, totalDistanceMeters)
        val avgPaceStr = HaversineCalculator.formatPace(avgPaceSec)
        val avgSpeed = HaversineCalculator.calculateSpeedKmH(activeSeconds, totalDistanceMeters)
        val calories = HaversineCalculator.estimateCalories(totalDistanceMeters)

        val currentSpeed = if (latestPoint != null && latestPoint.speed > 0f) {
            (latestPoint.speed * 3.6).toDouble()
        } else {
            avgSpeed
        }

        val currentPace = if (currentSpeed > 1.0) {
            val currentPaceSec = (3600.0 / currentSpeed)
            HaversineCalculator.formatPace(currentPaceSec)
        } else {
            avgPaceStr
        }

        _uiState.value = _uiState.value.copy(
            durationSeconds = activeSeconds,
            distanceMeters = totalDistanceMeters,
            currentPace = currentPace,
            averagePace = avgPaceStr,
            currentSpeedKmH = currentSpeed,
            averageSpeedKmH = avgSpeed,
            maxSpeedKmH = maxSpeedKmH,
            calories = calories,
            gpsAccuracyMeters = latestPoint?.accuracy ?: 0f,
            gpsStatusMessage = gpsStatus,
            isGpsWeak = isWeak,
            currentPoint = latestPoint ?: _uiState.value.currentPoint,
            points = recordedPoints.toList()
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                if (_uiState.value.isTracking && !_uiState.value.isPaused) {
                    val latest = _uiState.value.currentPoint
                    updateMetrics(_uiState.value.gpsStatusMessage, _uiState.value.isGpsWeak, latest)
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startAutosave() {
        autosaveJob?.cancel()
        autosaveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000) // Autosave every 5 seconds for recovery
                if (_uiState.value.isTracking && recordedPoints.isNotEmpty()) {
                    val activeSeconds = _uiState.value.durationSeconds
                    val dist = totalDistanceMeters
                    val partialRun = RunEntity(
                        id = activeRunId,
                        title = "Sesi Lari Berlangsung",
                        startTime = sessionStartTime,
                        endTime = System.currentTimeMillis(),
                        durationSeconds = activeSeconds,
                        distanceMeters = dist,
                        averagePace = _uiState.value.averagePace,
                        averageSpeed = _uiState.value.averageSpeedKmH,
                        maxSpeed = maxSpeedKmH,
                        calories = _uiState.value.calories,
                        gpsPointsJson = GpsPoint.listToJsonString(recordedPoints),
                        isCompleted = false
                    )
                    runRepository.saveRun(partialRun)
                }
            }
        }
    }

    private fun stopAutosave() {
        autosaveJob?.cancel()
        autosaveJob = null
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            _uiState.value = _uiState.value.copy(
                gpsStatusMessage = "Menunggu Izin Akses GPS..."
            )
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMinUpdateDistanceMeters(1.0f)
                .setWaitForAccurateLocation(true)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Direct native GPS satellite provider (do NOT use cellular network provider to avoid teleports)
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    nativeLocationListener,
                    Looper.getMainLooper()
                )
            }

            // Immediately check last known fresh location for instant anchor
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null && lastLoc.accuracy <= 25.0f && (System.currentTimeMillis() - lastLoc.time) < 30000) {
                    if (lastRecordedPoint == null) {
                        handleNewLocation(lastLoc)
                    }
                }
            }

            // Request fresh high-accuracy GPS fix immediately
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null && loc.accuracy <= 25.0f) {
                        handleNewLocation(loc)
                    }
                }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                gpsStatusMessage = "Mencari Sinyal GPS..."
            )
        }
    }

    /**
     * Pre-warms GPS hardware so satellite lock is already acquired before user starts running.
     */
    @SuppressLint("MissingPermission")
    fun warmUpGps() {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine) return

        try {
            if (isGpsEnabled()) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null && !_uiState.value.isTracking) {
                            val pt = GpsPoint(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                timestamp = loc.time,
                                accuracy = loc.accuracy,
                                speed = if (loc.hasSpeed()) loc.speed else 0f,
                                altitude = loc.altitude
                            )
                            val gpsMsg = when {
                                loc.accuracy <= 10.0f -> "GPS Sangat Akurat (±${loc.accuracy.toInt()}m)"
                                loc.accuracy <= 25.0f -> "GPS Akurat (±${loc.accuracy.toInt()}m)"
                                else -> "GPS Siap (±${loc.accuracy.toInt()}m)"
                            }
                            _uiState.value = _uiState.value.copy(
                                currentPoint = pt,
                                gpsAccuracyMeters = loc.accuracy,
                                gpsStatusMessage = gpsMsg
                            )
                        }
                    }
            }
        } catch (_: Exception) {}
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.removeUpdates(nativeLocationListener)
        } catch (_: Exception) {}
        simulationJob?.cancel()
        simulationJob = null
    }

    /**
     * GPS Simulator for browser emulator testing
     */
    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = scope.launch(Dispatchers.Default) {
            // Start around Monas / Senayan Jakarta
            var lat = -6.2183
            var lon = 106.8026
            var angle = 0.0

            while (isActive) {
                delay(1200)
                if (_uiState.value.isTracking && !_uiState.value.isPaused) {
                    // Simulates running speed around ~10 km/h (approx 2.8 meters per 1.2s)
                    val stepDist = 0.00003
                    angle += 0.08
                    lat += stepDist * cos(angle)
                    lon += stepDist * sin(angle)

                    val simLocation = Location("Simulation").apply {
                        latitude = lat
                        longitude = lon
                        accuracy = 4.5f
                        speed = 2.85f // ~10.2 km/h
                        time = System.currentTimeMillis()
                    }

                    launch(Dispatchers.Main) {
                        handleNewLocation(simLocation)
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L) // Max 3 hours
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun generateRunTitle(startTime: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = startTime }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..10 -> "Lari Pagi"
            in 11..14 -> "Lari Siang"
            in 15..18 -> "Lari Sore"
            else -> "Lari Malam"
        }
    }
}
