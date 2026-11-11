package app.schoolday.data

import app.schoolday.KeyValues
import app.schoolday.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val icUser: String = "",
    val icPass: String = "",
    /** Friends' classes shown in the app (IDs from Bakaláři), in display order. */
    val shownClasses: List<String> = listOf(DEFAULT_IT3A, DEFAULT_IT2B),
    /** Your own class – used to find your lunch break and for "same as you". */
    val myClass: String = DEFAULT_MY_CLASS,
    /** Also show your own class's timetable in the Timetable tab. */
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

/** App settings. The iCanteen password lives in the phone's secure storage. */
class Prefs(private val kv: KeyValues, private val secure: SecureStore) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun read() = Settings(
        icUser = kv.getString("icUser") ?: "",
        icPass = secure.get(PASSWORD) ?: "",
        shownClasses = kv.getString("shownClasses")?.split(',')?.filter { it.isNotBlank() } ?: Settings().shownClasses,
        myClass = kv.getString("myClass") ?: Settings.DEFAULT_MY_CLASS,
        lunchNotification = kv.getBoolean("lunchNotification", true),
        theme = kv.getString("theme") ?: if (kv.getBoolean("systemColors", false)) "system" else "midnight",
        // Anyone upgrading from 1.0 already set the app up.
        onboarded = kv.getBoolean("onboarded", kv.contains("myClass")),
        lang = kv.getString("lang") ?: "system",
    )

    fun update(block: (Settings) -> Settings) {
        val s = block(_state.value).let { it.copy(icUser = it.icUser.trim()) }
        secure.set(PASSWORD, s.icPass.ifEmpty { null })
        kv.putString("icUser", s.icUser)
        kv.putString("shownClasses", s.shownClasses.joinToString(","))
        kv.putString("myClass", s.myClass)
        kv.putBoolean("lunchNotification", s.lunchNotification)
        kv.putString("theme", s.theme)
        kv.putBoolean("onboarded", s.onboarded)
        kv.putString("lang", s.lang)
        _state.value = s
    }

    private companion object {
        const val PASSWORD = "icPass"
    }
}
