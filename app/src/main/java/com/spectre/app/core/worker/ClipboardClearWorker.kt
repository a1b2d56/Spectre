package com.spectre.app.core.worker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ClipboardClearWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    companion object {
        private const val WORK_ID = "ClipboardClearWorker"

        fun enqueue(context: Context, delaySeconds: Long = 30) {
            val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_ID,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_ID)
        }
    }

    override fun doWork(): Result {
        val clipboardManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                @Suppress("DEPRECATION")
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        return Result.success()
    }
}
