package com.sergioalexo.mediamover

import android.app.Application
import com.sergioalexo.mediamover.data.Store
import com.sergioalexo.mediamover.work.Scheduler

class MediaMoverApp : Application() {
    val store by lazy { Store(this) }

    override fun onCreate() {
        super.onCreate()
        Scheduler.apply(this, store.settings.value)
    }
}
