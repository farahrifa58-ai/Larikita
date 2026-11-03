package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.GpsPoint
import java.util.UUID

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "local_runner",
    val title: String = "Lari Pagi",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averagePace: String = "0'00\"",
    val averagePaceSeconds: Double = 0.0, // seconds per km
    val averageSpeed: Double = 0.0, // km/h
    val maxSpeed: Double = 0.0, // km/h
    val calories: Int = 0,
    val gpsPointsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SYNC_PENDING, // pending, syncing, synced, failed
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long = 0L,
    val syncErrorMessage: String = "",
    val version: Int = 1,
    val isCompleted: Boolean = true,
    val isDeleted: Boolean = false, // Tombstone for sync
    val deletedAt: Long? = null,
    val routeChunkCount: Int = 1,
    val notes: String = ""
) {
    fun getGpsPoints(): List<GpsPoint> {
        return GpsPoint.listFromJsonString(gpsPointsJson)
    }

    val distanceKm: Double
        get() = distanceMeters / 1000.0

    companion object {
        const val SYNC_PENDING = "pending"
        const val SYNC_SYNCING = "syncing"
        const val SYNC_SYNCED = "synced"
        const val SYNC_FAILED = "failed"

        const val SYNC_STATUS_LOCAL = SYNC_PENDING
        const val SYNC_STATUS_PENDING = SYNC_PENDING
        const val SYNC_STATUS_SYNCED = SYNC_SYNCED
    }
}
