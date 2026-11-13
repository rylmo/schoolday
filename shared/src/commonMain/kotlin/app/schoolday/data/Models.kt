package app.schoolday.data

import kotlinx.serialization.Serializable

/** One class/group lesson inside a timetable slot. Times are minutes since midnight. */
@Serializable
data class Lesson(
    val subject: String,
    val abbrev: String,
    val teacher: String,
    val teacherFull: String,
    val room: String,
    val group: String,
    val theme: String,
    val change: String,
    val changed: Boolean,
    val removed: Boolean,
    /** What kind of change Bakaláři reports (moved, cancelled, sub, …). */
    val kind: ChangeKind = ChangeKind.None,
    /** Short English description of the change, e.g. "from 13:15" or "J405 → V413". */
    val note: String = "",
)

/**
 * Bakaláři change labels: Přesun na / Přesun z / Zrušeno / Vyjmuto z rozvrhu /
 * Suplování / Spojeno / Výměna / Změna místnosti.
 */
@Serializable
enum class ChangeKind(val label: String, val removesLesson: Boolean) {
    None("", false),
    MovedHere("Moved here", false),
    Swapped("Swapped", false),
    Sub("Sub", false),
    Merged("Merged", false),
    Room("Room", false),
    Other("Changed", false),
    MovedAway("Moved", true),
    Cancelled("Cancelled", true),
    Off("Off", true),
}

/** A lesson period (e.g. "3", 9:50–10:35). Several lessons when the class is split into groups. */
@Serializable
data class Slot(
    val caption: String,
    val start: Int,
    val end: Int,
    val lessons: List<Lesson>,
) {
    /** True if at least one group actually has a lesson (not cancelled/moved away). */
    val isReal: Boolean get() = lessons.any { !it.removed }
}

@Serializable
data class TimetableDay(
    /** ISO date (yyyy-MM-dd), empty for the permanent timetable. */
    val date: String,
    /** 0 = Monday … 4 = Friday */
    val weekday: Int,
    val dayOff: Boolean,
    val dayOffName: String,
    val slots: List<Slot>,
)

@Serializable
data class Timetable(
    val classId: String,
    val mode: TimetableMode,
    val days: List<TimetableDay>,
    val fetchedAt: Long,
)

@Serializable
enum class TimetableMode(val path: String, val label: String) {
    Actual("Actual", "This week"),
    Permanent("Permanent", "Permanent"),
    Next("Next", "Next week"),
}

@Serializable
data class SchoolClass(val id: String, val name: String)

@Serializable
data class MealOption(
    /** 1 for "Oběd 1", 2 for "Oběd 2" … */
    val number: Int,
    val name: String,
    val allergens: String = "",
    val ordered: Boolean = false,
    /** iCanteen action that orders / switches to this meal (null = not possible). */
    val orderUrl: String? = null,
    /** iCanteen action that cancels this meal (only on the ordered one). */
    val cancelUrl: String? = null,
    /** iCanteen's meal ID (stable across sessions, unlike the order links). */
    val id: String = "",
)

@Serializable
data class LunchDay(
    val date: String,
    val soup: String?,
    val options: List<MealOption>,
    /** The dormitory's breakfast and dinner (school website, view only). */
    val breakfast: String? = null,
    val dinner: String? = null,
) {
    val ordered: MealOption? get() = options.firstOrNull { it.ordered }
}

@Serializable
enum class LunchSource { ICanteen, Website }

@Serializable
data class LunchMenu(
    val source: LunchSource,
    val days: List<LunchDay>,
    val fetchedAt: Long,
    /** Why iCanteen wasn't used, if we fell back to the website. */
    val fallbackReason: String? = null,
)
