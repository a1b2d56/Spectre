package com.spectre.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spectre.app.core.backup.BackupRunner
import com.spectre.app.core.data.datastore.SpectrePreferences
import com.spectre.app.core.security.LockState
import com.spectre.app.core.security.VaultSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRunner: BackupRunner,
    private val prefs: SpectrePreferences,
    private val session: VaultSession
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()
        if (!settings.backupEnabled) return Result.success()

        // Backup requires active vault key to decrypt ciphers
        if (session.lockState.value != LockState.UNLOCKED) {
            return Result.retry() // Retry later when unlocked
        }

        val accountId = session.activeAccountId ?: return Result.success()

        val backupPassword = if (settings.backupPassword.isNotEmpty()) {
            settings.backupPassword
        } else {
            // Fallback to Hex derived key from session encKey
            session.getUserKey().encKey.joinToString("") { "%02x".format(it) }
        }

        val result = backupRunner.runBackup(
            accountId = accountId,
            backupPassword = backupPassword,
            destFolderUri = settings.backupLocalPath.takeIf { it.isNotEmpty() },
            webDavUrl = settings.backupWebDavUrl.takeIf { it.isNotEmpty() },
            webDavUser = settings.backupWebDavUsername.takeIf { it.isNotEmpty() },
            webDavPassword = settings.backupWebDavPassword.takeIf { it.isNotEmpty() }
        )

        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "spectre_background_backup"

        fun schedule(context: Context, intervalHours: Int = 24) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                intervalHours.toLong(), TimeUnit.HOURS,
                1, TimeUnit.HOURS // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
