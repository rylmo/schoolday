package app.schoolday

import app.schoolday.update.AppUpdater

/** Simple persistent settings (SharedPreferences on Android, NSUserDefaults on iOS). */
interface KeyValues {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun contains(key: String): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}

/** Encrypted storage for the iCanteen password (Android Keystore / iOS Keychain). */
interface SecureStore {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

/** Everything the shared code needs from the phone it runs on. */
interface Platform {
    val versionName: String
    val deviceInfo: String
    val keyValues: KeyValues
    val secureStore: SecureStore

    fun readCache(name: String): String?
    fun writeCache(name: String, text: String)

    /** Cached files as name to age in minutes, for the debug report. */
    fun cacheAges(): List<Pair<String, Long>>

    /** Why this phone can't play the launch animation, or null if it can. */
    fun introBlocker(): String?

    /** Re-plan today's lunch notification after new data arrived. */
    fun rescheduleLunch() {}

    /** The channel name of notifications follows the app's language. */
    fun languageChanged() {}

    /** In-app updates. Null where the platform can't update itself (iOS). */
    val updater: AppUpdater? get() = null
}
