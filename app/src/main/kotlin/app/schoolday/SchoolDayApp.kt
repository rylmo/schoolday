package app.schoolday

import android.app.Application
import app.schoolday.data.Prefs
import app.schoolday.ui.L
import app.schoolday.data.Repository
import app.schoolday.notify.LunchNotifier
import app.schoolday.work.SyncWorker

class SchoolDayApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repository(this, prefs)
        L.apply(prefs.value.lang)
        LunchNotifier.createChannel(this)
        SyncWorker.schedulePeriodic(this)
    }
}

val android.content.Context.app: SchoolDayApp get() = applicationContext as SchoolDayApp
