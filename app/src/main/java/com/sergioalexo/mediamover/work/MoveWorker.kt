package com.sergioalexo.mediamover.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.sergioalexo.mediamover.MediaMoverApp
import com.sergioalexo.mediamover.data.Storage
import com.sergioalexo.mediamover.engine.FileMover

class MoveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = (applicationContext as MediaMoverApp).store
        val instant = inputData.getBoolean(KEY_INSTANT, false)
        try {
            val rules = store.rules.value.filter { it.enabled }
            if (Storage.hasAccess() && rules.isNotEmpty()) {
                FileMover.run(applicationContext, store, rules, dryRun = false)
            }
        } finally {
            // Re-arm the MediaStore trigger; APPEND makes it start watching once this run is done.
            if (instant && store.settings.value.instant) {
                Scheduler.enqueueInstant(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_INSTANT = "instant"
    }
}
