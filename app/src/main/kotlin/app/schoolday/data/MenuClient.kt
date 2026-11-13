package app.schoolday.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException

/** Read-only menu from https://www.roznovskastredni.cz/jidelnicek (fallback when iCanteen is down). */
object MenuClient {
    private const val URL = "https://www.roznovskastredni.cz/jidelnicek"

    suspend fun fetch(): LunchMenu {
        val days = parse(Http.get(URL))
        if (days.isEmpty()) throw IOException("No menu found on the school website")
        return LunchMenu(LunchSource.Website, days, System.currentTimeMillis())
    }

    fun parse(html: String): List<LunchDay> {
        val doc = Jsoup.parse(html)
        return doc.select("div.jidelnicekDen").mapNotNull { dayEl ->
            val date = dayEl.selectFirst("[id^=day-]")?.id()?.removePrefix("day-") ?: return@mapNotNull null
            var soup: String? = null
            var breakfast: String? = null
            var dinner: String? = null
            val options = mutableListOf<MealOption>()
            for (row in dayEl.select("div.container")) {
                val label = row.selectFirst(".shrinkedColumn")?.text()?.trim().orEmpty()
                val col = row.selectFirst(".column") ?: continue
                val (name, allergens) = splitAllergens(col)
                if (name.isBlank()) continue
                when {
                    label.startsWith("Polévka", ignoreCase = true) -> soup = name
                    label.startsWith("Oběd", ignoreCase = true) -> {
                        val n = Regex("""\d+""").find(label)?.value?.toIntOrNull() ?: (options.size + 1)
                        options += MealOption(number = n, name = name, allergens = allergens)
                    }
                    label.startsWith("Snídaně", ignoreCase = true) -> breakfast = name
                    label.startsWith("Večeře", ignoreCase = true) -> dinner = name
                }
            }
            if (options.isEmpty() && soup == null) null
            else LunchDay(date, soup, options.sortedBy { it.number }, breakfast, dinner)
        }
    }

    private fun splitAllergens(col: Element): Pair<String, String> {
        val c = col.clone()
        val allergens = c.select(".textGrey").text().trim().removePrefix("(").removeSuffix(")")
        c.select(".textGrey").remove()
        return c.text().trim() to allergens
    }
}
