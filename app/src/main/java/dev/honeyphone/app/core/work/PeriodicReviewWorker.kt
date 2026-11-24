package dev.honeyphone.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.honeyphone.app.data.RoomReviewRepository
import dev.honeyphone.app.data.HoneypotEventRepository
import dev.honeyphone.app.core.network.AndroidSignalReader
import dev.honeyphone.app.core.network.ApiFailure
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class PeriodicReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = RoomReviewRepository(applicationContext)
        return try {
            if (!PeriodicReviewScheduler.isEnabled(applicationContext)) {
                PeriodicReviewScheduler.cancel(applicationContext)
                return Result.success()
            }
            if (!AndroidSignalReader.hasValidatedWifi(applicationContext)) return Result.success()
            val activeDevice = try {
                repository.deviceIsActive()
            } catch (e: ApiFailure) {
                if (e.status == 401) return Result.success()
                null
            } catch (_: Exception) {
                null
            }
            if (activeDevice == false) {
                return Result.success()
            }
            repository.recoverInterruptedSyncs()
            repository.captureAndSave(null,requireValidatedInternet=true)
            repository.syncPendingOutbox()
            HoneypotEventRepository(applicationContext).syncPending()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object PeriodicReviewScheduler {
    const val INTERVAL_MINUTES = 15L
    private const val UNIQUE_NAME = "honeyphone-periodic-network-review"
    private const val PREFERENCES = "honeyphone_local"
    private const val ENABLED_KEY = "automatic_wifi_scans_enabled"

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(ENABLED_KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED_KEY, enabled).apply()
        if (enabled) ensureScheduled(context) else cancel(context)
    }

    fun ensureScheduled(context: Context) {
        if (!isEnabled(context)) {
            cancel(context)
            return
        }
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<PeriodicReviewWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
}
