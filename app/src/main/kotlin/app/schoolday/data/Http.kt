package app.schoolday.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android) Schoolday/${app.schoolday.BuildConfig.VERSION_NAME} (unofficial student app)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** A separate client with a cookie jar, used for the logged-in iCanteen session. */
    fun sessionClient(jar: CookieJar = MemoryCookieJar()): OkHttpClient =
        client.newBuilder().cookieJar(jar).build()

    suspend fun get(url: String, c: OkHttpClient = client): String = withContext(Dispatchers.IO) {
        c.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { it.bodyOrThrow() }
    }
}

fun Response.bodyOrThrow(): String {
    if (!isSuccessful) throw IOException("HTTP $code from ${request.url.host}")
    return body?.string() ?: throw IOException("Empty response from ${request.url.host}")
}

class MemoryCookieJar : CookieJar {
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
