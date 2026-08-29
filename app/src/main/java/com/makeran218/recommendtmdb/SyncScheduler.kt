package com.makeran218.recommendtmdb

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun startPeriodicSync(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            6, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag("tmdb_sync")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun triggerSync(context: Context) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag("tmdb_sync_immediate")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "tmdb_sync_immediate",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
