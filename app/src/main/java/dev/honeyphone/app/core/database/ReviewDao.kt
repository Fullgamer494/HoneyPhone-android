package dev.honeyphone.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT reviews.*, COALESCE(outbox.status, 'synced') AS syncStatus, outbox.lastError AS lastError FROM reviews LEFT JOIN outbox ON reviews.checkId = outbox.checkId ORDER BY reviews.createdAt DESC")
    fun observeAll(): Flow<List<ReviewWithSync>>

    @Query("SELECT * FROM reviews WHERE checkId = :id")
    suspend fun get(id: String): ReviewEntity?

    @Query("SELECT * FROM signals WHERE checkId = :id ORDER BY kind")
    suspend fun signals(id: String): List<SignalEntity>

    @Query("SELECT * FROM outbox WHERE checkId = :id")
    suspend fun outbox(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE status IN ('pending','failed') ORDER BY createdAt")
    suspend fun pending(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReview(review: ReviewEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSignals(signals: List<SignalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOutbox(item: OutboxEntity)

    @Upsert
    suspend fun putRemoteReview(review: ReviewEntity)

    @Upsert
    suspend fun putRemoteSignals(signals: List<SignalEntity>)

    @Query("UPDATE outbox SET status = 'pending', lastError = 'Sync interrupted; retry with the same payload.' WHERE status = 'syncing'")
    suspend fun recoverInterruptedSyncs()

    @Query("UPDATE outbox SET status = :status, lastError = :error WHERE checkId = :id")
    suspend fun setStatus(id: String, status: String, error: String?)

    @Query("DELETE FROM outbox WHERE checkId = :id")
    suspend fun clearOutbox(id: String)

    @Query("DELETE FROM reviews WHERE checkId = :id")
    suspend fun deleteReview(id: String): Int

    @Transaction
    suspend fun saveLocal(review: ReviewEntity, signals: List<SignalEntity>, outbox: OutboxEntity) {
        insertReview(review)
        insertSignals(signals)
        putOutbox(outbox)
    }

    @Transaction
    suspend fun saveRemote(review: ReviewEntity, signals: List<SignalEntity>) {
        putRemoteReview(review)
        putRemoteSignals(signals)
    }
}
