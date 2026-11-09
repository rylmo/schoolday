package app.schoolday.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate

/**
 * Reads the public Bakaláři timetable. Each class page embeds the whole week as JSON
 * (`const timetableData = {...};`), so we parse that instead of the rendered HTML.
 */
object BakalariClient {
    private const val BASE = "https://roznovskastredni.bakalari.cz/bakaweb/Timetable/Public"

    suspend fun classes(): List<SchoolClass> {
        val doc = Jsoup.parse(Http.get(BASE))
        return doc.select("select#selectedClass option")
            .mapNotNull { o ->
                val id = o.attr("value").trim()
                val name = o.text().trim()
                if (id.isEmpty() || name.isEmpty()) null else SchoolClass(id, name)
            }
            .ifEmpty { throw IOException("No classes found on Bakaláři page") }
    }

    suspend fun timetable(classId: String, mode: TimetableMode): Timetable {
        val html = Http.get("$BASE/${mode.path}/Class/$classId")
        return parse(html, classId, mode)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(html: String, classId: String, mode: TimetableMode, today: LocalDate = LocalDate.now()): Timetable {
        val marker = "const timetableData = "
        val start = html.indexOf(marker)
        if (start < 0) throw IOException("Timetable data not found (page layout changed?)")
        val lineEnd = html.indexOf('\n', start).let { if (it < 0) html.length else it }
        val raw = html.substring(start + marker.length, lineEnd).trim().removeSuffix(";")
        val root = json.parseToJsonElement(raw).jsonObject

        val days = root["Days"]?.jsonArray.orEmpty().mapIndexed { index, d ->
            val day = d.jsonObject
            val date = resolveDate(day.str("Date"), today)
            val slots = day["Hours"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .mapNotNull { h -> parseSlot(h, date, today) }
                .sortedBy { it.start }
            TimetableDay(
                date = date,
                weekday = weekdayOf(day.str("DayAbbrev"), index),
                dayOff = day.bool("DayOff"),
                dayOffName = day.str("DayOffName"),
                slots = slots,
            )
        }
        return Timetable(classId, mode, days, System.currentTimeMillis())
    }

    private fun parseSlot(h: JsonObject, date: String, today: LocalDate): Slot? {
        val atoms = (h["Atoms"] as? JsonArray).orEmpty().map { it.jsonObject }
        if (atoms.isEmpty()) return null // empty period = gap
        val begin = h.int("BeginTimeValue") ?: return null
        val end = h.int("EndTimeValue") ?: return null
        val lessons = atoms.map { a ->
            val subject = a.str("SubjectText")
            val info = a.str("ChangeInfo")
            val (kind, note) = classify(info, a.bool("HasChanged"), date, today)
            val removed = a.str("Type") == "removed" || subject.isEmpty() || kind.removesLesson
            Lesson(
                subject = subject,
                abbrev = a.str("SubjectAbbrev"),
                teacher = a.str("Teacher"),
                teacherFull = a.str("TeacherFullname"),
                room = a.str("Room"),
                group = a.str("GroupsNames"),
                theme = a.str("Theme"),
                change = a.str("ChangeInfo"),
                changed = a.bool("HasChanged") || a.str("ChangeInfo").isNotEmpty(),
                removed = removed,
                kind = if (removed && !kind.removesLesson) ChangeKind.Cancelled else kind,
                note = note,
            )
        }
        return Slot(caption = captionFor(begin), start = begin, end = end, lessons = lessons)
    }

    // ------------------------------------------------------------ change labels

    private val dateTime = Regex("""(\d{1,2}\.\s*\d{1,2}\.)\s*,?\s*(\d{1,2}):(\d{2})""")

    /** "4.11., 11:35" → "11:35" when it's the same day, else "2026-11-04 11:35" (the UI names the weekday). */
    private fun whenText(info: String, lessonDate: String, today: LocalDate): String {
        val m = dateTime.find(info) ?: return ""
        val time = "${m.groupValues[2].toInt()}:${m.groupValues[3]}"
        val d = resolveDate(m.groupValues[1], today)
        return if (d.isEmpty() || d == lessonDate) time else "$d $time"
    }

    /** Teacher surname from "Suplování: ANJ, Foukalová Alena, V311 (…)" → "Foukalová". */
    private fun subTeacher(info: String): String =
        info.substringAfter(':').split(',').getOrNull(1)?.trim()?.substringBefore(' ').orEmpty()

    fun classify(info: String, hasChanged: Boolean, lessonDate: String, today: LocalDate): Pair<ChangeKind, String> {
        val i = info.trim()
        return when {
            i.isEmpty() -> (if (hasChanged) ChangeKind.Other else ChangeKind.None) to ""
            i.startsWith("Přesun na", true) -> ChangeKind.MovedAway to whenText(i, lessonDate, today)
            i.startsWith("Přesun z", true) -> ChangeKind.MovedHere to whenText(i, lessonDate, today)
            i.startsWith("Výměna", true) -> ChangeKind.Swapped to whenText(i, lessonDate, today)
            i.startsWith("Zrušeno", true) -> ChangeKind.Cancelled to ""
            i.startsWith("Vyjmuto", true) -> ChangeKind.Off to ""
            i.startsWith("Suplování", true) -> ChangeKind.Sub to subTeacher(i).let { if (it.isEmpty()) "" else it }
            i.startsWith("Spojeno", true) -> ChangeKind.Merged to (Regex("""Spojeno s ([^:]+)""").find(i)?.groupValues?.get(1)?.trim()
                ?.replace(Regex("""\(([^)]*)\)"""), " ($1)") ?: "")
            i.startsWith("Změna místnosti", true) -> ChangeKind.Room to (Regex("""Změna místnosti:\s*(\S+)\s*\(([^)]+)\)""").find(i)
                ?.let { "${it.groupValues[2]} → ${it.groupValues[1]}" } ?: "")
            else -> ChangeKind.Other to i
        }
    }

    /** School period numbers (0 = 7:00, 1 = 8:00, …). */
    private val periodStarts = listOf(420, 480, 530, 590, 645, 695, 745, 795, 845, 895, 945)
    private fun captionFor(begin: Int): String {
        val i = periodStarts.indexOfFirst { kotlin.math.abs(it - begin) <= 5 }
        return if (i >= 0) i.toString() else ""
    }

    /** "2.11." → 2026-11-02, picking the year that puts the date nearest to today. */
    private fun resolveDate(text: String, today: LocalDate): String {
        val m = Regex("""(\d{1,2})\.\s*(\d{1,2})\.""").find(text) ?: return ""
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val candidates = (today.year - 1..today.year + 1).mapNotNull {
            runCatching { LocalDate.of(it, mo, d) }.getOrNull()
        }
        return candidates.minByOrNull { kotlin.math.abs(it.toEpochDay() - today.toEpochDay()) }?.toString() ?: ""
    }

    private fun weekdayOf(abbrev: String, fallback: Int) = when (abbrev.lowercase()) {
        "po" -> 0; "út", "ut" -> 1; "st" -> 2; "čt", "ct" -> 3; "pá", "pa" -> 4
        else -> fallback
    }
}

private fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim().orEmpty()

private fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.content == "true"

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
