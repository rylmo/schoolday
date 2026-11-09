package app.schoolday

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.schoolday.notify.LunchNotifier
import app.schoolday.update.AppUpdater
import app.schoolday.update.Updater
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidPlatform(private val ctx: Context) : Platform {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "cache").apply { mkdirs() }

    override val versionName: String get() = BuildConfig.VERSION_NAME
    override val deviceInfo: String
        get() = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    override val keyValues = object : KeyValues {
        override fun getString(key: String) = sp.getString(key, null)
        override fun getBoolean(key: String, default: Boolean) = sp.getBoolean(key, default)
        override fun contains(key: String) = sp.contains(key)
        override fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
        override fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
    }

    override val secureStore = object : SecureStore {
        override fun get(key: String): String? {
            // Very old versions stored the password in plain text: move it to the encrypted slot.
            sp.getString(key, null)?.takeIf { it.isNotEmpty() }?.let { plain ->
                Crypto.encrypt(plain)?.let { sp.edit().putString("${key}Enc", it).remove(key).apply() }
            }
            return sp.getString("${key}Enc", null)?.let { Crypto.decrypt(it) }
        }

        override fun set(key: String, value: String?) {
            val enc = value?.let { Crypto.encrypt(it) }
            sp.edit().apply { if (enc == null) remove("${key}Enc") else putString("${key}Enc", enc) }.remove(key).apply()
        }
    }

    override fun readCache(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun writeCache(name: String, text: String) = File(dir, name).writeText(text)
    override fun cacheAges(): List<Pair<String, Long>> =
        dir.listFiles().orEmpty().map { it.name to (System.currentTimeMillis() - it.lastModified()) / 60_000 }

    override fun introBlocker(): String? {
        val scale = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val am = ctx.getSystemService(ActivityManager::class.java)
        return when {
            scale <= 0f -> "animations are turned off on this phone"
            am?.isLowRamDevice == true -> "low-RAM phone"
            else -> null
        }
    }

    override fun rescheduleLunch() = LunchNotifier.reschedule(ctx)
    override fun languageChanged() = LunchNotifier.createChannel(ctx)
    override val updater: AppUpdater? get() = Updater.takeIf { it.enabled }
}

/** AES-GCM with a non-exportable key in the Android Keystore. */
private object Crypto {
    private const val ALIAS = "schoolday-login"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()
}
