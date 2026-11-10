package app.schoolday.update

import app.schoolday.data.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Implemented by the Swift app: gets LocalDevVPN running, then hands the .ipa link to SideStore. */
interface UpdateInstaller {
    fun install(url: String)
}

/**
 * iPhone updates: iOS doesn't let an app install itself, but SideStore can install an .ipa from
 * a link. The GitHub build publishes ios-update.json next to the .ipa; a higher build number
 * than ours means there is something newer, and "Update" passes it to [installer].
 */
class IosUpdater(private val buildNumber: () -> Int, private val installer: UpdateInstaller) : AppUpdater {
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

    override suspend fun install(path: String) = installer.install(path)

    private companion object {
        const val MANIFEST = "https://github.com/rylmo/schoolday/releases/download/ios-test/ios-update.json"
    }
}
