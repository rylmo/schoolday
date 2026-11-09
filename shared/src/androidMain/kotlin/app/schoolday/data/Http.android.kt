package app.schoolday.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

actual class HttpSession actual constructor() {
    private val jar = MemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(jar)
        .build()

    private suspend fun call(request: Request): HttpResult = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${r.request.url.host}")
            HttpResult(r.request.url.toString(), r.body?.string() ?: throw IOException("Empty response from ${r.request.url.host}"))
        }
    }

    private fun builder(url: String, headers: Map<String, String>) = Request.Builder().url(url).apply {
        headers.forEach { (k, v) -> header(k, v) }
    }

    actual suspend fun get(url: String, headers: Map<String, String>): HttpResult = call(builder(url, headers).build())

    actual suspend fun postForm(url: String, fields: List<Pair<String, String>>, headers: Map<String, String>): HttpResult {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return call(builder(url, headers).post(body).build())
    }

    actual fun clearCookies() = jar.clear()
}

actual fun netFailureOf(e: Throwable): NetFailure? = when (e) {
    is UnknownHostException -> NetFailure.NoInternet
    is SocketTimeoutException -> NetFailure.Timeout
    is ConnectException -> NetFailure.Connect
    else -> null
}

actual fun deviceLanguage(): String = java.util.Locale.getDefault().language

private class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store["${it.domain}|${it.path}|${it.name}"] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.values.removeAll { it.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = store.clear()
}
