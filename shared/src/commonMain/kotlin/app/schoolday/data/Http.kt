package app.schoolday.data

/** Any failure talking to the school's servers. */
open class IOException(message: String) : Exception(message)

data class HttpResult(val finalUrl: String, val body: String)

/**
 * A browser-like HTTP session: follows redirects and keeps cookies between calls.
 * OkHttp on Android, NSURLSession (through Ktor) on iOS. Non-2xx responses throw [IOException].
 */
expect class HttpSession() {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult
    suspend fun postForm(url: String, fields: List<Pair<String, String>>, headers: Map<String, String> = emptyMap()): HttpResult
    fun clearCookies()
}

/** Two-letter language code of the phone. */
expect fun deviceLanguage(): String

enum class NetFailure { NoInternet, Timeout, Connect }

/** Recognises the platform's network errors, or null for anything else. */
expect fun netFailureOf(e: Throwable): NetFailure?

object Http {
    private var version = "dev"
    val userAgent: String get() = "Mozilla/5.0 Schoolday/$version (unofficial student app)"

    fun setVersion(v: String) { version = v }

    private val plain by lazy { HttpSession() }

    suspend fun get(url: String): String = plain.get(url, mapOf("User-Agent" to userAgent)).body
}
