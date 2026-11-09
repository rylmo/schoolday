package app.schoolday.data

import app.schoolday.ui.S
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

class LoginFailedException(msg: String) : IOException(msg)

/**
 * Talks to the school's iCanteen (https://jidelnicek.roznovskastredni.cz) like a browser:
 * Spring-security form login, then the month menu page, and the same order links the
 * website's buttons use (db/dbProcessOrder.jsp?...&type=make|reorder|delete).
 */
class ICanteenClient(private val username: String, private val password: String) {
    companion object {
        const val BASE = "https://jidelnicek.roznovskastredni.cz"
        const val LOGIN_PAGE = "$BASE/faces/login.jsp"
        private const val MONTH = "$BASE/faces/secured/month.jsp?terminal=false&keyboard=false&printer=false"
    }

    private val http = HttpSession()
    private var loggedIn = false
    private val agent get() = mapOf("User-Agent" to Http.userAgent)

    suspend fun login() {
        http.clearCookies()
        val loginHtml = http.get(LOGIN_PAGE, agent).body
        val doc = Ksoup.parse(loginHtml, LOGIN_PAGE)
        val form = doc.selectFirst("form[action*=j_spring_security_check]")
            ?: throw IOException("iCanteen login form not found")
        val fields = form.select("input[type=hidden]").map { it.attr("name") to it.attr("value") } +
            listOf("j_username" to username, "j_password" to password)
        val action = form.absUrl("action").ifEmpty { "$BASE/j_spring_security_check" }
        val (finalUrl, html) = http.postForm(action, fields, agent + ("Referer" to LOGIN_PAGE))
        if (finalUrl.contains("login.jsp") || looksLikeLogin(html)) throw LoginFailedException(S.errBadLogin)
        loggedIn = true
    }

    private fun looksLikeLogin(html: String) = html.contains("j_spring_security_check")

    private suspend fun monthPage(): String {
        if (!loggedIn) login()
        var (url, html) = http.get(MONTH, agent)
        if (url.contains("login.jsp") || looksLikeLogin(html)) {
            login()
            val r = http.get(MONTH, agent); url = r.finalUrl; html = r.body
            if (looksLikeLogin(html)) throw LoginFailedException(S.errSession)
        }
        return html
    }

    suspend fun fetchMenu(): LunchMenu {
        val days = parse(monthPage())
        if (days.isEmpty()) throw IOException("iCanteen returned no menu days")
        return LunchMenu(LunchSource.ICanteen, days, nowMillis())
    }

    /** Runs an order/cancel link taken from the menu, then returns the refreshed menu. */
    suspend fun perform(actionUrl: String): LunchMenu {
        if (!loggedIn) login()
        val full = if (actionUrl.startsWith("http")) actionUrl else "$BASE/faces/secured/$actionUrl"
        val resp = http.get(full, agent + mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to MONTH)).body
        if (looksLikeLogin(resp)) throw LoginFailedException(S.errExpired)
        // The website's own script treats any "error" in the answer as a failed order.
        if (resp.contains("error", ignoreCase = true)) throw IOException(S.errRefused)
        return fetchMenu()
    }

    // ---------------------------------------------------------------- parsing
    //
    // Real markup (iCanteen 2.17):
    //   <div id="day-2026-11-09" class="jidelnicekTop">…</div>
    //   <div id="orderContent2026-11-09" class="orderContent">
    //     <div class="jidelnicekItem…"><div class="jidelnicekItemWrapper">
    //       <div class="jidWrapLeft"><a class="btn … ordered|enabled|disabled"
    //            onClick="ajaxOrder(this, 'db/dbProcessOrder.jsp?…&type=delete|reorder|make|none…')">
    //           <span class="button-link-align">zrušit</span>
    //           <span class="smallBoldTitle button-link-align">Oběd1</span> … <span class="button-link-tick"><i class="fa fa-check">
    //       <div class="jidWrapCenter">Hrachová s krutóny; Sumeček africký … <span class="textGrey">(allergens)</span>

    fun parse(html: String): List<LunchDay> {
        val doc = Ksoup.parse(html)
        return doc.select("[id^=day-]").mapNotNull { header ->
            val date = header.id().removePrefix("day-").takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
                ?: return@mapNotNull null
            val block = doc.getElementById("orderContent$date") ?: header.nextElementSibling() ?: return@mapNotNull null
            parseDay(date, block)
        }.distinctBy { it.date }
    }

    private val orderLink = Regex("""(db/dbProcessOrder\.jsp\?[^'"]+)""")
    private val mealLabel = Regex("""Ob[eě]d\s*(\d)""", RegexOption.IGNORE_CASE)

    private fun parseDay(date: String, block: Element): LunchDay? {
        var soup: String? = null
        val options = mutableListOf<MealOption>()

        for (row in block.select(".jidelnicekItemWrapper")) {
            val button = row.selectFirst(".jidWrapLeft a") ?: row.selectFirst("[onclick*=dbProcessOrder]")
            val label = row.selectFirst(".smallBoldTitle")?.text().orEmpty()
            val number = mealLabel.find(label)?.groupValues?.get(1)?.toIntOrNull() ?: continue // breakfast/dinner

            val link = button?.attr("onclick")?.let { orderLink.find(it)?.groupValues?.get(1)?.replace("&amp;", "&") }
            val type = link?.let { Regex("""[?&]type=(\w+)""").find(it)?.groupValues?.get(1) }
            val classes = button?.classNames().orEmpty()
            val ordered = type == "delete" || "ordered" in classes ||
                row.selectFirst(".jidWrapLeft .button-link-tick, .jidWrapLeft .fa-check") != null

            // "Soup , ; Main dish , sides , nápoje" → soup + main
            val center = row.selectFirst(".jidWrapCenter")?.clone() ?: continue
            center.select(".textGrey").remove()
            val parts = tidy(center.text()).split(';').map { tidy(it) }.filter { it.isNotEmpty() }
            val name = parts.lastOrNull() ?: continue
            if (parts.size > 1 && soup == null) soup = parts.dropLast(1).joinToString(", ")

            options += MealOption(
                number = number,
                name = name,
                id = link?.let { Regex("""[?&]ID=(\d+)""").find(it)?.groupValues?.get(1) }.orEmpty(),
                ordered = ordered,
                orderUrl = if (type == "make" || type == "reorder") link else null,
                cancelUrl = if (type == "delete") link else null,
            )
        }
        if (options.isEmpty()) return null
        return LunchDay(date, soup, options.distinctBy { it.number }.sortedBy { it.number })
    }

    /** "Špagety po milánsku ,  jarní cibulka ,  nápoje" → "Špagety po milánsku, jarní cibulka, nápoje" */
    private fun tidy(s: String): String = s
        .replace('\u00A0', ' ')
        .replace(Regex("""\s+,"""), ",")
        .replace(Regex(""",(?=\S)"""), ", ")
        .replace(Regex("""\s+"""), " ")
        .trim().trim(',', ' ')
}
