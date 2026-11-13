package app.schoolday.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import app.schoolday.BuildConfig
import app.schoolday.data.Diag
import app.schoolday.data.Http
import app.schoolday.ui.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Contents of update.json, published next to each release. */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val minSdk: Int = 0,
)

/**
 * Self-updating: reads a small manifest from the latest GitHub release and installs newer
 * APKs through Android's PackageInstaller. Android itself refuses any APK that isn't signed
 * with the same key as the installed app, and the download is checked against the manifest.
 */
object Updater {
    private val json = Json { ignoreUnknownKeys = true }

    val enabled: Boolean get() = BuildConfig.UPDATE_MANIFEST_URL.isNotEmpty()

    /** Set when the system reports a failed install (the app is still running to show it). */
    private val _installError = MutableStateFlow<String?>(null)
    val installError = _installError.asStateFlow()
    fun clearInstallError() { _installError.value = null }
    internal fun reportInstallError(message: String) { _installError.value = message }

    /**
     * Android's "Update this app?" screen, waiting to be opened by the visible activity.
     * Opening it from the receiver let Android's installer lose track of who asked, and it
     * then cancelled the install without ever showing the screen.
     */
    private val _confirm = MutableStateFlow<Intent?>(null)
    val confirm = _confirm.asStateFlow()
    internal fun requestConfirm(intent: Intent) { _confirm.value = intent }
    fun takeConfirm(): Intent? = _confirm.value.also { _confirm.value = null }

    /** The newer version on offer, or null if up to date. Throws on network problems. */
    suspend fun check(): UpdateInfo? {
        if (!enabled) return null
        val info = json.decodeFromString(UpdateInfo.serializer(), Http.get(BuildConfig.UPDATE_MANIFEST_URL))
        return info.takeIf { it.versionCode > BuildConfig.VERSION_CODE && Build.VERSION.SDK_INT >= it.minSdk }
    }

    /** Downloads and verifies the APK. [onProgress] gets 0..1 (or -1 if the size is unknown). */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        if (!info.url.startsWith("https://")) throw IOException("Insecure update URL")
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "schoolday-${info.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        Http.client.newCall(Request.Builder().url(info.url).header("User-Agent", Http.USER_AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            var done = 0L
            var lastSent = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done - lastSent > 128 * 1024) {
                            lastSent = done
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(info.sha256, ignoreCase = true)) {
            file.delete()
            throw IOException(S.updateBadFile)
        }
        file
    }

    /** Hands the verified APK to Android. The app is closed by the system while it installs. */
    suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        Diag.update = "installing ${apk.name}"
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            // Once Schoolday installed itself, later updates can skip the system's confirm dialog.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("schoolday.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, UpdateReceiver::class.java).setPackage(ctx.packageName),
                // Mutable: the system adds the install status to this intent.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }
}

/** Receives install progress from Android: shows its confirm screen, or reports a failure. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Diag.update = "status $status" + (detail?.let { ": $it" } ?: "")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let(Updater::requestConfirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.reportInstallError(S.updateCancelled)
            else -> Updater.reportInstallError(S.updateFailed + (detail?.let { "\n$it" } ?: ""))
        }
    }
}
