package dev.honeyphone.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HoneypotDao {
    @Query("SELECT * FROM honeypot_events ORDER BY occurredAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<HoneypotEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: HoneypotEventEntity)

    @Query("DELETE FROM honeypot_events WHERE id NOT IN (SELECT id FROM honeypot_events ORDER BY occurredAt DESC LIMIT 100)")
    suspend fun retainLatestHundred()

    @Query("DELETE FROM honeypot_events")
    suspend fun deleteAll()

    @Query("SELECT * FROM honeypot_events WHERE syncStatus != 'synced' AND sessionId IS NOT NULL ORDER BY occurredAt LIMIT 100")
    suspend fun pendingUpload(): List<HoneypotEventEntity>

    @Query("UPDATE honeypot_events SET syncStatus = 'synced' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
