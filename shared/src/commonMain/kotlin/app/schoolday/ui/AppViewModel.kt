package app.schoolday.ui

import app.schoolday.Platform
import app.schoolday.data.LunchMenu
import app.schoolday.data.Repository
import app.schoolday.data.SchoolClass
import app.schoolday.data.Settings
import app.schoolday.data.Timetable
import app.schoolday.data.TimetableMode
import app.schoolday.data.friendly
import app.schoolday.update.UpdateInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

private const val CLASS_LIST_REFRESH_DELAY_MS = 8_000L
private const val LOAD_RETRIES = 2
private const val LOAD_RETRY_DELAY_MS = 2_500L

data class UiState<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null)
data class TtKey(val classId: String, val mode: TimetableMode)

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class NeedsPermission(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Installing(val info: UpdateInfo) : UpdateUi
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateUi
}

sealed interface UiEvent {
    data class Toast(val text: String, val undo: (() -> Unit)? = null) : UiEvent
    data object Confirm : UiEvent
}

/** All app state and actions, shared by Android and iOS. */
class AppViewModel(private val platform: Platform, private val repo: Repository) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val versionName: String get() = platform.versionName
    val settings: StateFlow<Settings> = repo.prefs.state

    private val _classes = MutableStateFlow(repo.cachedClasses() ?: Repository.DEFAULT_CLASSES)
    val classes = _classes.asStateFlow()

    private val _timetables = MutableStateFlow<Map<TtKey, UiState<Timetable>>>(emptyMap())
    val timetables = _timetables.asStateFlow()

    private val _lunch = MutableStateFlow(UiState(repo.cachedLunch()))
    val lunch = _lunch.asStateFlow()

    /** Dates with an order/cancel in flight. */
    private val _lunchBusy = MutableStateFlow<Set<String>>(emptySet())
    val lunchBusy = _lunchBusy.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        val s = settings.value
        (s.shownClasses + s.myClass).distinct().forEach { id ->
            repo.cachedTimetable(id, TimetableMode.Actual)?.let { put(TtKey(id, TimetableMode.Actual), UiState(it)) }
        }
        viewModelScope.launch {
            repo.classes().data?.let { _classes.value = it }
            delay(CLASS_LIST_REFRESH_DELAY_MS) // parsing that page is heavy; keep it clear of startup
            repo.classes(force = true).data?.let { _classes.value = it }
        }
    }

    private fun put(key: TtKey, state: UiState<Timetable>) = _timetables.update { it + (key to state) }

    fun className(id: String): String = _classes.value.firstOrNull { it.id == id }?.name ?: id

    /** Classes in the Timetable tab: always your own first, then friends'. */
    fun timetableClasses(s: Settings, selected: String): List<String> =
        (listOf(s.myClass) + s.shownClasses).distinct()

    fun loadTimetable(classId: String, mode: TimetableMode, force: Boolean = false) {
        if (classId.isEmpty()) return
        val key = TtKey(classId, mode)
        val current = _timetables.value[key]
        if (current?.loading == true) return
        put(key, (current ?: UiState(repo.cachedTimetable(classId, mode))).copy(loading = true, error = null))
        viewModelScope.launch {
            var r = repo.timetable(classId, mode, force)
            // A launch-time request can fail before the phone's network is up; with nothing
            // cached that would leave the class empty until the next manual refresh.
            var retries = 0
            while (r.data == null && r.error != null && retries++ < LOAD_RETRIES) {
                delay(LOAD_RETRY_DELAY_MS)
                r = repo.timetable(classId, mode, force)
            }
            put(key, UiState(r.data, loading = false, error = r.error))
            if (classId == settings.value.myClass && mode == TimetableMode.Actual) platform.rescheduleLunch()
        }
    }

    fun refreshLunch(force: Boolean = false) {
        if (_lunch.value.loading) return
        _lunch.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val r = repo.lunch(force)
            _lunch.value = UiState(r.data, loading = false, error = r.error)
            platform.rescheduleLunch()
        }
    }

    fun refreshAll(force: Boolean = false) {
        val s = settings.value
        (s.shownClasses + s.myClass).distinct().forEach { loadTimetable(it, TimetableMode.Actual, force) }
        refreshLunch(force)
    }

    // ------------------------------------------------------------------ lunch ordering

    /**
     * Order (or switch to) lunch [number] on [date], or cancel it. The UI changes instantly;
     * iCanteen is updated in the background and the change is rolled back if it refuses.
     */
    fun lunchAction(date: String, number: Int, cancel: Boolean, isUndo: Boolean = false) {
        if (date in _lunchBusy.value) return
        val before = _lunch.value.data ?: return
        val prevOrdered = before.days.firstOrNull { it.date == date }?.ordered?.number
        _lunch.update { it.copy(data = before.withOrder(date, if (cancel) null else number)) }
        _lunchBusy.update { it + date }
        viewModelScope.launch {
            val r = repo.lunchAction(date, number, cancel)
            _lunchBusy.update { it - date }
            if (r.error != null) {
                _lunch.update { it.copy(data = r.data ?: before) }
                _events.tryEmit(UiEvent.Toast(r.error))
                return@launch
            }
            _lunch.value = UiState(r.data)
            platform.rescheduleLunch()
            _events.tryEmit(UiEvent.Confirm)
            if (isUndo) return@launch
            val d = LocalDate.parse(date)
            val text = if (cancel) S.cancelled(d) else S.ordered(number, d)
            val undo: () -> Unit = when {
                prevOrdered != null && prevOrdered != number -> { { lunchAction(date, prevOrdered, false, isUndo = true) } }
                cancel && prevOrdered != null -> { { lunchAction(date, prevOrdered, false, isUndo = true) } }
                else -> { { lunchAction(date, number, true, isUndo = true) } }
            }
            _events.tryEmit(UiEvent.Toast(text, undo))
        }
    }

    private fun LunchMenu.withOrder(date: String, number: Int?): LunchMenu = copy(days = days.map { d ->
        if (d.date != date) d else d.copy(options = d.options.map { it.copy(ordered = it.number == number) })
    })

    // ------------------------------------------------------------------ settings

    fun updateSettings(block: (Settings) -> Settings) {
        val before = settings.value
        repo.prefs.update(block)
        val after = settings.value
        if (before.lang != after.lang) {
            L.apply(after.lang)
            platform.languageChanged()
        }
        if (before.myClass != after.myClass) loadTimetable(after.myClass, TimetableMode.Actual, force = true)
        (after.shownClasses - before.shownClasses.toSet()).forEach { loadTimetable(it, TimetableMode.Actual) }
        if (before.lunchNotification != after.lunchNotification) platform.rescheduleLunch()
    }

    fun finishSetup(myClass: String, shown: List<String>) {
        updateSettings { it.copy(myClass = myClass, shownClasses = shown, onboarded = true) }
        refreshAll(force = true)
    }

    /** Saves the iCanteen login after checking it. Returns null on success or an error message. */
    suspend fun saveICanteen(user: String, pass: String): String? {
        val err = repo.testICanteen(user.trim(), pass)
        if (err == null) {
            repo.prefs.update { it.copy(icUser = user.trim(), icPass = pass) }
            refreshLunch(force = true)
        }
        return err
    }

    fun logoutICanteen() {
        repo.prefs.update { it.copy(icUser = "", icPass = "") }
        refreshLunch(force = true)
    }

    fun debugInfo(): String = repo.debugInfo()

    // ------------------------------------------------------------------ app updates

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update = _update.asStateFlow()

    init {
        platform.updater?.let { updater ->
            viewModelScope.launch {
                updater.installError.collect { msg ->
                    if (msg != null) {
                        _update.value = UpdateUi.Failed(pendingUpdate, msg)
                        updater.clearInstallError()
                    }
                }
            }
        }
    }

    private var pendingUpdate: UpdateInfo? = null

    /** Runs at every launch (quietly) and from Settings ([manual] shows the result). */
    fun checkForUpdate(manual: Boolean = false) {
        val updater = platform.updater ?: return
        if (_update.value is UpdateUi.Downloading || _update.value is UpdateUi.Installing) return
        if (manual) _update.value = UpdateUi.Checking
        viewModelScope.launch {
            val found = runCatching { updater.check() }
            found.getOrNull()?.let { pendingUpdate = it; _update.value = UpdateUi.Available(it); return@launch }
            if (manual) _update.value = if (found.isSuccess) UpdateUi.UpToDate else UpdateUi.Failed(null, found.exceptionOrNull()!!.friendly())
        }
    }

    fun openInstallSettings() { platform.updater?.openInstallSettings() }

    fun dismissUpdate() { _update.value = UpdateUi.Idle }

    fun startUpdate() {
        val info = pendingUpdate ?: return
        val updater = platform.updater ?: return
        if (!updater.canInstall()) { _update.value = UpdateUi.NeedsPermission(info); return }
        _update.value = UpdateUi.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val apk = updater.download(info) { p -> _update.value = UpdateUi.Downloading(info, p) }
                _update.value = UpdateUi.Installing(info)
                updater.install(apk)
                // iOS: SideStore takes it from here, and this app stays open behind it.
                if (updater.handsOff) _update.value = UpdateUi.Idle
            } catch (e: Exception) {
                _update.value = UpdateUi.Failed(info, e.friendly())
            }
        }
    }

    /** Back from Android's "install unknown apps" screen: carry on if it was allowed. */
    fun resumeUpdateIfAllowed() {
        if (_update.value is UpdateUi.NeedsPermission && platform.updater?.canInstall() == true) startUpdate()
    }
}
