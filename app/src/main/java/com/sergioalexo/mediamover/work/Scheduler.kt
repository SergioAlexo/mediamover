package com.sergioalexo.mediamover.work

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sergioalexo.mediamover.model.AppSettings
import java.time.Duration
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val PERIODIC = "periodic-move"
    private const val INSTANT = "instant-move"

    fun apply(context: Context, settings: AppSettings) {
        val wm = WorkManager.getInstance(context)

        if (settings.intervalMinutes > 0) {
            val request = PeriodicWorkRequestBuilder<MoveWorker>(
                settings.intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES,
            ).build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            wm.cancelUniqueWork(PERIODIC)
        }

        if (settings.instant) enqueueInstant(context, ExistingWorkPolicy.KEEP) else wm.cancelUniqueWork(INSTANT)
    }

    /**
     * One-shot job that fires shortly after anything new lands in MediaStore. Content-URI jobs only fire once,
     * so [MoveWorker] queues the next one behind itself when it finishes.
     */
    fun enqueueInstant(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Files.getContentUri("external"), true)
            // Wait for a burst of downloads to settle before running.
            .setTriggerContentUpdateDelay(Duration.ofSeconds(15))
            .setTriggerContentMaxDelay(Duration.ofMinutes(1))
            .build()
        val request = OneTimeWorkRequestBuilder<MoveWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(MoveWorker.KEY_INSTANT to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(INSTANT, policy, request)
    }
}
