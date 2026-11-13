package app.schoolday

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import app.schoolday.data.Http
import app.schoolday.data.Prefs
import app.schoolday.data.Repository
import app.schoolday.ui.AppViewModel
import app.schoolday.ui.IntroState
import app.schoolday.ui.L
import app.schoolday.ui.NativeChrome
import app.schoolday.ui.OPEN_LUNCH
import app.schoolday.ui.OPEN_TIMETABLE
import app.schoolday.ui.SchoolApp
import app.schoolday.update.AppUpdater
import app.schoolday.update.IosUpdater
import app.schoolday.update.UpdateInstaller
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import app.schoolday.data.Groups
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPlatform(override val secureStore: SecureStore, installer: UpdateInstaller, private val shared: SharedFiles) : Platform {
    private val defaults = NSUserDefaults.standardUserDefaults

    private val dir: String = run {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        "$base/cache".also { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }
    }

    /** "1.2 (31)" on the stable release, "1.2 · test 31" with Settings › Test builds on. */
    override val versionName: String
        get() {
            val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
            return if (keyValues.getBoolean(IosUpdater.TEST_BUILDS, false)) "$version · test $buildNumber" else "$version ($buildNumber)"
        }

    private val buildNumber: Int
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 0

    // Lazy: keyValues is declared further down and isn't set yet while this line would run.
    override val updater: AppUpdater by lazy { IosUpdater({ buildNumber }, keyValues, installer) }

    override val deviceInfo: String
        get() = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}, ${UIDevice.currentDevice.model}"

    override val keyValues = object : KeyValues {
        override fun getString(key: String) = defaults.stringForKey(key)
        override fun getBoolean(key: String, default: Boolean) = if (contains(key)) defaults.boolForKey(key) else default
        override fun contains(key: String) = defaults.objectForKey(key) != null
        override fun putString(key: String, value: String) = defaults.setObject(value, key)
        override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)
    }

    override fun readCache(name: String): String? =
        NSString.stringWithContentsOfFile("$dir/$name", NSUTF8StringEncoding, null)

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun writeCache(name: String, text: String) {
        (text as NSString).writeToFile("$dir/$name", true, NSUTF8StringEncoding, null)
        // The Lunch Lock Screen widget shows what's ordered, which only the app knows.
        if (name == LUNCH_CACHE) shared.write(name, text)
    }

    override fun cacheAges(): List<Pair<String, Long>> = emptyList()

    override fun introBlocker(): String? = if (UIAccessibilityIsReduceMotionEnabled()) "Reduce Motion is on" else null
}

/** Entry point used by the SwiftUI app. */
class SchoolIos(secure: SecureStore, installer: UpdateInstaller, private val shared: SharedFiles) {
    val chrome = NativeChrome()
    private val vm: AppViewModel
    private val intro: IntroState

    /** The native Settings screen's access to the shared settings. */
    val settingsBridge: SettingsBridge

    init {
        val platform = IosPlatform(secure, installer, shared)
        Http.setVersion(platform.versionName)
        val prefs = Prefs(platform.keyValues, platform.secureStore)
        L.apply(prefs.value.lang)
        vm = AppViewModel(platform, Repository(platform, prefs))
        settingsBridge = SettingsBridge(vm)
        intro = IntroState(willPlay = prefs.value.onboarded && platform.introBlocker() == null)
        // SwiftUI doesn't reliably report "became active" for the launch itself, and Today has no load of its own.
        vm.refreshAll()
        // Quietly, once per launch: a newer test build offers itself through SideStore.
        vm.checkForUpdate()
        shareWithWidgets(platform)
    }

    fun makeViewController(): UIViewController = ComposeUIViewController {
        SchoolApp(vm, openRequest, intro, chrome)
    }

    /**
     * From the widget: schoolday://timetable/<class id>?date=yyyy-MM-dd opens that class on that day,
     * schoolday://lunch?date=yyyy-MM-dd (while it shows the menu) the Lunch tab.
     */
    fun openLink(url: String) {
        if (url.startsWith("schoolday://lunch")) {
            if (!intro.finished) intro.cancelled = true
            openRequest.value = OPEN_LUNCH + url.substringAfter("date=", "").substringBefore('&')
            return
        }
        val rest = url.substringAfter("schoolday://timetable/", "")
        val classId = rest.substringBefore('?').trim('/')
        val date = rest.substringAfter("date=", "").substringBefore('&')
        if (classId.isEmpty()) return
        // Opened from the widget while the app wasn't running: skip the launch animation, which
        // would otherwise play over the Timetable it opens.
        if (!intro.finished) intro.cancelled = true
        openRequest.value = OPEN_TIMETABLE + classId + (if (date.isNotEmpty()) "@$date" else "")
    }

    /** Called whenever the app comes to the foreground. */
    fun onForeground() = vm.refreshAll()

    /** After an update was handed to SideStore and the app is still running the old build: offer it again. */
    fun recheckUpdate() = vm.checkForUpdate()

    /** Gives the widgets the saved lunch menu and your class (their default when none is chosen). */
    private fun shareWithWidgets(platform: IosPlatform) {
        platform.readCache(LUNCH_CACHE)?.let { shared.write(LUNCH_CACHE, it) }
        MainScope().launch {
            combine(vm.settings, vm.classes) { s, _ -> WidgetClass(s.myClass, vm.className(s.myClass), s.allMeals, Groups.hidden(s.groups), s.groups.keys) }
                .distinctUntilChanged()
                .collect { c ->
                    shared.write("myclass.json", buildJsonObject {
                        put("id", c.id)
                        put("name", c.name)
                        // The widget's menu shows breakfast and dinner too.
                        put("allMeals", c.allMeals)
                        // Lessons of these groups are left out of your class's widgets.
                        put("hiddenGroups", buildJsonArray { c.hiddenGroups.sorted().forEach { add(it) } })
                        // Splits with a group picked; the others get a "!" on the widget.
                        put("pickedSplits", buildJsonArray { c.pickedSplits.sorted().forEach { add(it) } })
                    }.toString())
                }
        }
    }

    private val openRequest = mutableStateOf<String?>(null)

    private data class WidgetClass(
        val id: String, val name: String, val allMeals: Boolean, val hiddenGroups: Set<String>, val pickedSplits: Set<String>,
    )
}

/** The lunch menu's cache file, also handed to the widgets. */
private const val LUNCH_CACHE = "lunch.json"
