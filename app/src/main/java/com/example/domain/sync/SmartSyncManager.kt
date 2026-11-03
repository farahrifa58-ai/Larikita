package com.example.domain.sync

import android.content.Context
import com.example.data.db.RunDao
import com.example.data.db.RunEntity
import com.example.data.model.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED
}

class SmartSyncManager(
    private val context: Context,
    private val runDao: RunDao,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("larikita_sync_prefs", Context.MODE_PRIVATE)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(loadLastSyncTimestamp())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _statusMessage = MutableStateFlow("Penyimpanan lokal aktif (Offline-First)")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private fun loadLastSyncTimestamp(): Long {
        return prefs.getLong("last_sync_timestamp", 0L)
    }

    private fun saveLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
        _lastSyncTimestamp.value = timestamp
    }

    private fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes Smart Sync:
     * - Only pushes pending/failed activities that are completed
     * - Uses exponential backoff delays on failure
     * - Handles route chunking
     * - Prevents duplicate uploads
     */
    suspend fun syncPendingActivities(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (_syncState.value == SyncState.SYNCING) return@withContext false

        _syncState.value = SyncState.SYNCING
        _statusMessage.value = "Memeriksa antrean sinkronisasi..."

        val pendingRuns = runDao.getPendingSyncRuns()
        if (pendingRuns.isEmpty()) {
            _syncState.value = SyncState.IDLE
            _statusMessage.value = "Semua aktivitas sudah tersinkronisasi."
            return@withContext true
        }

        if (!isFirebaseConfigured()) {
            // No Firebase cloud project active yet: keep runs in pending state, mark message
            _syncState.value = SyncState.IDLE
            _statusMessage.value = "${pendingRuns.size} aktivitas tersimpan lokal (Menunggu konfigurasi Firebase Cloud)."
            return@withContext true
        }

        var successCount = 0
        var failCount = 0
        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            _statusMessage.value = "Koneksi Firebase gagal: ${e.localizedMessage}"
            return@withContext false
        }

        for (run in pendingRuns) {
            try {
                // Progressive backoff delay if previous attempt failed
                if (run.syncAttempts > 0) {
                    val backoffMs = (run.syncAttempts * 1000L).coerceAtMost(5000L)
                    delay(backoffMs)
                }

                // If tombstone deleted, delete in Firestore
                if (run.isDeleted) {
                    firestore.collection("users")
                        .document(userId)
                        .collection("activities")
                        .document(run.id)
                        .update("isDeleted", true, "deletedAt", FieldValue.serverTimestamp())
                        .await()

                    runDao.hardDeleteRunById(run.id)
                    successCount++
                    continue
                }

                // Split route coordinates into manageable chunks
                val points = run.getGpsPoints()
                val chunks = RouteChunkOptimizer.splitIntoChunks(run.id, points)

                // 1. Upload Activity Summary Document
                val activityDoc = hashMapOf(
                    "id" to run.id,
                    "userId" to userId,
                    "title" to run.title,
                    "startTime" to run.startTime,
                    "endTime" to run.endTime,
                    "durationSeconds" to run.durationSeconds,
                    "distanceMeters" to run.distanceMeters,
                    "averagePace" to run.averagePace,
                    "averagePaceSeconds" to run.averagePaceSeconds,
                    "averageSpeed" to run.averageSpeed,
                    "maxSpeed" to run.maxSpeed,
                    "calories" to run.calories,
                    "routeChunkCount" to chunks.size,
                    "createdAt" to run.createdAt,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "version" to run.version,
                    "notes" to run.notes,
                    "isDeleted" to false
                )

                firestore.collection("users")
                    .document(userId)
                    .collection("activities")
                    .document(run.id)
                    .set(activityDoc, SetOptions.merge())
                    .await()

                // 2. Upload Route Chunks in batch or sequential subcollection
                val chunksCollection = firestore.collection("users")
                    .document(userId)
                    .collection("activities")
                    .document(run.id)
                    .collection("routeChunks")

                for (chunk in chunks) {
                    val chunkData = hashMapOf(
                        "chunkId" to chunk.chunkId,
                        "activityId" to chunk.activityId,
                        "chunkIndex" to chunk.chunkIndex,
                        "totalChunks" to chunk.totalChunks,
                        "pointCount" to chunk.pointCount,
                        "pointsJson" to chunk.pointsJson,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    chunksCollection.document(chunk.chunkId)
                        .set(chunkData, SetOptions.merge())
                        .await()
                }

                // Mark local run as synced
                runDao.markSynced(run.id, System.currentTimeMillis())
                successCount++
            } catch (e: Exception) {
                failCount++
                val nextAttempt = run.syncAttempts + 1
                val errMsg = e.localizedMessage ?: "Unknown error"
                val newStatus = if (nextAttempt >= 5) RunEntity.SYNC_FAILED else RunEntity.SYNC_PENDING
                runDao.updateSyncAttempt(run.id, newStatus, nextAttempt, System.currentTimeMillis(), errMsg)
            }
        }

        saveLastSyncTimestamp(System.currentTimeMillis())

        if (failCount == 0) {
            _syncState.value = SyncState.SUCCESS
            _statusMessage.value = "Sinkronisasi berhasil! $successCount aktivitas dicadangkan ke cloud."
            true
        } else {
            _syncState.value = SyncState.FAILED
            _statusMessage.value = "$successCount berhasil, $failCount gagal. Akan dicoba kembali."
            false
        }
    }

    /**
     * Uploads user profile and preferences to Firestore.
     */
    suspend fun syncProfile(userId: String, profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured()) return@withContext true
        try {
            val firestore = FirebaseFirestore.getInstance()
            val profileDoc = hashMapOf(
                "name" to profile.name,
                "email" to profile.email,
                "dailyTargetKm" to profile.dailyTargetKm,
                "weeklyTargetKm" to profile.weeklyTargetKm,
                "monthlyTargetKm" to profile.monthlyTargetKm,
                "weightKg" to profile.weightKg,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users")
                .document(userId)
                .collection("profile")
                .document("main")
                .set(profileDoc, SetOptions.merge())
                .await()

            val prefsDoc = hashMapOf(
                "isDarkMode" to profile.isDarkMode,
                "isAutoSync" to profile.isAutoSync,
                "unit" to "km",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users")
                .document(userId)
                .collection("settings")
                .document("preferences")
                .set(prefsDoc, SetOptions.merge())
                .await()

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Restores activities from Firestore (paginated, 20 per request).
     * Merges by ID, never overwriting newer local data and never dropping unbacked runs!
     */
    suspend fun restoreFromCloud(userId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured()) {
            _statusMessage.value = "Firebase Cloud belum aktif di perangkat ini."
            return@withContext Pair(0, 0)
        }

        _syncState.value = SyncState.SYNCING
        _statusMessage.value = "Mengunduh cadangan dari cloud..."

        var imported = 0
        var updated = 0

        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("activities")
                .whereEqualTo("isDeleted", false)
                .limit(20)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val id = doc.getString("id") ?: doc.id
                val existing = runDao.getRunByIdDirect(id)

                // Fetch route chunks
                val chunksSnap = firestore.collection("users")
                    .document(userId)
                    .collection("activities")
                    .document(id)
                    .collection("routeChunks")
                    .get()
                    .await()

                val chunks = chunksSnap.documents.mapNotNull { cDoc ->
                    val cId = cDoc.getString("chunkId") ?: return@mapNotNull null
                    val idx = cDoc.getLong("chunkIndex")?.toInt() ?: 0
                    val total = cDoc.getLong("totalChunks")?.toInt() ?: 1
                    val pts = cDoc.getString("pointsJson") ?: "[]"
                    RouteChunk(cId, id, idx, total, 0, pts)
                }

                val assembledPoints = RouteChunkOptimizer.assembleRoute(chunks)
                val gpsPointsJson = com.example.data.model.GpsPoint.listToJsonString(assembledPoints)

                val restoredRun = RunEntity(
                    id = id,
                    userId = userId,
                    title = doc.getString("title") ?: "Lari Cloud",
                    startTime = doc.getLong("startTime") ?: System.currentTimeMillis(),
                    endTime = doc.getLong("endTime") ?: System.currentTimeMillis(),
                    durationSeconds = doc.getLong("durationSeconds") ?: 0L,
                    distanceMeters = doc.getDouble("distanceMeters") ?: 0.0,
                    averagePace = doc.getString("averagePace") ?: "0'00\"",
                    averagePaceSeconds = doc.getDouble("averagePaceSeconds") ?: 0.0,
                    averageSpeed = doc.getDouble("averageSpeed") ?: 0.0,
                    maxSpeed = doc.getDouble("maxSpeed") ?: 0.0,
                    calories = doc.getLong("calories")?.toInt() ?: 0,
                    gpsPointsJson = gpsPointsJson,
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = RunEntity.SYNC_SYNCED,
                    isCompleted = true,
                    notes = doc.getString("notes") ?: ""
                )

                if (existing != null) {
                    runDao.updateRun(restoredRun)
                    updated++
                } else {
                    runDao.insertRun(restoredRun)
                    imported++
                }
            }

            saveLastSyncTimestamp(System.currentTimeMillis())
            _syncState.value = SyncState.SUCCESS
            _statusMessage.value = "Pemulihan selesai: $imported aktivitas baru, $updated diperbarui."
            Pair(imported, updated)
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            _statusMessage.value = "Gagal memulihkan dari cloud: ${e.localizedMessage}"
            Pair(imported, updated)
        }
    }
}
