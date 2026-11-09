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
import app.schoolday.ui.SchoolApp
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
private class IosPlatform(override val secureStore: SecureStore) : Platform {
    private val defaults = NSUserDefaults.standardUserDefaults

    private val dir: String = run {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        "$base/cache".also { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }
    }

    override val versionName: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"

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
    }

    override fun cacheAges(): List<Pair<String, Long>> = emptyList()

    override fun introBlocker(): String? = if (UIAccessibilityIsReduceMotionEnabled()) "Reduce Motion is on" else null
}

/** Entry point used by the SwiftUI app. */
class SchoolIos(secure: SecureStore) {
    val chrome = NativeChrome()
    private val vm: AppViewModel
    private val intro: IntroState

    init {
        val platform = IosPlatform(secure)
        Http.setVersion(platform.versionName)
        val prefs = Prefs(platform.keyValues, platform.secureStore)
        L.apply(prefs.value.lang)
        vm = AppViewModel(platform, Repository(platform, prefs))
        intro = IntroState(willPlay = prefs.value.onboarded && platform.introBlocker() == null)
    }

    fun makeViewController(): UIViewController = ComposeUIViewController {
        SchoolApp(vm, openRequest, intro, chrome)
    }

    /** Called whenever the app comes to the foreground. */
    fun onForeground() = vm.refreshAll()

    private val openRequest = mutableStateOf<String?>(null)
}
