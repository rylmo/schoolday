package app.schoolday.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.schoolday.app
import app.schoolday.data.TimetableMode
import app.schoolday.notify.LunchNotifier
import java.util.concurrent.TimeUnit

/**
 * Background refresh: your own class (for lunch detection), the shown classes and the lunch menu.
 * Everything lands in the offline cache, then today's lunch notification is (re)scheduled.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val s = app.prefs.value
        app.repo.timetable(s.myClass, TimetableMode.Actual, force = true)
        s.shownClasses.forEach { app.repo.timetable(it, TimetableMode.Actual, force = true) }
        app.repo.lunch(force = true)
        LunchNotifier.reschedule(applicationContext)
        return Result.success()
    }

    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(ctx: Context) {
            // A random start offset spreads many phones out, so the school's servers
            // never get every user's refresh in the same minute.
            val req = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS, 45, TimeUnit.MINUTES)
                .setConstraints(network)
                .setInitialDelay((0..40L).random(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
