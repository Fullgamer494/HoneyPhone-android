package dev.honeyphone.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reviews", indices = [Index("installationId"), Index("createdAt")])
data class ReviewEntity(
    @PrimaryKey val checkId: String,
    val installationId: String,
    val source: String,
    val verdict: String,
    val hasAttention: Boolean,
    val runStatus: String,
    val startedAt: String,
    val finishedAt: String,
    val createdAt: Long,
    val payloadJson: String,
    val origin: String,
    val networkLabel: String? = null,
    val metricsJson: String? = null,
)

@Entity(
    tableName = "signals",
    primaryKeys = ["checkId", "signalId"],
    foreignKeys = [ForeignKey(entity = ReviewEntity::class, parentColumns = ["checkId"], childColumns = ["checkId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("checkId")],
)
data class SignalEntity(
    val checkId: String,
    val signalId: String,
    val kind: String,
    val outcome: String,
    val reasonCode: String,
    val evidenceJson: String,
    val elapsedMs: Int?,
)

@Entity(
    tableName = "outbox",
    foreignKeys = [ForeignKey(entity = ReviewEntity::class, parentColumns = ["checkId"], childColumns = ["checkId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("status"), Index("createdAt")],
)
data class OutboxEntity(
    @PrimaryKey val checkId: String,
    val status: String,
    val createdAt: Long,
    val lastError: String? = null,
)

data class ReviewWithSync(@androidx.room.Embedded val review: ReviewEntity, val syncStatus: String, val lastError: String?)
