package app.schoolday.update

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Contents of update.json, published next to each release. */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val minSdk: Int = 0,
)

/** Self-updating, for platforms that allow it. */
interface AppUpdater {
    val installError: StateFlow<String?>
    fun clearInstallError()

    /** The newer version on offer, or null if up to date. Throws on network problems. */
    suspend fun check(): UpdateInfo?
    fun canInstall(): Boolean
    fun openInstallSettings()

    /** Downloads and verifies the update; returns the local file's path. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): String
    suspend fun install(path: String)
}
