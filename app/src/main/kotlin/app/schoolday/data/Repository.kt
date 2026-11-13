package app.schoolday.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Result of a load: data (possibly from cache) plus an error if the network part failed. */
data class Loaded<T>(val data: T?, val error: String? = null, val fromCache: Boolean = false)

/**
 * Single place that fetches data and keeps an offline copy of everything on disk.
 * Network errors never wipe data – the last good copy is returned with an error message.
 */
class Repository(private val context: Context, val prefs: Prefs) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir = File(context.filesDir, "cache").apply { mkdirs() }
    private val icLock = Mutex()
    private var icClient: ICanteenClient? = null
    private var icClientKey = ""

    // ------------------------------------------------------------- cache helpers

    private fun <T> readCache(name: String, s: KSerializer<T>): T? = runCatching {
        File(dir, name).takeIf { it.exists() }?.readText()?.let { json.decodeFromString(s, it) }
    }.getOrNull()

    private fun <T> writeCache(name: String, s: KSerializer<T>, value: T) {
        runCatching { File(dir, name).writeText(json.encodeToString(s, value)) }
    }

    private suspend fun <T> load(
        name: String, s: KSerializer<T>, maxAgeMs: Long, force: Boolean,
        age: (T) -> Long, fetch: suspend () -> T,
    ): Loaded<T> = withContext(Dispatchers.IO) {
        val cached = readCache(name, s)
        if (!force && cached != null && System.currentTimeMillis() - age(cached) < maxAgeMs) {
            return@withContext Loaded(cached, fromCache = true)
        }
        try {
            val fresh = fetch()
            writeCache(name, s, fresh)
            Loaded(fresh)
        } catch (e: Exception) {
            noteError(name, e)
            Loaded(cached, error = e.friendly(), fromCache = true)
        }
    }

    fun cachedTimetable(classId: String, mode: TimetableMode): Timetable? =
        readCache("tt2_${classId}_${mode.name}.json", Timetable.serializer())

    fun cachedLunch(): LunchMenu? = readCache("lunch.json", LunchMenu.serializer())
    fun cachedClasses(): List<SchoolClass>? = readCache("classes.json", ListSerializer(SchoolClass.serializer()))

    // ------------------------------------------------------------- timetable

    suspend fun timetable(classId: String, mode: TimetableMode, force: Boolean = false): Loaded<Timetable> =
        load("tt2_${classId}_${mode.name}.json", Timetable.serializer(),
            maxAgeMs = if (mode == TimetableMode.Permanent) 24 * HOUR else 20 * MIN,
            force = force, age = { it.fetchedAt }) { BakalariClient.timetable(classId, mode) }

    suspend fun classes(force: Boolean = false): Loaded<List<SchoolClass>> =
        withContext(Dispatchers.IO) {
            val name = "classes.json"
            val s = ListSerializer(SchoolClass.serializer())
            val cached = readCache(name, s)
            if (!force && cached != null) return@withContext Loaded(cached, fromCache = true)
            try {
                BakalariClient.classes().also { writeCache(name, s, it) }.let { Loaded(it) }
            } catch (e: Exception) {
                Loaded(cached ?: DEFAULT_CLASSES, e.friendly(), true)
            }
        }

    // ------------------------------------------------------------- lunch

    private fun icanteen(): ICanteenClient? {
        val s = prefs.value
        if (!s.hasICanteen) return null
        val key = s.icUser + "\u0000" + s.icPass
        if (icClient == null || icClientKey != key) {
            icClient = ICanteenClient(s.icUser, s.icPass) { html ->
                runCatching { File(context.filesDir, "icanteen_last.html").writeText(html) }
            }
            icClientKey = key
        }
        return icClient
    }

    /** The school website's menu, cached for a few hours (used for nicer names and as fallback). */
    private suspend fun webMenu(force: Boolean): LunchMenu? {
        val cached = readCache("web.json", LunchMenu.serializer())
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < 6 * HOUR) return cached
        return try {
            MenuClient.fetch().also { writeCache("web.json", LunchMenu.serializer(), it) }
        } catch (e: Exception) {
            noteError("menu", e); cached
        }
    }

    /** iCanteen first (if logged in), the school website as fallback. Names come from the website. */
    suspend fun lunch(force: Boolean = false): Loaded<LunchMenu> = withContext(Dispatchers.IO) {
        val cached = cachedLunch()
        val wantICanteen = prefs.value.hasICanteen
        val cacheUsable = cached != null && (cached.source == LunchSource.ICanteen) == wantICanteen
        if (!force && cacheUsable && System.currentTimeMillis() - cached!!.fetchedAt < 30 * MIN) {
            return@withContext Loaded(cached, fromCache = true)
        }
        var icError: String? = null
        val ic = icanteen()
        if (ic != null) {
            try {
                val menu = merge(icLock.withLock { ic.fetchMenu() }, force)
                writeCache("lunch.json", LunchMenu.serializer(), menu)
                return@withContext Loaded(menu)
            } catch (e: Exception) {
                noteError("icanteen", e)
                icError = e.friendly()
            }
        }
        val web = webMenu(force = true)
        if (web != null) {
            val fallback = web.copy(fallbackReason = icError)
            writeCache("lunch.json", LunchMenu.serializer(), fallback)
            Loaded(fallback, error = icError?.let { "iCanteen: $it" })
        } else {
            Loaded(cached, error = icError?.let { "iCanteen: $it" } ?: app.schoolday.ui.S.errMenu, fromCache = true)
        }
    }

    /**
     * iCanteen decides what's ordered and how to change it, but its dish names are messier,
     * so names, soup and allergens come from the school website (same date + lunch number).
     */
    private suspend fun merge(menu: LunchMenu, refreshWeb: Boolean = false): LunchMenu {
        val web = webMenu(refreshWeb) ?: return menu
        val webDays = web.days.associateBy { it.date }
        return menu.copy(days = menu.days.map { d ->
            val w = webDays[d.date] ?: return@map d
            d.copy(
                soup = w.soup ?: d.soup,
                options = d.options.map { o ->
                    val wo = w.options.firstOrNull { it.number == o.number } ?: return@map o
                    o.copy(name = wo.name, allergens = wo.allergens)
                },
            )
        })
    }

    /**
     * Order / switch to ([cancel] = false) or cancel ([cancel] = true) lunch [number] on [date].
     * Always works from a freshly loaded page (iCanteen's links expire), checks iCanteen's answer
     * and confirms the new state before reporting success.
     */
    suspend fun lunchAction(date: String, number: Int, cancel: Boolean): Loaded<LunchMenu> = withContext(Dispatchers.IO) {
        val ic = icanteen() ?: return@withContext Loaded(cachedLunch(), app.schoolday.ui.S.errLoginFirst)
        try {
            val result = icLock.withLock {
                val fresh = ic.fetchMenu()
                val opt = fresh.days.firstOrNull { it.date == date }?.options?.firstOrNull { it.number == number }
                    ?: throw java.io.IOException(app.schoolday.ui.S.errGone)
                val wanted = !cancel
                if (opt.ordered == wanted) return@withLock fresh // already in the right state
                val url = (if (cancel) opt.cancelUrl else opt.orderUrl)
                    ?: throw java.io.IOException(app.schoolday.ui.S.errLocked)
                val after = ic.perform(url)
                val now = after.days.firstOrNull { it.date == date }?.options?.firstOrNull { it.number == number }
                if (now?.ordered != wanted) throw java.io.IOException(app.schoolday.ui.S.errNotAccepted)
                after
            }
            val menu = merge(result)
            writeCache("lunch.json", LunchMenu.serializer(), menu)
            Loaded(menu)
        } catch (e: Exception) {
            noteError("order", e)
            Loaded(cachedLunch(), e.friendly())
        }
    }

    // ------------------------------------------------------------- diagnostics

    private val errors = ArrayDeque<String>()

    @Synchronized
    private fun noteError(where: String, e: Throwable) {
        errors.addLast("${java.time.LocalDateTime.now().withNano(0)} $where: ${e.javaClass.simpleName}: ${e.message}")
        while (errors.size > 15) errors.removeFirst()
    }

    /** Text for Settings › Copy debug info. No passwords or personal data. */
    @Synchronized
    fun debugInfo(): String = buildString {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("Schoolday ${pkg.versionName} (${pkg.longVersionCode})")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        val s = prefs.value
        appendLine("Classes: ${s.shownClasses.joinToString()} · mine: ${s.myClass} · iCanteen: ${if (s.hasICanteen) "logged in" else "no"} · theme: ${s.theme}")
        dir.listFiles()?.sortedBy { it.name }?.forEach {
            appendLine("cache ${it.name}: ${(System.currentTimeMillis() - it.lastModified()) / 60_000} min old")
        }
        appendLine("Launch animation: ${Diag.intro}")
        appendLine("Last update attempt: ${Diag.update}")
        appendLine("Recent errors:")
        if (errors.isEmpty()) appendLine("  none") else errors.forEach { appendLine("  $it") }
    }

    /** Checks the saved iCanteen login. Null = OK, otherwise an error message. */
    suspend fun testICanteen(user: String, pass: String): String? = withContext(Dispatchers.IO) {
        try {
            ICanteenClient(user, pass).login(); null
        } catch (e: Exception) {
            e.friendly()
        }
    }

    companion object {
        const val MIN = 60_000L
        const val HOUR = 60 * MIN
        val DEFAULT_CLASSES = listOf(
            SchoolClass(Settings.DEFAULT_IT3A, "IT3A"),
            SchoolClass(Settings.DEFAULT_IT2B, "IT2B"),
            SchoolClass(Settings.DEFAULT_MY_CLASS, "IT3B"),
        )
    }
}

fun Throwable.friendly(): String = when (this) {
    is LoginFailedException -> message ?: app.schoolday.ui.S.errBadLogin
    is java.net.UnknownHostException -> app.schoolday.ui.S.errNoInternet
    is java.net.SocketTimeoutException -> app.schoolday.ui.S.errTimeout
    is java.net.ConnectException -> app.schoolday.ui.S.errConnect
    else -> message ?: javaClass.simpleName
}

/** Facts for Settings > Copy debug info. */
object Diag {
    @Volatile var intro: String = "not run"
    @Volatile var update: String = "none"
}
