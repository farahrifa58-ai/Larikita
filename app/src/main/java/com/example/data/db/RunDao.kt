package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM runs WHERE isCompleted = 1 AND isDeleted = 0 ORDER BY startTime DESC")
    fun getAllCompletedRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE isCompleted = 1 AND isDeleted = 0 ORDER BY startTime DESC")
    suspend fun getAllCompletedRunsList(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE id = :id LIMIT 1")
    fun getRunById(id: String): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id LIMIT 1")
    suspend fun getRunByIdDirect(id: String): RunEntity?

    @Query("SELECT * FROM runs WHERE isCompleted = 1 AND isDeleted = 0 ORDER BY startTime DESC LIMIT 1")
    fun getLatestCompletedRun(): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE isCompleted = 0 AND isDeleted = 0 ORDER BY startTime DESC LIMIT 1")
    fun getActiveUnfinishedRun(): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE isCompleted = 1 AND isDeleted = 0 AND startTime >= :sinceTimestamp ORDER BY startTime ASC")
    fun getRunsSince(sinceTimestamp: Long): Flow<List<RunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity)

    @Update
    suspend fun updateRun(run: RunEntity)

    @Query("UPDATE runs SET isDeleted = 1, deletedAt = :now, syncStatus = 'pending', updatedAt = :now WHERE id = :id")
    suspend fun deleteRunById(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE runs SET isDeleted = 1, deletedAt = :now, syncStatus = 'pending', updatedAt = :now WHERE id = :id")
    suspend fun softDeleteRunById(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun hardDeleteRunById(id: String)

    @Query("DELETE FROM runs WHERE isCompleted = 0")
    suspend fun clearActiveRuns()

    @Query("DELETE FROM runs")
    suspend fun deleteAllRuns()

    @Query("SELECT * FROM runs WHERE (syncStatus = 'pending' OR syncStatus = 'failed') AND syncAttempts < 5")
    suspend fun getPendingSyncRuns(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE (syncStatus = 'pending' OR syncStatus = 'failed') AND syncAttempts < 5")
    suspend fun getUnsyncedRuns(): List<RunEntity>

    @Query("SELECT COUNT(*) FROM runs WHERE isCompleted = 1 AND isDeleted = 0 AND syncStatus != 'synced'")
    fun getPendingSyncCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM runs WHERE isCompleted = 1 AND isDeleted = 0 AND syncStatus != 'synced'")
    suspend fun getPendingSyncCount(): Int

    @Query("UPDATE runs SET syncStatus = 'synced', syncErrorMessage = '', updatedAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE runs SET syncStatus = :status, syncAttempts = :attempts, lastSyncAttempt = :timestamp, syncErrorMessage = :error WHERE id = :id")
    suspend fun updateSyncAttempt(id: String, status: String, attempts: Int, timestamp: Long, error: String)
}
