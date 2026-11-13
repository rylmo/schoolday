package app.schoolday.ui

import android.Manifest
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.schoolday.data.Diag
import app.schoolday.notify.LunchNotifier
import app.schoolday.update.Updater
import app.schoolday.ui.theme.AppTheme
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.SchoolDayTheme
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.isDarkNow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
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
            else -> IntroState.blocker(this)
        }
        Diag.intro = blocked?.let { "skipped: $it" } ?: "started"
        intro = IntroState(willPlay = blocked == null)
        if (savedInstanceState == null) vm.checkForUpdate()
        // Android's install confirmation is opened from here, while Schoolday is on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                Updater.confirm.collect { if (it != null) Updater.takeConfirm()?.let(::openInstallConfirm) }
            }
        }
        // Keep the launch screen until Today is laid out (max ~0.7 s), then hand over to the
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
            LaunchedEffect(settings.lang) { L.apply(settings.lang) }
            val theme = AppTheme.of(settings.theme)
            val dark = theme.isDarkNow()
            LaunchedEffect(dark) {
                val c = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c),
                )
            }
            SchoolDayTheme(theme) {
                Box(Modifier.fillMaxSize().background(T.bg)) {
                    if (settings.onboarded) AppRoot(vm, openRequest, intro) else SetupScreen(vm)
                    if (!intro.finished) IntroOverlay(intro)
                    else {
                        val update by vm.update.collectAsStateWithLifecycle()
                        UpdatePrompt(update, onUpdate = vm::startUpdate, onLater = vm::dismissUpdate, onAllow = ::openInstallSettings, onBrowser = ::openInBrowser)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = intent.getStringExtra(LunchNotifier.EXTRA_OPEN)
    }

    private fun openInstallConfirm(confirm: Intent) {
        // Tell the installer who is asking; without this Android 14+ may hide it and cancel.
        val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle() else null
        try {
            startActivity(confirm, options)
        } catch (e: Exception) {
            Diag.update = "confirm screen failed: ${e.javaClass.simpleName}: ${e.message}"
            Updater.reportInstallError(S.updateFailed)
        }
    }

    private fun openInBrowser(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) {}
    }

    private fun openInstallSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }

    override fun onResume() {
        super.onResume()
        vm.resumeUpdateIfAllowed()
        vm.refreshAll()
        LunchNotifier.reschedule(this)
    }
}

private enum class Tab(val label: String) { Today("Today"), Timetable("Timetable"), Lunch("Lunch") }

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun AppRoot(vm: AppViewModel, openRequest: MutableState<String?>, intro: IntroState) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var lunchSheetDate by rememberSaveable { mutableStateOf<String?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Timetable shows your own class unless you tapped another class on Today.
    var selectedClass by rememberSaveable { mutableStateOf(settings.myClass) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var toast by remember { mutableStateOf<UiEvent.Toast?>(null) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(openRequest.value) {
        if (openRequest.value == LunchNotifier.OPEN_LUNCH_TODAY) {
            showSettings = false
            tab = Tab.Lunch
            lunchSheetDate = LocalDate.now().toString()
            openRequest.value = null
        }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                UiEvent.Confirm -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                is UiEvent.Toast -> toast = e
            }
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(4500); toast = null } }
    LaunchedEffect(showSettings) { if (showSettings) backProgress = 0f }

    BackHandler(enabled = !showSettings && tab != Tab.Today) { tab = Tab.Today }
    PredictiveBackHandler(enabled = showSettings) { events ->
        try {
            events.collect { backProgress = it.progress }
            showSettings = false
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    Box(Modifier.fillMaxSize().background(T.bg)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab, label = "tab", modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(340, easing = Emphasized)) { dir * it / 6 } + fadeIn(tween(220, 60))) togetherWith
                        (slideOutHorizontally(tween(340, easing = Emphasized)) { -dir * it / 6 } + fadeOut(tween(120)))
                },
            ) { t ->
                when (t) {
                    Tab.Today -> TodayScreen(
                        vm,
                        onOpenLunch = { tab = Tab.Lunch },
                        onOpenClass = { selectedClass = it; tab = Tab.Timetable },
                        onSettings = { showSettings = true },
                        intro = intro,
                    )
                    Tab.Timetable -> TimetableScreen(vm, selectedClass, onClassChange = { selectedClass = it }, onSettings = { showSettings = true })
                    Tab.Lunch -> LunchScreen(vm, lunchSheetDate, onSheetDate = { lunchSheetDate = it }, onSettings = { showSettings = true })
                }
            }
            NavBar(
                listOf(S.today to Ic.Today, S.timetable to Ic.Timetable, S.lunch to Ic.Lunch),
                tab.ordinal,
            ) {
                val target = Tab.entries[it]
                if (target == Tab.Timetable) selectedClass = settings.myClass
                tab = target
            }
        }

        AnimatedVisibility(
            toast != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            enter = slideInVertically(tween(260, easing = Emphasized)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        ) {
            val t = toast
            if (t != null) UndoBar(t.text, t.undo?.let { u -> { u(); toast = null } })
        }

        val scrim by animateFloatAsState(if (showSettings) 0.5f * (1f - backProgress) else 0f, tween(300), label = "scrim")
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(tween(380, easing = Emphasized)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(300, easing = Emphasized)) { it } + fadeOut(tween(250)),
        ) {
            Box(Modifier.graphicsLayer {
                val p = backProgress
                translationX = p * size.width * 0.12f
                scaleX = 1f - 0.08f * p; scaleY = 1f - 0.08f * p
                shape = RoundedCornerShape((36 * p).dp); clip = p > 0f
            }) { SettingsScreen(vm, onBack = { showSettings = false }) }
        }
    }
}
