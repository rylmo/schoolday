package app.schoolday.data

/**
 * One split in your class: groups that have lessons at the same time, like ASW1 / ASW2, or
 * Nj / Šj where each group has its own subject. [key] is the group codes, sorted, joined by "|".
 */
data class GroupChoice(
    val key: String,
    val title: String,
    /** True when every group has the same subject (ASW1 / ASW2), false for Nj / Šj or P / G. */
    val sameSubject: Boolean,
    val options: List<GroupOption>,
)

/** [detail] is the teacher's surname, or the group's subjects when the groups differ by subject. */
data class GroupOption(val code: String, val detail: String)

/**
 * "Your groups": which group you're in for each split subject of your own class, so it shows
 * one lesson per slot instead of every group's.
 */
object Groups {
    /** Praxe's teachers rotate every few months, so it isn't offered and always shows every group. */
    private fun rotates(l: Lesson) = l.abbrev.equals("PRX", ignoreCase = true) || l.subject.startsWith("Praxe", ignoreCase = true)

    /** "Aj1" → [Aj1]; a lesson shared by several groups lists them with commas. */
    fun codes(group: String): List<String> = group.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** The split [slot] belongs to (its key, as in [choices]), or null when it isn't a choosable split. */
    fun keyOf(slot: Slot): String? {
        val live = slot.lessons.filter { !it.removed && it.group.isNotBlank() }
        val codes = live.flatMap { codes(it.group) }.distinct()
        if (codes.size < 2 || live.any(::rotates)) return null
        return codes.sorted().joinToString("|")
    }

    /** The splits in [tt], in the order they first come up in the week. */
    fun choices(tt: Timetable): List<GroupChoice> {
        val found = LinkedHashMap<String, LinkedHashMap<String, MutableList<Lesson>>>()
        for (day in tt.days) for (slot in day.slots) {
            val live = slot.lessons.filter { !it.removed && it.group.isNotBlank() }
            val codes = live.flatMap { codes(it.group) }.distinct()
            if (codes.size < 2 || live.any(::rotates)) continue
            val byCode = found.getOrPut(codes.sorted().joinToString("|")) { LinkedHashMap() }
            live.forEach { l -> codes(l.group).forEach { byCode.getOrPut(it) { mutableListOf() }.add(l) } }
        }
        return found.map { (key, byCode) ->
            val same = byCode.values.flatten().map { it.subject }.distinct().size == 1
            val groups = byCode.entries.sortedBy { it.key.lowercase() }
            val options = groups.map { (code, lessons) ->
                GroupOption(code, if (same) surname(lessons) else subjects(lessons))
            }
            val title = when {
                same -> byCode.values.first().first().subject
                // One subject per group: "Německý jazyk / Španělský jazyk"; several: "M3D, PGR / MSA, OOP".
                else -> groups.joinToString(" / ") { subjects(it.value) }
            }
            GroupChoice(key, title, same, options)
        }
    }

    private fun subjects(lessons: List<Lesson>): String {
        val names = lessons.map { it.subject }.distinct()
        return if (names.size == 1) names.first() else lessons.map { it.abbrev.ifBlank { it.subject } }.distinct().joinToString(", ")
    }

    /** The teacher who usually takes the group, by surname ("Mgr. Václav Knápek" → "Knápek"). */
    private fun surname(lessons: List<Lesson>): String {
        val name = lessons.map { it.teacherFull.ifBlank { it.teacher } }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return ""
        return name.split(' ').last { it.isNotBlank() }
    }

    /** Group codes to hide: the other groups of every split where you picked one. */
    fun hidden(picks: Map<String, String>): Set<String> =
        picks.flatMap { (key, pick) -> key.split('|').filter { it != pick } }.toSet()

    /** [tt] without lessons for groups you're not in. Slots left with nothing are dropped. */
    fun filter(tt: Timetable, hidden: Set<String>): Timetable {
        if (hidden.isEmpty()) return tt
        return tt.copy(days = tt.days.map { day ->
            day.copy(slots = day.slots.mapNotNull { slot ->
                val kept = slot.lessons.filter { l -> codes(l.group).let { c -> c.isEmpty() || c.any { it !in hidden } } }
                if (kept.isEmpty()) null else slot.copy(lessons = kept)
            })
        })
    }
}
