package app.schoolday.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Settings(
    val icUser: String = "",
    val icPass: String = "",
    /** Friends' classes shown in the app (IDs from Bakaláři), in display order. */
    val shownClasses: List<String> = listOf(DEFAULT_IT3A, DEFAULT_IT2B),
    /** Your own class – used to find your lunch break and for "same as you". */
    val myClass: String = DEFAULT_MY_CLASS,
    /** Also show your own class's timetable in the Timetable tab. */
    val showMyClass: Boolean = false,
    val lunchNotification: Boolean = true,
    /** Colour theme id, see AppTheme. */
    val theme: String = "midnight",
    /** First-launch setup finished. */
    val onboarded: Boolean = false,
    /** "system", "en" or "cs". */
    val lang: String = "system",
) {
    val hasICanteen get() = icUser.isNotBlank() && icPass.isNotBlank()

    companion object {
        const val DEFAULT_IT3A = "42"
        const val DEFAULT_IT2B = "4E"
        const val DEFAULT_MY_CLASS = "43" // IT3B
    }
}

/**
 * App-private settings. The iCanteen password is encrypted with a key that lives in the
 * phone's Android Keystore, so it can't be read from a backup or a copied data folder.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun read(): Settings {
        // Older versions stored the password in plain text – move it into the encrypted slot.
        sp.getString("icPass", null)?.takeIf { it.isNotEmpty() }?.let { plain ->
            Crypto.encrypt(plain)?.let { sp.edit().putString("icPassEnc", it).remove("icPass").apply() }
        }
        return Settings(
            icUser = sp.getString("icUser", "") ?: "",
            icPass = sp.getString("icPassEnc", null)?.let { Crypto.decrypt(it) } ?: "",
            shownClasses = sp.getString("shownClasses", null)?.split(',')?.filter { it.isNotBlank() }
                ?: Settings().shownClasses,
            myClass = sp.getString("myClass", null) ?: Settings.DEFAULT_MY_CLASS,
            showMyClass = sp.getBoolean("showMyClass", false),
            lunchNotification = sp.getBoolean("lunchNotification", true),
            theme = sp.getString("theme", null) ?: if (sp.getBoolean("systemColors", false)) "system" else "midnight",
            // Anyone upgrading from 1.0 already set the app up.
            onboarded = sp.getBoolean("onboarded", sp.contains("myClass")),
            lang = sp.getString("lang", null) ?: "system",
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(_state.value).let { it.copy(icUser = it.icUser.trim()) }
        val enc = if (s.icPass.isEmpty()) null else Crypto.encrypt(s.icPass)
        sp.edit()
            .putString("icUser", s.icUser)
            .apply { if (enc == null) remove("icPassEnc") else putString("icPassEnc", enc) }
            .remove("icPass")
            .putString("shownClasses", s.shownClasses.joinToString(","))
            .putString("myClass", s.myClass)
            .putBoolean("showMyClass", s.showMyClass)
            .putBoolean("lunchNotification", s.lunchNotification)
            .putString("theme", s.theme)
            .putBoolean("onboarded", s.onboarded)
            .putString("lang", s.lang)
            .apply()
        _state.value = s
    }
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
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(out, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(c.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()
}
