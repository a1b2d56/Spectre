package com.spectre.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spectre.app.core.data.repository.VaultRepository
import com.spectre.app.core.security.LockState
import com.spectre.app.core.security.VaultSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val vaultRepository: VaultRepository,
    private val session: VaultSession,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Only sync if vault is unlocked — never attempt a sync when locked
        if (session.lockState.value != LockState.UNLOCKED) return Result.success()

        val accountId = session.activeAccountId ?: return Result.success()
        val result    = vaultRepository.sync(accountId)

        return if (result.isSuccess) Result.success()
        else Result.retry()
    }

    companion object {
        const val WORK_NAME = "spectre_background_sync"

        fun schedule(context: Context, intervalMinutes: Long = 30) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES, // flex interval
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
