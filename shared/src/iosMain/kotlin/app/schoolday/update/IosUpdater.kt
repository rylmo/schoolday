package app.schoolday.update

import app.schoolday.data.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication

/**
 * iPhone updates: iOS doesn't let an app install itself, but SideStore can install an .ipa from
 * a link. The GitHub build publishes ios-update.json next to the .ipa; a higher build number
 * than ours means there is something newer, and "Update" opens it in SideStore.
 */
class IosUpdater(private val buildNumber: () -> Int) : AppUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    private val _installError = MutableStateFlow<String?>(null)
    override val installError = _installError.asStateFlow()
    override fun clearInstallError() { _installError.value = null }

    override val handsOff: Boolean get() = true

    override suspend fun check(): UpdateInfo? {
        val info = json.decodeFromString(UpdateInfo.serializer(), Http.get(MANIFEST))
        return info.takeIf { it.versionCode > buildNumber() }
    }

    override fun canInstall(): Boolean = true
    override fun openInstallSettings() {}

    /** Nothing to download here: SideStore fetches the .ipa itself. */
    override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): String = info.url

    @Suppress("CAST_NEVER_SUCCEEDS")
    override suspend fun install(path: String) {
        val encoded = (path as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: path
        val sideStore = NSURL.URLWithString("sidestore://install?url=$encoded")
        val app = UIApplication.sharedApplication
        if (sideStore == null) {
            openInBrowser(path)
            return
        }
        app.openURL(sideStore, emptyMap<Any?, Any>()) { opened ->
            // No SideStore on this phone: download the .ipa in Safari instead.
            if (!opened) openInBrowser(path)
        }
    }

    private fun openInBrowser(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
    }

    private companion object {
        const val MANIFEST = "https://github.com/rylmo/schoolday/releases/download/ios-test/ios-update.json"
    }
}
