package dev.honeyphone.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.honeyphone.app.data.RoomReviewRepository
import dev.honeyphone.app.data.HoneypotEventRepository
import java.util.concurrent.TimeUnit

class PeriodicReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = RoomReviewRepository(applicationContext)
        return try {
            if (runCatching { repository.deviceIsActive() }.getOrNull() == false) {
                PeriodicReviewScheduler.cancel(applicationContext)
                return Result.success()
            }
            repository.recoverInterruptedSyncs()
            repository.captureAndSave(null)
            repository.syncPendingOutbox()
            HoneypotEventRepository(applicationContext).syncPending()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object PeriodicReviewScheduler {
    const val INTERVAL_MINUTES = 15L
    private const val UNIQUE_NAME = "honeyphone-periodic-network-review"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<PeriodicReviewWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
}
