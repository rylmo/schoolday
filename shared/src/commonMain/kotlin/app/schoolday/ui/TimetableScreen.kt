package app.schoolday.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import app.schoolday.data.ChangeKind
import app.schoolday.data.Lesson
import app.schoolday.data.Slot
import app.schoolday.data.TimetableDay
import app.schoolday.data.TimetableMode
import app.schoolday.logic.Gap
import app.schoolday.logic.endTime
import app.schoolday.logic.gaps
import app.schoolday.logic.hm
import app.schoolday.logic.nowMinutes
import app.schoolday.logic.periodText
import app.schoolday.logic.periodsIn
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type
import kotlinx.coroutines.launch
import app.schoolday.data.todayDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlin.math.absoluteValue


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    vm: AppViewModel,
    classId: String,
    onClassChange: (String) -> Unit,
    onSettings: () -> Unit,
    openDate: String? = null,
    onOpened: () -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val tts by vm.timetables.collectAsState()
    val today = todayDate()
    val todayIdx = today.dayOfWeek.isoDayNumber - 1
    val weekend = todayIdx > 4
    var mode by rememberSaveable { mutableStateOf(if (weekend) TimetableMode.Next else TimetableMode.Actual) }
    var weekView by rememberSaveable { mutableStateOf(false) }
    var dayIdx by rememberSaveable { mutableIntStateOf(if (weekend) 0 else todayIdx) }
    var detail by remember { mutableStateOf<Slot?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val tabs = vm.timetableClasses(settings, classId)

    LaunchedEffect(classId, mode) { vm.loadTimetable(classId, mode) }

    // Opened from the iOS widget: show the week and day it was showing.
    LaunchedEffect(openDate) {
        val date = openDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@LaunchedEffect
        val monday = today.minus(todayIdx, DateTimeUnit.DAY)
        mode = if (monday.daysUntil(date) >= 7) TimetableMode.Next else TimetableMode.Actual
        dayIdx = (date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 4)
        weekView = false
        onOpened()
    }

    // iOS: the week/day switch and the menu are native Liquid Glass buttons above the screen.
    val chrome = LocalNativeChrome.current
    if (chrome != null) {
        val permanent = mode == TimetableMode.Permanent
        LaunchedEffect(weekView, permanent) { chrome.publishTimetable(weekView, permanent) }
        DisposableEffect(chrome) {
            val actions = TimetableActions(
                toggleWeekView = { weekView = !weekView },
                togglePermanent = { mode = if (mode == TimetableMode.Permanent) TimetableMode.Actual else TimetableMode.Permanent },
            )
            chrome.timetable = actions
            onDispose { if (chrome.timetable === actions) chrome.timetable = null }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // class tabs with a sliding underline
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            ClassTabs(tabs, classId, { vm.className(it) }, onClassChange, Modifier.weight(1f))
            if (chrome == null) {
                IconBtn(if (weekView) Ic.Day else Ic.Week, if (weekView) S.dayView else S.weekView, { weekView = !weekView })
                IconBtn(Ic.More, S.more, { menuOpen = true })
            } else {
                Spacer(Modifier.size(width = 112.dp, height = 44.dp))
            }
        }

        // week switcher
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Prev, S.prevWeek, { mode = TimetableMode.Actual }, enabled = mode == TimetableMode.Next)
            AnimatedContent(mode, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "week") { m ->
                val range = tts[TtKey(classId, m)]?.data?.days?.mapNotNull { it.date.takeIf { d -> d.isNotEmpty() }?.let { s -> LocalDate.parse(s) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(m.text(), color = T.text2)
                    if (!range.isNullOrEmpty()) {
                        Text("  ${range.first().day}. – ${range.last().day}. ${range.last().month.number}.", style = Type.small, color = T.text3)
                    }
                }
            }
            IconBtn(Ic.Next, S.nextWeek, { mode = TimetableMode.Next }, enabled = mode == TimetableMode.Actual)
        }

        val offline = tts[TtKey(classId, mode)]?.let { if (it.data != null) it.error else null }
        AnimatedVisibility(offline != null) { ErrorNote(S.offlineTimetable, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }

        AnimatedContent(
            targetState = Triple(classId, mode, weekView), label = "content",
            transitionSpec = { fadeIn(tween(220, 40)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize(),
        ) { (cls, m, week) ->
            val s = tts[TtKey(cls, m)]
            val data = s?.data
            ForkRefresh(s?.loading == true, onRefresh = { vm.loadTimetable(cls, m, force = true) }, modifier = Modifier.fillMaxSize()) {
                when {
                    data == null -> Message(s?.error ?: S.loading)
                    week -> WeekList(data.days, m == TimetableMode.Actual, today) { dayIdx = it; weekView = false }
                    else -> DayPager(data.days, dayIdx, { dayIdx = it }, m == TimetableMode.Actual, todayIdx) { detail = it }
                }
            }
        }
    }

    if (menuOpen) {
        PickerSheet(
            title = S.timetable,
            items = listOf("permanent", "settings"),
            selected = { it == "permanent" && mode == TimetableMode.Permanent },
            label = { if (it == "permanent") S.permanentTimetable else S.settings },
            onPick = {
                menuOpen = false
                if (it == "permanent") mode = if (mode == TimetableMode.Permanent) TimetableMode.Actual else TimetableMode.Permanent
                else onSettings()
            },
            onDismiss = { menuOpen = false },
        )
    }

    detail?.let { slot ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = T.raised, contentColor = T.text, dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(T.lineStrong))
        }) { SlotDetail(slot) }
    }
}

@Composable
private fun ClassTabs(ids: List<String>, selected: String, name: (String) -> String, onSelect: (String) -> Unit, modifier: Modifier) {
    val positions = remember { mutableStateMapOf<String, Pair<Dp, Dp>>() }
    val density = LocalDensity.current
    val target = positions[selected]
    val x by animateDpAsState(target?.first ?: 0.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "tabX")
    val w by animateDpAsState(target?.second ?: 0.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "tabW")
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ids.forEach { id ->
                val active = id == selected
                val color by animateColorAsState(if (active) T.text else T.text3, label = "tabColor")
                Text(
                    name(id), style = Type.tab, color = color,
                    modifier = Modifier
                        .onGloballyPositioned { c -> with(density) { positions[id] = c.positionInParent().x.toDp() to c.size.width.toDp() } }
                        .pressable { onSelect(id) }
                        .padding(bottom = 8.dp),
                )
            }
        }
        if (target != null) Box(Modifier.offset(x = x, y = 38.dp).width(w).height(2.dp).background(T.accent))
    }
}

@Composable
private fun Message(text: String) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text(text, Modifier.fillMaxWidth().padding(32.dp), color = T.text2, style = Type.body) }
    }
}

// ------------------------------------------------------------------ day pager

@Composable
private fun DayPager(
    days: List<TimetableDay>, dayIdx: Int, onDayChange: (Int) -> Unit,
    showToday: Boolean, todayIdx: Int, onSlot: (Slot) -> Unit,
) {
    val pager = rememberPagerState(initialPage = dayIdx.coerceIn(0, 4)) { 5 }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect(onDayChange) }
    // Follow a day chosen from outside (the iOS widget), but never fight a swipe in progress.
    LaunchedEffect(dayIdx) {
        if (pager.currentPage != dayIdx && !pager.isScrollInProgress) pager.scrollToPage(dayIdx.coerceIn(0, 4))
    }

    Column(Modifier.fillMaxSize()) {
        DayPicker(days, pager, showToday, todayIdx) { scope.launch { pager.animateScrollToPage(it) } }
        Hairline()
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            // slight depth while swiping
            Box(Modifier.graphicsLayer {
                val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val s = 1f - 0.05f * off
                scaleX = s; scaleY = s; alpha = 1f - 0.35f * off
            }) {
                DayRail(days.firstOrNull { it.weekday == page }, showToday && page == todayIdx, onSlot)
            }
        }
    }
}

@Composable
private fun DayPicker(days: List<TimetableDay>, pager: PagerState, showToday: Boolean, todayIdx: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        S.weekdays.forEachIndexed { i, label ->
            val date = days.firstOrNull { it.weekday == i }?.date?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
            val sel = i == pager.targetPage
            val color by animateColorAsState(if (sel) T.text else T.text3, label = "dayColor")
            Column(
                Modifier.weight(1f).pressable { onSelect(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, style = Type.tiny.copy(fontSize = Type.small.fontSize * 0.92f), color = color)
                Text(date?.day?.toString() ?: "·", style = Type.title.copy(fontSize = Type.heading.fontSize * 1.18f, fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium), color = color)
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (showToday && i == todayIdx) T.accent else Color.Transparent))
            }
        }
    }
}

private sealed interface Row_ {
    val time: Int
    data class Lesson_(val slot: Slot) : Row_ { override val time get() = slot.start }
    data class Gap_(val gap: Gap) : Row_ { override val time get() = gap.start }
    data class Now(override val time: Int) : Row_
    data class After(override val time: Int) : Row_
}

private fun rowsFor(day: TimetableDay, now: Int?): List<Row_> {
    val rows = mutableListOf<Row_>()
    rows += day.slots.map { Row_.Lesson_(it) }
    rows += day.gaps().filter { it.kind != Gap.Kind.Short }.map { Row_.Gap_(it) }
    if (day.gaps().none { it.kind == Gap.Kind.Lunch }) day.endTime()?.let { rows += Row_.After(it) }
    val sorted = rows.sortedWith(compareBy<Row_> { it.time }.thenBy { if (it is Row_.Gap_) 0 else 1 }).toMutableList()
    if (now != null) {
        val first = day.slots.firstOrNull()?.start; val last = day.endTime()
        if (first != null && last != null && now in first until last) {
            // insert after the last row that started before now
            val idx = sorted.indexOfLast { it.time <= now }.let { if (it < 0) 0 else it + 1 }
            sorted.add(idx.coerceAtMost(sorted.size), Row_.Now(now))
        }
    }
    return sorted
}

@Composable
private fun DayRail(day: TimetableDay?, isToday: Boolean, onSlot: (Slot) -> Unit) {
    if (day == null || day.slots.none { it.isReal }) {
        Message(if (day?.dayOff == true) day.dayOffName.ifEmpty { S.dayOff } else S.noLessons)
        return
    }
    val now = if (isToday) nowMinutes() else null
    Box(Modifier.fillMaxSize()) {
        // the rail line
        Box(Modifier.padding(start = 20.dp + 56.dp + 4.dp).width(1.dp).fillMaxHeight().background(T.lineStrong))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalBottomInset.current)) {
            items(rowsFor(day, now), key = { r -> "${r::class.simpleName}-${r.time}" }) { r ->
                Box(Modifier.animateItem()) {
                    when (r) {
                        is Row_.Lesson_ -> LessonRow(r.slot, isNow = now != null && now in r.slot.start until r.slot.end) { onSlot(r.slot) }
                        is Row_.Gap_ -> Band(
                            if (r.gap.kind == Gap.Kind.Lunch) S.statusLunch else S.free,
                            periodText(periodsIn(r.gap.start, r.gap.end)), "${r.gap.start.hm()} – ${r.gap.end.hm()}",
                            lunch = r.gap.kind == Gap.Kind.Lunch,
                        )
                        is Row_.After -> Band(S.lunchAfterSchool, null, S.fromLower(r.time.hm()), lunch = true)
                        is Row_.Now -> NowLine(r.time)
                    }
                }
            }
        }
    }
}

@Composable
private fun NowLine(time: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            time.hm(), style = Type.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = T.bg,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(T.now).padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Box(Modifier.weight(1f).height(1.5.dp).background(T.now))
    }
}

@Composable
private fun Band(title: String, period: String?, time: String, lunch: Boolean) {
    val fg = if (lunch) T.accent else T.text2
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(if (lunch) T.accentSoft else T.raised)
            .border(width = 0.dp, color = Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.Lunch, null, Modifier.size(18.dp), tint = fg)
        Text(title + (period?.let { " · $it" } ?: ""), style = Type.bodyStrong.copy(fontSize = Type.body.fontSize), color = fg, modifier = Modifier.weight(1f))
        Text(time, style = Type.small.copy(fontSize = Type.body.fontSize * 0.93f), color = fg)
    }
}

/** One lesson period; groups are merged into one line, details live in the sheet. */
@Composable
private fun LessonRow(slot: Slot, isNow: Boolean, onClick: () -> Unit) {
    val live = slot.lessons.filter { !it.removed }
    val gone = slot.lessons.filter { it.removed }
    val allGone = live.isEmpty()
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp)) {
        Column(Modifier.width(56.dp)) {
            Text(slot.start.hm(), style = Type.time, color = if (isNow) T.now else if (allGone) T.text3 else T.text)
            Text(
                (if (slot.caption.isNotEmpty()) "${slot.caption}h · " else "") + slot.end.hm(),
                style = Type.tiny, color = T.text3,
            )
        }
        Box(Modifier.width(14.dp).padding(top = 6.dp)) {
            Box(
                Modifier.offset(x = (-1).dp).size(9.dp).clip(CircleShape)
                    .background(if (isNow) T.now else if (allGone) T.bg else T.lineStrong.copy(alpha = 1f))
                    .border(if (allGone) 1.5.dp else 0.dp, T.lineStrong, CircleShape),
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (allGone) {
                RemovedLine(gone)
            } else {
                val title = mergedTitle(live)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = Type.bodyStrong, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (live.size > 1) Text(S.groups(live.size), style = Type.tiny.copy(fontSize = Type.small.fontSize * 0.92f), color = T.text3)
                }
                Text(mergedMeta(live), style = Type.small, color = T.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val tags = live.filter { it.kind != ChangeKind.None }.distinctBy { it.kind }
                if (tags.isNotEmpty() || gone.isNotEmpty()) {
                    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { ChangeTag(it) }
                        gone.distinctBy { it.kind }.forEach { ChangeTag(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemovedLine(gone: List<Lesson>) {
    val l = gone.first()
    val color = when (l.kind) { ChangeKind.Cancelled -> T.removed; else -> T.text3 }
    val struck = l.kind != ChangeKind.MovedAway
    val abbrev = Regex("""\(([^,)]+)""").find(l.change)?.groupValues?.get(1)?.trim().orEmpty()
    Text(
        abbrev.ifEmpty { l.kind.text() }, style = Type.bodyStrong,
        color = color, textDecoration = if (struck) TextDecoration.LineThrough else null,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { gone.distinctBy { it.kind }.forEach { ChangeTag(it) } }
}

@Composable
fun ChangeTag(l: Lesson) {
    val (fg, bg) = when (l.kind) {
        ChangeKind.Cancelled -> T.removed to T.removedSoft
        ChangeKind.MovedHere, ChangeKind.Swapped, ChangeKind.MovedAway -> T.now to T.nowSoft
        ChangeKind.Sub, ChangeKind.Room -> T.changed to T.changedSoft
        else -> T.text2 to T.raised
    }
    val text = when (l.kind) {
        ChangeKind.None -> return
        ChangeKind.MovedAway -> S.chMoved + (whenLabel(l.note).takeIf { it.isNotEmpty() }?.let { " → $it" } ?: "")
        ChangeKind.MovedHere, ChangeKind.Swapped -> l.kind.text() + (whenLabel(l.note).takeIf { it.isNotEmpty() }?.let { " · " + S.fromWhen(it) } ?: "")
        ChangeKind.Merged -> l.kind.text() + (l.note.takeIf { it.isNotEmpty() }?.let { " · " + S.withClass(it) } ?: "")
        ChangeKind.Sub, ChangeKind.Room -> l.kind.text() + (l.note.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
        else -> l.kind.text()
    }
    Tag(text, fg, bg)
}

private fun mergedTitle(live: List<Lesson>): String {
    val subjects = live.map { it.subject.ifEmpty { it.abbrev } }.distinct()
    if (subjects.size == 1) return subjects[0]
    val full = subjects.joinToString(" / ")
    return if (full.length <= 26) full else live.map { it.abbrev.ifEmpty { it.subject } }.distinct().joinToString(" / ")
}

private fun mergedMeta(live: List<Lesson>): String =
    if (live.size == 1) listOf(live[0].room, surname(live[0].teacherFull, live[0].teacher)).filter { it.isNotEmpty() }.joinToString(" · ")
    else live.map { it.room }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")

// ------------------------------------------------------------------ week view

@Composable
private fun WeekList(days: List<TimetableDay>, showToday: Boolean, today: LocalDate, onDay: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalBottomInset.current)) {
        items(days.sortedBy { it.weekday }, key = { it.weekday }) { day ->
            val date = day.date.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
            val isToday = showToday && date == today
            Column(Modifier.fillMaxWidth().pressable { onDay(day.weekday) }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        S.weekdayFull(day.weekday),
                        style = Type.heading, color = if (isToday) T.accent else T.text, modifier = Modifier.weight(1f),
                    )
                    day.endTime()?.let { Text(S.untilLower(it.hm()), style = Type.small, color = T.text3) }
                }
                Spacer(Modifier.height(8.dp))
                if (day.dayOff || day.slots.none { it.isReal }) {
                    Text(day.dayOffName.ifEmpty { S.noLessons }, color = T.text3, style = Type.small)
                } else {
                    val lunch = day.gaps().firstOrNull { it.kind == Gap.Kind.Lunch }
                    day.slots.forEach { s ->
                        CompactSlot(s)
                        if (lunch != null && s.end == lunch.start) {
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(lunch.start.hm(), style = Type.small, color = T.accent, modifier = Modifier.width(48.dp))
                                Text(S.statusLunch + (periodText(periodsIn(lunch.start, lunch.end))?.let { " · $it" } ?: ""), style = Type.small, color = T.accent)
                            }
                        }
                    }
                }
            }
            Hairline()
        }
    }
}

@Composable
private fun CompactSlot(slot: Slot) {
    val live = slot.lessons.filter { !it.removed }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(slot.start.hm(), style = Type.small, color = T.text3, modifier = Modifier.width(48.dp))
        if (live.isEmpty()) {
            val k = slot.lessons.first().kind
            Text(
                if (k == ChangeKind.MovedAway) S.movedLower(whenLabel(slot.lessons.first().note)) else k.text().lowercase().ifEmpty { S.cancelledLower },
                style = Type.small, color = if (k == ChangeKind.Cancelled) T.removed else T.text3,
                textDecoration = if (k == ChangeKind.MovedAway) null else TextDecoration.LineThrough,
            )
        } else {
            val changed = live.any { it.kind != ChangeKind.None }
            Text(
                live.map { it.abbrev.ifEmpty { it.subject } }.distinct().joinToString(" / "),
                style = Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = if (changed) T.changed else T.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ detail sheet

@Composable
private fun SlotDetail(slot: Slot) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "${slot.start.hm()} – ${slot.end.hm()}" + if (slot.caption.isNotEmpty()) "  ·  ${slot.caption}h" else "",
            style = Type.small, color = T.text2,
        )
        slot.lessons.forEachIndexed { i, l ->
            if (i > 0) Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (l.removed) (l.subject.ifEmpty { S.lesson }) else l.subject, style = Type.title, color = if (l.removed) T.text3 else T.text)
                if (l.kind != ChangeKind.None) ChangeTag(l)
                Detail(S.group, l.group)
                Detail(S.teacher, l.teacherFull.ifEmpty { l.teacher })
                Detail(S.room, l.room)
                Detail(S.topic, l.theme)
                Detail(S.bakalariNote, l.change)
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Eyebrow(label, color = T.text3)
        Text(value, style = Type.body, color = T.text)
    }
}

fun TimetableMode.text(): String = when (this) {
    TimetableMode.Actual -> S.thisWeek
    TimetableMode.Permanent -> S.permanent
    TimetableMode.Next -> S.nextWeekLabel
}

fun ChangeKind.text(): String = when (this) {
    ChangeKind.None -> ""
    ChangeKind.MovedHere -> S.chMovedHere
    ChangeKind.Swapped -> S.chSwapped
    ChangeKind.Sub -> S.chSub
    ChangeKind.Merged -> S.chMerged
    ChangeKind.Room -> S.chRoom
    ChangeKind.Other -> S.chOther
    ChangeKind.MovedAway -> S.chMoved
    ChangeKind.Cancelled -> S.chCancelled
    ChangeKind.Off -> S.chOff
}

/** Parser note "11:35" or "2026-11-04 11:35" → "11:35" / "Wed 11:35" (in the app's language). */
fun whenLabel(note: String): String {
    val parts = note.trim().split(' ')
    if (parts.size == 2) {
        val d = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return note
        return S.shortDay(d.dayOfWeek) + " " + parts[1]
    }
    // older cached notes were already phrased in English
    return note.removePrefix("→ ").removePrefix("from ")
}
