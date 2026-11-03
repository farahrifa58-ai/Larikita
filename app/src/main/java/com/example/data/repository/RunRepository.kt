package com.example.data.repository

import com.example.data.db.RunDao
import com.example.data.db.RunEntity
import com.example.data.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RunRepository(private val runDao: RunDao) {
    val allCompletedRuns: Flow<List<RunEntity>> = runDao.getAllCompletedRuns()
    val latestCompletedRun: Flow<RunEntity?> = runDao.getLatestCompletedRun()
    val activeUnfinishedRun: Flow<RunEntity?> = runDao.getActiveUnfinishedRun()

    fun getRunById(id: String): Flow<RunEntity?> = runDao.getRunById(id)

    suspend fun getRunByIdDirect(id: String): RunEntity? = runDao.getRunByIdDirect(id)

    suspend fun saveRun(run: RunEntity) {
        runDao.insertRun(run)
    }

    suspend fun updateRun(run: RunEntity) {
        runDao.updateRun(run)
    }

    suspend fun deleteRun(id: String) {
        runDao.deleteRunById(id)
    }

    suspend fun clearActiveRuns() {
        runDao.clearActiveRuns()
    }

    suspend fun deleteAllRuns() {
        runDao.deleteAllRuns()
    }

    suspend fun getUnsyncedRuns(): List<RunEntity> {
        return runDao.getUnsyncedRuns()
    }

    suspend fun markAllSynced() {
        val unsynced = runDao.getUnsyncedRuns()
        val now = System.currentTimeMillis()
        for (r in unsynced) {
            runDao.markSynced(r.id, now)
        }
    }

    suspend fun exportRunsAsJson(): String {
        val runs = runDao.getAllCompletedRunsList()
        val array = JSONArray()
        for (r in runs) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("userId", r.userId)
                put("title", r.title)
                put("startTime", r.startTime)
                put("endTime", r.endTime)
                put("durationSeconds", r.durationSeconds)
                put("distanceMeters", r.distanceMeters)
                put("averagePace", r.averagePace)
                put("averagePaceSeconds", r.averagePaceSeconds)
                put("averageSpeed", r.averageSpeed)
                put("maxSpeed", r.maxSpeed)
                put("calories", r.calories)
                put("gpsPointsJson", r.gpsPointsJson)
                put("createdAt", r.createdAt)
                put("updatedAt", r.updatedAt)
                put("syncStatus", r.syncStatus)
                put("isCompleted", r.isCompleted)
                put("notes", r.notes)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    suspend fun importRunsFromJson(jsonStr: String): Pair<Int, Int> {
        if (jsonStr.isBlank()) return Pair(0, 0)
        var imported = 0
        var updated = 0
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val existing = runDao.getRunByIdDirect(id)

                val run = RunEntity(
                    id = id,
                    userId = obj.optString("userId", "local_runner"),
                    title = obj.optString("title", "Lari Terimpor"),
                    startTime = obj.optLong("startTime", System.currentTimeMillis()),
                    endTime = obj.optLong("endTime", System.currentTimeMillis()),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    distanceMeters = obj.optDouble("distanceMeters", 0.0),
                    averagePace = obj.optString("averagePace", "0'00\""),
                    averagePaceSeconds = obj.optDouble("averagePaceSeconds", 0.0),
                    averageSpeed = obj.optDouble("averageSpeed", 0.0),
                    maxSpeed = obj.optDouble("maxSpeed", 0.0),
                    calories = obj.optInt("calories", 0),
                    gpsPointsJson = obj.optString("gpsPointsJson", "[]"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = RunEntity.SYNC_STATUS_LOCAL,
                    isCompleted = obj.optBoolean("isCompleted", true),
                    notes = obj.optString("notes", "")
                )

                if (existing != null) {
                    runDao.updateRun(run)
                    updated++
                } else {
                    runDao.insertRun(run)
                    imported++
                }
            }
        } catch (_: Exception) {}
        return Pair(imported, updated)
    }

    suspend fun populateSampleRunsIfEmpty() {
        val now = System.currentTimeMillis()
        val oneDayMs = 24L * 60 * 60 * 1000

        // Sample 1: 2 days ago, 5.2 km
        val sample1Points = listOf(
            GpsPoint(-6.2088, 106.8456, now - (2 * oneDayMs), 4f, 2.8f),
            GpsPoint(-6.2095, 106.8470, now - (2 * oneDayMs) + 300000, 3f, 3.1f),
            GpsPoint(-6.2110, 106.8490, now - (2 * oneDayMs) + 600000, 4f, 3.2f),
            GpsPoint(-6.2130, 106.8520, now - (2 * oneDayMs) + 1200000, 4f, 3.0f),
            GpsPoint(-6.2088, 106.8456, now - (2 * oneDayMs) + 1820000, 4f, 2.9f)
        )
        val run1 = RunEntity(
            id = UUID.randomUUID().toString(),
            title = "Lari Pagi Monas",
            startTime = now - (2 * oneDayMs),
            endTime = now - (2 * oneDayMs) + (30 * 60 * 1000) + (20 * 1000),
            durationSeconds = 1820,
            distanceMeters = 5240.0,
            averagePace = "5'47\"",
            averagePaceSeconds = 347.0,
            averageSpeed = 10.36,
            maxSpeed = 13.2,
            calories = 345,
            gpsPointsJson = GpsPoint.listToJsonString(sample1Points),
            createdAt = now - (2 * oneDayMs),
            updatedAt = now - (2 * oneDayMs),
            syncStatus = RunEntity.SYNC_STATUS_LOCAL,
            isCompleted = true,
            notes = "Udara segar dan ritme lari stabil."
        )

        // Sample 2: 4 days ago, 3.6 km
        val sample2Points = listOf(
            GpsPoint(-6.2100, 106.8400, now - (4 * oneDayMs), 5f, 2.6f),
            GpsPoint(-6.2140, 106.8420, now - (4 * oneDayMs) + 600000, 4f, 2.8f),
            GpsPoint(-6.2180, 106.8440, now - (4 * oneDayMs) + 1320000, 4f, 2.7f)
        )
        val run2 = RunEntity(
            id = UUID.randomUUID().toString(),
            title = "Lari Sore Santai",
            startTime = now - (4 * oneDayMs),
            endTime = now - (4 * oneDayMs) + (22 * 60 * 1000),
            durationSeconds = 1320,
            distanceMeters = 3620.0,
            averagePace = "6'04\"",
            averagePaceSeconds = 364.0,
            averageSpeed = 9.87,
            maxSpeed = 11.5,
            calories = 238,
            gpsPointsJson = GpsPoint.listToJsonString(sample2Points),
            createdAt = now - (4 * oneDayMs),
            updatedAt = now - (4 * oneDayMs),
            syncStatus = RunEntity.SYNC_STATUS_SYNCED,
            isCompleted = true,
            notes = "Recovery run setelah istirahat."
        )

        // Sample 3: 6 days ago, 7.8 km
        val run3 = RunEntity(
            id = UUID.randomUUID().toString(),
            title = "Long Run Akhir Pekan",
            startTime = now - (6 * oneDayMs),
            endTime = now - (6 * oneDayMs) + (44 * 60 * 1000) + (10 * 1000),
            durationSeconds = 2650,
            distanceMeters = 7850.0,
            averagePace = "5'37\"",
            averagePaceSeconds = 337.0,
            averageSpeed = 10.66,
            maxSpeed = 14.1,
            calories = 520,
            gpsPointsJson = GpsPoint.listToJsonString(sample1Points),
            createdAt = now - (6 * oneDayMs),
            updatedAt = now - (6 * oneDayMs),
            syncStatus = RunEntity.SYNC_STATUS_LOCAL,
            isCompleted = true,
            notes = "Target 7 km tercapai dengan pace terbaik."
        )

        runDao.insertRun(run1)
        runDao.insertRun(run2)
        runDao.insertRun(run3)
    }
}
