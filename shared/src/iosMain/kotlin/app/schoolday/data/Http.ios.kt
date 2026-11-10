package app.schoolday.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.takeFrom
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual class HttpSession actual constructor() {
    private var client = newClient()

    private fun newClient() = HttpClient(Darwin) {
        // Redirects are followed by hand in send(). Ktor's own redirect plugin repeats a POST
        // (with the login form) at every hop instead of switching to GET like a browser, which
        // made iCanteen's login bounce until "Max send count 20 exceeded".
        followRedirects = false
        expectSuccess = false
        install(HttpCookies) { storage = AcceptAllCookiesStorage() }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
        engine {
            configureSession {
                // Cookies are handled above, separately for each login.
                HTTPCookieStorage = null
                HTTPShouldSetCookies = false
            }
        }
    }

    private suspend fun finish(r: HttpResponse): HttpResult {
        val url = r.request.url
        if (!r.status.isSuccess()) throw IOException("HTTP ${r.status.value} from ${url.host}")
        return HttpResult(url.toString(), r.bodyAsText())
    }

    /** One request plus its redirects, the way a browser (and OkHttp on Android) follows them. */
    private suspend fun send(url: String, headers: Map<String, String>, form: List<Pair<String, String>>?): HttpResult {
        var target = Url(url)
        var fields = form
        repeat(MAX_REDIRECTS) {
            val address = target.toString()
            val sending = fields
            val r = if (sending != null) {
                client.submitForm(address, parameters { sending.forEach { (k, v) -> append(k, v) } }) {
                    headers.forEach { (k, v) -> header(k, v) }
                }
            } else {
                client.get(address) { headers.forEach { (k, v) -> header(k, v) } }
            }
            val location = r.headers[HttpHeaders.Location]
            if (r.status.value !in REDIRECT_CODES || location == null) return finish(r)
            target = URLBuilder(target).apply {
                parameters.clear()
                fragment = ""
                takeFrom(location)
            }.build()
            // 301/302/303 after a form post continue as a plain GET; only 307/308 repeat the post.
            if (r.status.value != 307 && r.status.value != 308) fields = null
        }
        throw IOException("Too many redirects from ${target.host}")
    }

    actual suspend fun get(url: String, headers: Map<String, String>): HttpResult = send(url, headers, null)

    actual suspend fun postForm(url: String, fields: List<Pair<String, String>>, headers: Map<String, String>): HttpResult =
        send(url, headers, fields)

    actual fun clearCookies() {
        client.close()
        client = newClient()
    }

    private companion object {
        const val MAX_REDIRECTS = 10
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

actual fun netFailureOf(e: Throwable): NetFailure? = when {
    e is HttpRequestTimeoutException -> NetFailure.Timeout
    e is DarwinHttpRequestException -> when (e.origin.code) {
        -1001L -> NetFailure.Timeout
        -1009L, -1003L -> NetFailure.NoInternet
        else -> NetFailure.Connect
    }
    else -> null
}

actual fun deviceLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') ?: "en"
