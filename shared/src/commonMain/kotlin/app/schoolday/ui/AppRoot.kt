package app.schoolday.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.schoolday.data.ICanteenClient
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
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
import app.schoolday.data.todayDate
import app.schoolday.ui.theme.AppTheme
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.SchoolDayTheme
import app.schoolday.ui.theme.T
import kotlinx.coroutines.delay

/** Notification tap: open the Lunch tab with today's details. */
const val OPEN_LUNCH_TODAY = "lunch_today"

/** Widget tap on iOS: open the Timetable tab on "<class id>" or "<class id>@<yyyy-MM-dd>" after this prefix. */
const val OPEN_TIMETABLE = "timetable:"

/** Back gesture on Android; nothing on iOS, where the glass back button is used. */
@Composable
expect fun SimpleBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun ProgressBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit)

/**
 * Connects the shared screens to controls drawn by the host platform. On iOS the tab switcher
 * and back button are native Liquid Glass views floating above the Compose content.
 */
class NativeChrome {
    internal var selectTab: (Int) -> Unit = {}
    internal var goBack: () -> Unit = {}
    internal var showSettings: () -> Unit = {}
    internal var timetable: TimetableActions? = null
    internal var startUpdateAction: () -> Unit = {}
    internal var dismissUpdateAction: () -> Unit = {}
    private var replay: ((ChromeListener) -> Unit)? = null
    private var replayTimetable: ((ChromeListener) -> Unit)? = null
    private var replayUpdate: ((ChromeListener) -> Unit)? = null

    var listener: ChromeListener? = null
        set(value) {
            field = value
            if (value != null) {
                replay?.invoke(value)
                replayTimetable?.invoke(value)
                replayUpdate?.invoke(value)
            }
        }

    /** The app's texts in the current language, for the native controls' labels. */
    val text: Strings get() = S
    val iCanteenUrl: String get() = ICanteenClient.LOGIN_PAGE

    fun select(tab: Int) = selectTab(tab)
    fun back() = goBack()
    fun openSettings() = showSettings()
    fun toggleWeekView() { timetable?.toggleWeekView?.invoke() }
    fun togglePermanent() { timetable?.togglePermanent?.invoke() }
    fun startUpdate() = startUpdateAction()
    fun dismissUpdate() = dismissUpdateAction()

    internal fun publish(tab: Int, settingsOpen: Boolean, visible: Boolean, dark: Boolean, titles: List<String>) {
        val send: (ChromeListener) -> Unit = { it.changed(tab, settingsOpen, visible, dark, titles) }
        replay = send
        listener?.let(send)
    }

    internal fun publishTimetable(weekView: Boolean, permanent: Boolean) {
        val send: (ChromeListener) -> Unit = { it.timetableChanged(weekView, permanent) }
        replayTimetable = send
        listener?.let(send)
    }

    /** A newer version to offer ([version] is its name), or none. */
    internal fun publishUpdate(version: String?) {
        val send: (ChromeListener) -> Unit = { it.updateChanged(version != null, version.orEmpty()) }
        replayUpdate = send
        listener?.let(send)
    }
}

/** What the Timetable screen's native toolbar buttons do. */
class TimetableActions(val toggleWeekView: () -> Unit, val togglePermanent: () -> Unit)

/** Implemented by the host app to draw its native controls. */
interface ChromeListener {
    fun changed(tab: Int, settingsOpen: Boolean, visible: Boolean, dark: Boolean, titles: List<String>)
    fun timetableChanged(weekView: Boolean, permanent: Boolean)
    fun updateChanged(available: Boolean, version: String)
}

/** Set on iOS, where toolbar buttons and Settings are native; screens then leave them out. */
val LocalNativeChrome = staticCompositionLocalOf<NativeChrome?> { null }

/** The whole app: theme, setup or main screens, startup animation and update prompt. */
@Composable
fun SchoolApp(vm: AppViewModel, openRequest: MutableState<String?>, intro: IntroState, chrome: NativeChrome? = null) {
    val settings by vm.settings.collectAsState()
    LaunchedEffect(settings.lang) { L.apply(settings.lang) }
    SchoolDayTheme(AppTheme.of(settings.theme)) {
        CompositionLocalProvider(LocalBottomInset provides if (chrome != null) 84.dp else 0.dp, LocalNativeChrome provides chrome) {
            Box(Modifier.fillMaxSize().background(T.bg)) {
                if (settings.onboarded) AppRoot(vm, openRequest, intro, chrome) else SetupScreen(vm)
                if (!intro.finished) IntroOverlay(intro)
                else {
                    val update by vm.update.collectAsState()
                    if (chrome == null) {
                        UpdatePrompt(update, onUpdate = vm::startUpdate, onLater = vm::dismissUpdate, onAllow = vm::openInstallSettings)
                    } else {
                        // iOS shows a native (Liquid Glass) alert in the middle of the screen instead.
                        DisposableEffect(chrome) {
                            chrome.startUpdateAction = vm::startUpdate
                            chrome.dismissUpdateAction = vm::dismissUpdate
                            onDispose { chrome.startUpdateAction = {}; chrome.dismissUpdateAction = {} }
                        }
                        val version = (update as? UpdateUi.Available)?.info?.versionName
                        LaunchedEffect(version) { chrome.publishUpdate(version) }
                    }
                }
            }
        }
    }
}

private enum class Tab { Today, Timetable, Lunch }

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun AppRoot(vm: AppViewModel, openRequest: MutableState<String?>, intro: IntroState, chrome: NativeChrome?) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var lunchSheetDate by rememberSaveable { mutableStateOf<String?>(null) }
    val settings by vm.settings.collectAsState()
    // The Timetable tab shows your own class unless you came from another class on Today
    // (or from the widget); picking the tab itself always goes back to yours.
    var selectedClass by rememberSaveable { mutableStateOf(settings.myClass) }
    fun selectTab(next: Tab) {
        if (next == Tab.Timetable && tab != Tab.Timetable) selectedClass = settings.myClass
        tab = next
    }
    var backProgress by remember { mutableFloatStateOf(0f) }
    /** A day the Timetable tab should jump to (from the iOS widget), until it has. */
    var timetableDate by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<UiEvent.Toast?>(null) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(openRequest.value) {
        val request = openRequest.value
        if (request == OPEN_LUNCH_TODAY) {
            showSettings = false
            tab = Tab.Lunch
            lunchSheetDate = todayDate().toString()
            openRequest.value = null
        } else if (request != null && request.startsWith(OPEN_TIMETABLE)) {
            val target = request.removePrefix(OPEN_TIMETABLE)
            showSettings = false
            selectedClass = target.substringBefore('@')
            timetableDate = target.substringAfter('@', "").ifEmpty { null }
            tab = Tab.Timetable
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

    if (chrome != null) {
        DisposableEffect(chrome) {
            chrome.selectTab = { i -> selectTab(Tab.entries[i]) }
            chrome.goBack = { showSettings = false }
            chrome.showSettings = { showSettings = true }
            onDispose { chrome.selectTab = {}; chrome.goBack = {}; chrome.showSettings = {} }
        }
        val titles = listOf(S.today, S.timetable, if (settings.allMeals) S.menu else S.lunch)
        val dark = T.isDark
        LaunchedEffect(tab, showSettings, intro.finished, titles, dark) {
            // Settings is a native sheet over the tab bar, so the bar stays.
            chrome.publish(tab.ordinal, showSettings, intro.finished, dark, titles)
        }
    }

    SimpleBackHandler(enabled = !showSettings && tab != Tab.Today) { tab = Tab.Today }
    ProgressBackHandler(enabled = showSettings, onProgress = { backProgress = it }) { showSettings = false }

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
                    Tab.Timetable -> TimetableScreen(
                        vm, selectedClass, onClassChange = { selectedClass = it }, onSettings = { showSettings = true },
                        openDate = timetableDate, onOpened = { timetableDate = null },
                    )
                    Tab.Lunch -> LunchScreen(vm, lunchSheetDate, onSheetDate = { lunchSheetDate = it }, onSettings = { showSettings = true })
                }
            }
            if (chrome == null) {
                NavBar(
                    listOf(S.today to Ic.Today, S.timetable to Ic.Timetable, (if (settings.allMeals) S.menu else S.lunch) to Ic.Lunch),
                    tab.ordinal,
                ) { selectTab(Tab.entries[it]) }
            }
        }

        AnimatedVisibility(
            toast != null, Modifier.align(Alignment.BottomCenter).padding(bottom = if (chrome != null) 104.dp else 96.dp),
            enter = slideInVertically(tween(260, easing = Emphasized)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        ) {
            val t = toast
            if (t != null) UndoBar(t.text, t.undo?.let { u -> { u(); toast = null } })
        }

        // On iOS, Settings is a native sheet drawn by the host app.
        if (chrome == null) SettingsLayer(vm, showSettings, backProgress) { showSettings = false }
    }
}

@Composable
private fun SettingsLayer(vm: AppViewModel, showSettings: Boolean, backProgress: Float, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
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
            }) { SettingsScreen(vm, showBack = true, onBack = onBack) }
        }
    }
}
