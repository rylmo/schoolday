package app.schoolday

import androidx.compose.ui.graphics.toArgb
import app.schoolday.data.SchoolClass
import app.schoolday.ui.AppViewModel
import app.schoolday.ui.S
import app.schoolday.ui.Strings
import app.schoolday.ui.UpdateUi
import app.schoolday.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One choice in a native Settings list. [group] sorts themes into "dark"/"light" sections. */
class SettingsOption(val key: String, val name: String, val group: String = "", val swatch: List<String> = emptyList())

/** Everything the native Settings screen shows, rebuilt whenever something changes. */
class SettingsSnapshot(
    val loggedIn: Boolean,
    val icUser: String,
    val shownClasses: List<SettingsOption>,
    val addableClasses: List<SettingsOption>,
    val allClasses: List<SettingsOption>,
    val myClass: String,
    val showMyClass: Boolean,
    val theme: String,
    val themes: List<SettingsOption>,
    val language: String,
    val languages: List<SettingsOption>,
    val version: String,
    /** "Checking…", "You're on the latest version", "Version 1.2 (58) is available", or the error. */
    val updateStatus: String,
    val updateAvailable: Boolean,
)

/** Implemented by the SwiftUI Settings screen. */
interface SettingsListener {
    fun changed(snapshot: SettingsSnapshot)
}

/**
 * Lets the native SwiftUI Settings screen read and change the shared app's settings, so iOS gets
 * real system switches, menus and Liquid Glass buttons instead of the Compose look-alikes.
 */
class SettingsBridge internal constructor(private val vm: AppViewModel) {
    private val scope = MainScope()

    /** The app's texts in the current language. */
    val text: Strings get() = S

    fun watch(listener: SettingsListener) {
        scope.launch {
            combine(vm.settings, vm.classes, vm.update) { _, classes, _ -> snapshot(classes) }.collect { listener.changed(it) }
        }
    }

    private fun snapshot(classes: List<SchoolClass>): SettingsSnapshot {
        val s = vm.settings.value
        fun option(id: String) = SettingsOption(id, vm.className(id))
        return SettingsSnapshot(
            loggedIn = s.hasICanteen,
            icUser = s.icUser,
            shownClasses = s.shownClasses.map(::option),
            addableClasses = classes.filter { it.id !in s.shownClasses && it.id != s.myClass }.map { SettingsOption(it.id, it.name) },
            allClasses = classes.map { SettingsOption(it.id, it.name) },
            myClass = s.myClass,
            showMyClass = s.showMyClass,
            theme = s.theme,
            themes = AppTheme.entries.map { t ->
                SettingsOption(
                    key = t.id,
                    name = S.themeName(t.id),
                    group = when (t.dark) { null -> ""; true -> "dark"; false -> "light" },
                    swatch = t.preview.map { c -> (c.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0') },
                )
            },
            language = s.lang,
            languages = listOf(
                SettingsOption("system", S.langSystem),
                SettingsOption("cs", "Čeština"),
                SettingsOption("en", "English"),
            ),
            version = vm.versionName,
            updateStatus = when (val u = vm.update.value) {
                UpdateUi.Checking -> S.checkingUpdates
                UpdateUi.UpToDate -> S.upToDate
                is UpdateUi.Available -> S.updateReady(u.info.versionName)
                is UpdateUi.Failed -> u.message
                else -> S.versionLabel(vm.versionName)
            },
            updateAvailable = vm.update.value is UpdateUi.Available,
        )
    }

    fun updateShowMyClass(on: Boolean) = vm.updateSettings { it.copy(showMyClass = on) }
    fun updateTheme(key: String) = vm.updateSettings { it.copy(theme = key) }
    fun updateLanguage(key: String) = vm.updateSettings { it.copy(lang = key) }
    fun updateMyClass(key: String) = vm.updateSettings { it.copy(myClass = key, shownClasses = it.shownClasses - key) }
    fun addClass(key: String) = vm.updateSettings { it.copy(shownClasses = it.shownClasses + key) }
    fun removeClass(key: String) = vm.updateSettings { it.copy(shownClasses = it.shownClasses - key) }

    /** Checks and saves the iCanteen login; [done] gets null on success or the error to show. */
    fun logIn(user: String, password: String, done: (String?) -> Unit) {
        scope.launch { done(vm.saveICanteen(user, password)) }
    }

    fun logOut() = vm.logoutICanteen()

    fun checkForUpdates() = vm.checkForUpdate(manual = true)

    /** Hands the new build to SideStore. */
    fun installUpdate() = vm.startUpdate()

    fun debugInfo(): String = vm.debugInfo()
}
