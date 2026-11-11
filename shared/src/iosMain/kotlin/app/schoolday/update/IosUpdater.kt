package app.schoolday.update

import app.schoolday.KeyValues
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
 * a link. Each GitHub release has an ios-update.json next to its .ipa: "ios-test" holds the stable
 * build everyone gets, "ios-testing" the newest test build for phones with Settings › Test builds
 * on. A higher build number than ours means there is something newer; "Update" passes it to
 * [installer].
 */
class IosUpdater(
    private val buildNumber: () -> Int,
    private val kv: KeyValues,
    private val installer: UpdateInstaller,
) : AppUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    private val _installError = MutableStateFlow<String?>(null)
    override val installError = _installError.asStateFlow()
    override fun clearInstallError() { _installError.value = null }

    override val handsOff: Boolean get() = true

    override suspend fun check(): UpdateInfo? {
        val test = kv.getBoolean(TEST_BUILDS, false)
        // Turning test builds off: the stable build is offered even though its number is lower,
        // until the phone is back on it.
        if (kv.getBoolean(LAST_CHECK_TEST, test) && !test) kv.putBoolean(LEAVING_TEST, true)
        kv.putBoolean(LAST_CHECK_TEST, test)

        val info = json.decodeFromString(UpdateInfo.serializer(), Http.get(if (test) TESTING else STABLE))
        val ours = buildNumber()
        if (!test && info.versionCode == ours) kv.putBoolean(LEAVING_TEST, false)
        val backToStable = !test && kv.getBoolean(LEAVING_TEST, false) && info.versionCode != ours
        return info.takeIf { it.versionCode > ours || backToStable }
    }

    override fun canInstall(): Boolean = true
    override fun openInstallSettings() {}

    /** Nothing to download here: SideStore fetches the .ipa itself. */
    override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): String = info.url

    override suspend fun install(path: String) = installer.install(path)

    companion object {
        /** Settings › Test builds; the same key the shared settings store it under. */
        const val TEST_BUILDS = "testBuilds"
        private const val LAST_CHECK_TEST = "updateLastCheckTest"
        private const val LEAVING_TEST = "updateLeavingTest"
        private const val STABLE = "https://github.com/rylmo/schoolday/releases/download/ios-test/ios-update.json"
        private const val TESTING = "https://github.com/rylmo/schoolday/releases/download/ios-testing/ios-update.json"
    }
}
