package app.schoolday.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schoolday.AndroidPlatform
import app.schoolday.app
import app.schoolday.data.Diag
import app.schoolday.notify.LunchNotifier
import app.schoolday.ui.theme.AppTheme
import app.schoolday.ui.theme.isDarkNow

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel get() = app.vm
    private val openRequest = mutableStateOf<String?>(null)
    private lateinit var intro: IntroState

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        LunchNotifier.reschedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        openRequest.value = intent.getStringExtra(LunchNotifier.EXTRA_OPEN)

        val onboarded = vm.settings.value.onboarded
        val blocked = when {
            savedInstanceState != null -> "activity restored"
            openRequest.value != null -> "opened from notification"
            !onboarded -> "first-launch setup"
            else -> AndroidPlatform(this).introBlocker()
        }
        Diag.intro = blocked?.let { "skipped: $it" } ?: "started"
        intro = IntroState(willPlay = blocked == null)
        if (savedInstanceState == null) vm.checkForUpdate()
        // Keep the launch screen until Today is laid out, then hand over to the
        // overlay, which starts on exactly the same picture.
        val start = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { intro.willPlay && !intro.targetsReady && SystemClock.uptimeMillis() - start < 2000 }
        splash.setOnExitAnimationListener { it.remove() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = AppTheme.of(settings.theme).isDarkNow()
            LaunchedEffect(dark) {
                val c = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c),
                )
            }
            SchoolApp(vm, openRequest, intro)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = intent.getStringExtra(LunchNotifier.EXTRA_OPEN)
    }

    override fun onResume() {
        super.onResume()
        vm.resumeUpdateIfAllowed()
        vm.refreshAll()
        LunchNotifier.reschedule(this)
    }
}
