package app.schoolday.logic

import app.schoolday.data.LunchDay
import app.schoolday.data.LunchMenu
import app.schoolday.data.LunchSource
import app.schoolday.data.MealOption
import app.schoolday.data.Slot
import app.schoolday.data.Timetable
import app.schoolday.data.TimetableDay
import app.schoolday.data.minutesNow
import kotlinx.datetime.LocalDate

/** Minutes since midnight → "12:20". */
fun Int.hm(): String = "${this / 60}:${(this % 60).toString().padStart(2, '0')}"

fun nowMinutes(): Int = minutesNow()

/** A free stretch between two real lessons. */
data class Gap(val start: Int, val end: Int, val kind: Kind) {
    enum class Kind { Short, Lunch, Free }
    val minutes get() = end - start
}

/** Bakaláři period start times: period 0 = 7:00, 1 = 8:00, … */
val PERIOD_STARTS = listOf(420, 480, 530, 590, 645, 695, 745, 795, 845, 895, 945)

/** Periods that start inside [start, end) – e.g. lunch gap 11:30–12:25 → [5]. */
fun periodsIn(start: Int, end: Int): List<Int> = PERIOD_STARTS.indices.filter { PERIOD_STARTS[it] in start until end }

/** Czech-style period label ("hodina"): [5] → "5h", [5, 6] → "5–6h", [] → null */
fun periodText(p: List<Int>): String? = when {
    p.isEmpty() -> null
    p.size == 1 -> "${p[0]}h"
    else -> "${p.first()}–${p.last()}h"
}

/** Period number of the last real lesson (for "ends after period 8"). */
fun TimetableDay.lastPeriod(): Int? = realSlots().lastOrNull()?.caption?.toIntOrNull()

/** Lunch can be in period 4 (10:45) at the earliest; a missing period gives a gap of at least 40 minutes. */
const val LUNCH_EARLIEST_PERIOD = 4
const val LUNCH_MIN_GAP = 40

/** Lunch notification timing when lunch is the last thing of the day. */
const val AFTER_SCHOOL_LEAD = 10
const val AFTER_SCHOOL_TAIL = 5

fun TimetableDay.realSlots(): List<Slot> = slots.filter { it.isReal }.sortedBy { it.start }

fun TimetableDay.gaps(): List<Gap> {
    val real = realSlots()
    val raw = real.zipWithNext().map { (a, b) -> b.start - a.end to (a.end to b.start) }.filter { it.first > 0 }
    val firstLunchIdx = raw.indexOfFirst { (len, se) ->
        len >= LUNCH_MIN_GAP && se.second > PERIOD_STARTS[LUNCH_EARLIEST_PERIOD] &&
            periodsIn(se.first, se.second).let { p -> p.isEmpty() || p.last() >= LUNCH_EARLIEST_PERIOD }
    }
    return raw.mapIndexed { i, (len, se) ->
        val kind = when {
            i == firstLunchIdx -> Gap.Kind.Lunch
            len >= LUNCH_MIN_GAP -> Gap.Kind.Free
            else -> Gap.Kind.Short
        }
        Gap(se.first, se.second, kind)
    }
}

/** When to show the lunch notification for this day (start, end, isAfterSchool). */
data class LunchWindow(val start: Int, val end: Int, val afterSchool: Boolean)

fun TimetableDay.lunchWindow(): LunchWindow? {
    if (dayOff) return null
    val real = realSlots()
    if (real.isEmpty()) return null
    gaps().firstOrNull { it.kind == Gap.Kind.Lunch }?.let { return LunchWindow(it.start, it.end, false) }
    val last = real.last().end
    return LunchWindow(last - AFTER_SCHOOL_LEAD, last + AFTER_SCHOOL_TAIL, true)
}

fun TimetableDay.endTime(): Int? = realSlots().lastOrNull()?.end
fun TimetableDay.startTime(): Int? = realSlots().firstOrNull()?.start

fun Timetable.day(date: LocalDate): TimetableDay? = days.firstOrNull { it.date == date.toString() }

/** Short human status for a class right now. */
sealed interface ClassStatus {
    data object NoSchool : ClassStatus
    data class BeforeSchool(val startsAt: Int) : ClassStatus
    data class InLesson(val slot: Slot, val until: Int) : ClassStatus
    data class OnBreak(val gap: Gap) : ClassStatus
    data class Finished(val at: Int) : ClassStatus
}

fun TimetableDay?.status(now: Int): ClassStatus {
    if (this == null || dayOff) return ClassStatus.NoSchool
    val real = realSlots()
    if (real.isEmpty()) return ClassStatus.NoSchool
    if (now < real.first().start) return ClassStatus.BeforeSchool(real.first().start)
    if (now >= real.last().end) return ClassStatus.Finished(real.last().end)
    real.firstOrNull { now >= it.start && now < it.end }?.let { return ClassStatus.InLesson(it, it.end) }
    val gap = gaps().firstOrNull { now >= it.start && now < it.end }
        ?: Gap(now, real.first { it.start > now }.start, Gap.Kind.Short)
    return ClassStatus.OnBreak(gap)
}

// ------------------------------------------------------------------ lunch helpers

fun LunchMenu.day(date: LocalDate): LunchDay? = days.firstOrNull { it.date == date.toString() }

/**
 * The meal to show for a day: what's ordered in iCanteen, or lunch 1 when we only have the website.
 * Returns null when iCanteen says nothing is ordered.
 */
fun LunchMenu.myMeal(day: LunchDay): Pair<MealOption, Boolean>? = when (source) {
    LunchSource.ICanteen -> day.ordered?.let { it to true }
    LunchSource.Website -> day.options.firstOrNull { it.number == 1 }?.let { it to false }
}

/** "Mexické fazole (bezmasé), chléb, ovoce, nápoje" → "Mexické fazole (bezmasé), chléb" */
fun shortDish(name: String): String {
    val noise = setOf("ovoce", "nápoje", "nápoj", "čaj", "šťáva", "voda")
    val parts = name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val kept = parts.filterIndexed { i, p -> i == 0 || p.lowercase() !in noise }
    return kept.joinToString(", ").replace(Regex("""\(\s+"""), "(").replace(Regex("""\s+\)"""), ")")
}
