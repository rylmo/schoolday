package app.schoolday.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import app.schoolday.app
import app.schoolday.data.ChangeKind
import app.schoolday.data.LunchSource
import app.schoolday.data.Slot
import app.schoolday.data.TimetableMode
import app.schoolday.logic.AFTER_SCHOOL_LEAD
import app.schoolday.logic.day
import app.schoolday.logic.endTime
import app.schoolday.logic.hm
import app.schoolday.logic.lunchWindow
import app.schoolday.logic.myMeal
import app.schoolday.logic.nowMinutes
import app.schoolday.logic.shortDish
import app.schoolday.ui.S
import java.time.LocalDate

/** Widget background choices in Settings. Rows are a tint over the background, so they follow its transparency. */
enum class WidgetStyle(
    val id: String,
    val bg: Color,
    val row: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val changed: Color,
    val dayOff: Color,
) {
    Dark("dark", Color(0xFF2B3033), Color(0x47000000), Color(0xFFF4F4F4), Color(0xFFC4C7C9), Color(0xFFF2A65A), Color(0xFFF05487), Color(0xFF2FA8E3)),
    Black("black", Color(0xFF000000), Color(0x1FFFFFFF), Color(0xFFF4F4F4), Color(0xFFBDBDBD), Color(0xFFF2A65A), Color(0xFFF05487), Color(0xFF2FA8E3)),
    Light("light", Color(0xFFF1F1F3), Color(0x12000000), Color(0xFF1A1A1A), Color(0xFF55595C), Color(0xFFC46A12), Color(0xFFD81B60), Color(0xFF0B77B5));

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Dark
    }
}

/** One line of a widget's scrolling list. */
sealed interface WidgetItem {
    data class Header(val text: String) : WidgetItem
    data class Lesson(
        val period: String, val start: String, val end: String, val subject: String,
        val details: List<String>, val changed: Boolean, val cancelled: Boolean,
    ) : WidgetItem
    data class Lunch(val title: String, val dish: String?) : WidgetItem
    /** A day off ([blue]) or a plain message. */
    data class Note(val text: String, val blue: Boolean) : WidgetItem
    /** Breakfast/soup/dinner ([number] null) or a lunch option. */
    data class Meal(val label: String?, val number: Int?, val text: String, val ordered: Boolean) : WidgetItem
}

/** Everything the widgets show, read from the app's offline copies (no network here). */
class WidgetData(
    val className: String,
    val style: WidgetStyle,
    val opacity: Float,
    val timetable: List<WidgetItem>,
    val menu: List<WidgetItem>,
    /** Breakfast and dinner are on, so the menu is called Menu rather than Lunch. */
    val menuTitleAll: Boolean,
) {
    companion object {
        fun load(ctx: Context): WidgetData {
            val app = ctx.app
            val s = app.prefs.value
            val className = app.repo.cachedClasses()?.firstOrNull { it.id == s.myClass }?.name ?: s.myClass
            return WidgetData(
                className = className,
                style = WidgetStyle.of(s.widgetStyle),
                opacity = s.widgetOpacity.coerceIn(0, 100) / 100f,
                timetable = timetable(ctx),
                menu = menu(ctx, s.showAllMeals),
                menuTitleAll = s.showAllMeals,
            )
        }

        private fun dayTitle(date: LocalDate, today: LocalDate): String {
            val name = when (date) {
                today -> S.today
                today.plusDays(1) -> S.tomorrow
                else -> S.dayName(date)
            }
            return "${name.uppercase(S.locale)} (${date.dayOfMonth}. ${date.monthValue}.)"
        }

        /** Today from the current lesson (or the next school day once lessons are over), then the coming days. */
        private fun timetable(ctx: Context): List<WidgetItem> {
            val app = ctx.app
            val s = app.prefs.value
            val days = listOf(TimetableMode.Actual, TimetableMode.Next)
                .mapNotNull { app.repo.myTimetable(it) }
                .flatMap { it.days }
                .filter { it.date.isNotEmpty() }
                .distinctBy { it.date }
                .sortedBy { it.date }
            if (days.isEmpty()) return listOf(WidgetItem.Note(S.widgetNoData, false))
            val menu = app.repo.cachedLunch()
            val today = LocalDate.now()
            val now = nowMinutes()
            val out = mutableListOf<WidgetItem>()
            for (d in days) {
                val date = LocalDate.parse(d.date)
                if (date < today) continue
                if (date == today && !d.dayOff && (d.endTime()?.let { now >= it } != false)) continue // lessons over
                out += WidgetItem.Header(dayTitle(date, today))
                if (d.dayOff) {
                    out += WidgetItem.Note(d.dayOffName.ifBlank { S.dayOff }, true); continue
                }
                val slots = d.slots.filter { it.lessons.isNotEmpty() }.sortedBy { it.start }
                    .filter { date != today || it.end > now }
                if (slots.isEmpty()) {
                    out += WidgetItem.Note(S.noLessons, false); continue
                }
                val w = d.lunchWindow()?.takeIf { date != today || now < it.end }
                val lunch = w?.let {
                    val dish = menu?.day(date)?.let { day -> menu.myMeal(day)?.first?.name }?.let(::shortDish)
                    val title = if (it.afterSchool) S.lunchAfterSchoolFrom((it.start + AFTER_SCHOOL_LEAD).hm()) else S.lunchSpan(it.start.hm(), it.end.hm())
                    WidgetItem.Lunch(title, dish) to (if (it.afterSchool) Int.MAX_VALUE else it.end)
                }
                var lunchAdded = false
                for (slot in slots) {
                    if (lunch != null && !lunchAdded && slot.start >= lunch.second) { out += lunch.first; lunchAdded = true }
                    out += lesson(slot)
                }
                if (lunch != null && !lunchAdded) out += lunch.first
            }
            return out.ifEmpty { listOf(WidgetItem.Note(S.noLessons, false)) }
        }

        private fun lesson(slot: Slot): WidgetItem.Lesson {
            val active = slot.lessons.filter { !it.removed }.ifEmpty { slot.lessons }
            val subject = active.map { it.subject.ifBlank { it.abbrev } }.distinct().joinToString(" / ")
            val details = active.take(3).map { l ->
                listOf(l.group, l.room, l.teacherFull.ifBlank { l.teacher }).filter { it.isNotBlank() }.joinToString(" | ")
            }.filter { it.isNotBlank() }.distinct()
            return WidgetItem.Lesson(
                period = slot.caption, start = slot.start.hm(), end = slot.end.hm(), subject = subject, details = details,
                changed = active.any { it.changed || it.kind != ChangeKind.None },
                cancelled = slot.lessons.all { it.removed },
            )
        }

        private fun menu(ctx: Context, allMeals: Boolean): List<WidgetItem> {
            val menu = ctx.app.repo.cachedLunch() ?: return listOf(WidgetItem.Note(S.menuUnavailable, false))
            val today = LocalDate.now()
            // Days off from your timetable: the canteen lists them as a fake "lunch 1" ("ŘEDITELSKÉ VOLNO - NEVAŘÍ SE").
            val myClass = ctx.app.prefs.value.myClass
            val daysOff = listOf(TimetableMode.Actual, TimetableMode.Next)
                .mapNotNull { ctx.app.repo.cachedTimetable(myClass, it) }
                .flatMap { it.days }.filter { it.dayOff }.associate { it.date to it.dayOffName }
            val out = mutableListOf<WidgetItem>()
            for (d in menu.days.sortedBy { it.date }) {
                val date = LocalDate.parse(d.date)
                if (date < today) continue
                out += WidgetItem.Header(dayTitle(date, today))
                daysOff[d.date]?.let { name ->
                    out += WidgetItem.Note(name.ifBlank { S.dayOff }, true); continue
                }
                if (allMeals) d.breakfast?.let { out += WidgetItem.Meal(S.breakfast, null, shortDish(it), false) }
                d.soup?.let { out += WidgetItem.Meal(S.soup, null, it, false) }
                d.options.forEach { o ->
                    out += WidgetItem.Meal(null, o.number, shortDish(o.name), o.ordered && menu.source == LunchSource.ICanteen)
                }
                if (allMeals) d.dinner?.let { out += WidgetItem.Meal(S.dinner, null, shortDish(it), false) }
            }
            return out.ifEmpty { listOf(WidgetItem.Note(S.menuUnavailable, false)) }
        }
    }
}
