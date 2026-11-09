package app.schoolday.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.HttpRedirect
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
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual class HttpSession actual constructor() {
    private var client = newClient()

    private fun newClient() = HttpClient(Darwin) {
        followRedirects = false
        expectSuccess = false
        // Spring Security answers the login POST with a redirect that must be followed.
        install(HttpRedirect) { checkHttpMethod = false }
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

    actual suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        finish(client.get(url) { headers.forEach { (k, v) -> header(k, v) } })

    actual suspend fun postForm(url: String, fields: List<Pair<String, String>>, headers: Map<String, String>): HttpResult =
        finish(
            client.submitForm(url, parameters { fields.forEach { (k, v) -> append(k, v) } }) {
                headers.forEach { (k, v) -> header(k, v) }
            },
        )

    actual fun clearCookies() {
        client.close()
        client = newClient()
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
