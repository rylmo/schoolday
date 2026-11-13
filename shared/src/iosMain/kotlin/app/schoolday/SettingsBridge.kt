package app.schoolday

import androidx.compose.ui.graphics.toArgb
import app.schoolday.data.Groups
import app.schoolday.data.SchoolClass
import app.schoolday.data.Timetable
import app.schoolday.data.TimetableMode
import app.schoolday.ui.TtKey
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

/**
 * One split subject on the Your groups page. [options] start with "All" (key ""), each with the
 * teacher or subjects in [SettingsOption.group]; [picked] is the chosen key ("" for all).
 */
class GroupChoiceRow(val key: String, val title: String, val kind: String, val options: List<SettingsOption>, val picked: String)

/** Everything the native Settings screen shows, rebuilt whenever something changes. */
class SettingsSnapshot(
    val loggedIn: Boolean,
    val icUser: String,
    val shownClasses: List<SettingsOption>,
    val addableClasses: List<SettingsOption>,
    val allClasses: List<SettingsOption>,
    val myClass: String,
    val theme: String,
    val themes: List<SettingsOption>,
    val language: String,
    val languages: List<SettingsOption>,
    val version: String,
    /** "Checking…", "You're on the latest version", "Version 1.2 (58) is available", or the error. */
    val updateStatus: String,
    val updateAvailable: Boolean,
    val testBuilds: Boolean,
    val allMeals: Boolean,
    /** Your class's split subjects; empty while its timetable loads or when it has none. */
    val groups: List<GroupChoiceRow>,
    /** "5 of 6 set · IT3B" under Your groups. */
    val groupsSummary: String,
    val groupsLoading: Boolean,
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
            combine(vm.settings, vm.classes, vm.update, vm.rawTimetables) { _, classes, _, _ -> snapshot(classes) }
                .collect { listener.changed(it) }
        }
        loadGroups()
    }

    /** The permanent timetable has every split, without this week's changes. */
    fun loadGroups() = vm.loadTimetable(vm.settings.value.myClass, TimetableMode.Permanent)

    private fun splits(myClass: String): Timetable? =
        vm.rawTimetables.value[TtKey(myClass, TimetableMode.Permanent)]?.data
            ?: vm.rawTimetables.value[TtKey(myClass, TimetableMode.Actual)]?.data

    /** Picks [code] in the split [key]; "" shows every group again. */
    fun pickGroup(key: String, code: String) = vm.updateSettings {
        it.copy(groups = if (code.isEmpty()) it.groups - key else it.groups + (key to code))
    }

    /** Every version's changes, newest first, in the app's language. */
    fun releaseNotes(): List<ReleaseNotes> = IosChangelog.all()

    private fun snapshot(classes: List<SchoolClass>): SettingsSnapshot {
        val s = vm.settings.value
        fun option(id: String) = SettingsOption(id, vm.className(id))
        val tt = splits(s.myClass)
        val choices = tt?.let { Groups.choices(it) }.orEmpty()
        val groups = choices.map { c ->
            GroupChoiceRow(
                key = c.key,
                title = c.title,
                kind = if (c.sameSubject) S.yourGroup else S.yourSubject,
                options = listOf(SettingsOption("", S.allGroups, S.everyGroup)) + c.options.map { SettingsOption(it.code, it.code, it.detail) },
                picked = s.groups[c.key]?.takeIf { pick -> c.options.any { it.code == pick } }.orEmpty(),
            )
        }
        return SettingsSnapshot(
            loggedIn = s.hasICanteen,
            icUser = s.icUser,
            shownClasses = s.shownClasses.map(::option),
            addableClasses = classes.filter { it.id !in s.shownClasses && it.id != s.myClass }.map { SettingsOption(it.id, it.name) },
            allClasses = classes.map { SettingsOption(it.id, it.name) },
            myClass = s.myClass,
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
            testBuilds = s.testBuilds,
            allMeals = s.allMeals,
            groups = groups,
            groupsSummary = when {
                tt == null -> S.loading
                groups.isEmpty() -> S.noSplits
                else -> S.groupsSet(groups.count { it.picked.isNotEmpty() }, groups.size, vm.className(s.myClass))
            },
            groupsLoading = tt == null,
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

    fun updateTheme(key: String) = vm.updateSettings { it.copy(theme = key) }
    fun updateLanguage(key: String) = vm.updateSettings { it.copy(lang = key) }

    /** Switches between test builds and the stable release, then looks for what the new choice offers. */
    fun updateAllMeals(on: Boolean) = vm.updateSettings { it.copy(allMeals = on) }

    fun updateTestBuilds(on: Boolean) {
        vm.updateSettings { it.copy(testBuilds = on) }
        vm.checkForUpdate(manual = true)
    }
    fun updateMyClass(key: String) {
        vm.updateSettings { it.copy(myClass = key, shownClasses = it.shownClasses - key) }
        loadGroups()
    }
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
