package app.schoolday

import android.app.Application
import app.schoolday.data.Http
import app.schoolday.data.Prefs
import app.schoolday.data.Repository
import app.schoolday.ui.AppViewModel
import app.schoolday.ui.L
import app.schoolday.update.Updater
import app.schoolday.notify.LunchNotifier
import app.schoolday.work.SyncWorker

class SchoolDayApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set
    private lateinit var platform: AndroidPlatform

    /** Shared app state; lives as long as the process, so it survives rotation. */
    val vm: AppViewModel by lazy { AppViewModel(platform, repo) }

    override fun onCreate() {
        super.onCreate()
        Http.setVersion(BuildConfig.VERSION_NAME)
        Updater.attach(this)
        platform = AndroidPlatform(this)
        prefs = Prefs(platform.keyValues, platform.secureStore)
        repo = Repository(platform, prefs)
        L.apply(prefs.value.lang)
        LunchNotifier.createChannel(this)
        SyncWorker.schedulePeriodic(this)
    }
}

val android.content.Context.app: SchoolDayApp get() = applicationContext as SchoolDayApp
