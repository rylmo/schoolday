package app.schoolday.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import app.schoolday.data.LunchMenu
import app.schoolday.data.LunchSource
import app.schoolday.data.Timetable
import app.schoolday.data.TimetableDay
import app.schoolday.data.TimetableMode
import app.schoolday.logic.ClassStatus
import app.schoolday.logic.Gap
import app.schoolday.logic.day
import app.schoolday.logic.endTime
import app.schoolday.logic.gaps
import app.schoolday.logic.hm
import app.schoolday.logic.lunchWindow
import app.schoolday.logic.myMeal
import app.schoolday.logic.nowMinutes
import app.schoolday.logic.periodText
import app.schoolday.logic.periodsIn
import app.schoolday.logic.realSlots
import app.schoolday.logic.shortDish
import app.schoolday.logic.status
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type
import kotlinx.coroutines.delay
import app.schoolday.data.todayDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Day strips cover 8:00–16:00. */
private const val AXIS_START = 8 * 60
private const val AXIS_END = 16 * 60
private val NAME_W = 64.dp

@Composable
fun TodayScreen(
    vm: AppViewModel,
    onOpenLunch: () -> Unit,
    onOpenClass: (String) -> Unit,
    onSettings: () -> Unit,
    intro: IntroState?,
) {
    val settings by vm.settings.collectAsState()
    val tts by vm.timetables.collectAsState()
    val lunch by vm.lunch.collectAsState()
    val now by produceState(nowMinutes()) { while (true) { delay(15_000); value = nowMinutes() } }
    val today = todayDate()
    val refreshing = lunch.loading || tts.values.any { it.loading }
    val mySt = tts[TtKey(settings.myClass, TimetableMode.Actual)]
    val myNext = tts[TtKey(settings.myClass, TimetableMode.Next)]?.data
    val myDay = mySt?.data?.day(today)

    // next week's timetable, for "Monday's lunch" times on Fridays and weekends
    LaunchedEffect(settings.myClass) { vm.loadTimetable(settings.myClass, TimetableMode.Next) }
    // Today loads its own classes instead of relying on the host app's launch/foreground refresh.
    LaunchedEffect(settings.myClass, settings.shownClasses) {
        (settings.shownClasses + settings.myClass).distinct().forEach { vm.loadTimetable(it, TimetableMode.Actual) }
    }

    // Strips draw in left → right, unless the startup animation already morphed into them.
    val draw = remember { Animatable(if (intro?.willPlay == true) 1f else 0f) }
    LaunchedEffect(Unit) { if (draw.value < 1f) draw.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    ForkRefresh(refreshing, onRefresh = { vm.refreshAll(force = true) }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp + LocalBottomInset.current)) {
            // header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow(S.monthDay(today))
                    Text(S.dayName(today), style = Type.display, color = T.text)
                }
                // iOS shows a floating Liquid Glass button here instead.
                if (LocalNativeChrome.current == null) IconBtn(Ic.Settings, S.settings, onSettings, tint = T.text)
                else Spacer(Modifier.size(44.dp))
            }

            LunchBlock(lunch, today, now, mySt?.data != null, myDay, listOfNotNull(mySt?.data, myNext), onOpenLunch, intro)

            // classes
            val rows = buildList {
                add(Triple(S.you, settings.myClass, mySt))
                settings.shownClasses.forEach { add(Triple(vm.className(it), it, tts[TtKey(it, TimetableMode.Actual)])) }
            }
            val days = rows.map { it.third?.data?.day(today) }
            // the now-tick only while someone is still at school
            val stillGoing = days.any { d -> d?.endTime()?.let { now < it } == true && d.realSlots().isNotEmpty() }
            val nowFrac = if (stillGoing && now in AXIS_START until AXIS_END) (now - AXIS_START).toFloat() / (AXIS_END - AXIS_START) else null

            Eyebrow(S.classes, Modifier.padding(start = 20.dp, top = 22.dp))
            Row(Modifier.padding(start = 20.dp + NAME_W, end = 20.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f).height(16.dp)) {
                    val w = maxWidth
                    listOf(8, 10, 12, 14, 16).forEach { h ->
                        val x = w * ((h * 60 - AXIS_START).toFloat() / (AXIS_END - AXIS_START))
                        Text(h.toString(), style = Type.tiny, color = T.text3, modifier = Modifier.offset(x = x - (if (h >= 10) 7.dp else 3.dp)))
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                rows.forEachIndexed { i, (name, id, st) ->
                    ClassRow(
                        name = name, isYou = i == 0, label = if (i == 0) vm.className(id) else null,
                        st = st, myDay = if (i == 0) null else myDay, today = today, now = now, nowFrac = nowFrac,
                        progress = draw.value, onClick = { onOpenClass(id) },
                        onStrip = { r -> if (i < 3) intro?.strips?.set(i, r) },
                        onSegs = { segs -> if (i < 3) intro?.segs?.set(i, segs) },
                    )
                    Hairline()
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            ) {
                LegendItem(T.accent, S.legendLunch)
                LegendItem(T.text, S.legendSame)
            }
            val err = (listOf(lunch.error) + rows.map { it.third?.error }).filterNotNull().firstOrNull()
            ErrorNote(err?.let { S.offlineData }, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp))
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).border(1.5.dp, color, RoundedCornerShape(3.dp)))
        Text(text, style = Type.tiny.copy(fontSize = Type.small.fontSize * 0.92f), color = T.text3)
    }
}

/** The fork from the app icon, lying flat – the startup animation lands exactly on it. */
@Composable
private fun LunchFork(modifier: Modifier) {
    val c = T.accent
    Canvas(modifier) { drawFork(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), c, LUNCH_FORK_STROKE.toPx()) }
}

@Composable
private fun LunchBlock(
    lunch: UiState<LunchMenu>, today: LocalDate, now: Int, haveTimetable: Boolean, myDay: TimetableDay?,
    myWeeks: List<Timetable>, onOpen: () -> Unit, intro: IntroState?,
) {
    val menu = lunch.data
    val todayLunch = menu?.day(today)
    val todayWindow = myDay?.lunchWindow()
    // Once today's lunch break is over (or there's no school today), show the next lunch instead.
    val todayOver = haveTimetable && (myDay == null || myDay.realSlots().isEmpty() || (todayWindow != null && now >= todayWindow.end))
    val next = if (todayOver || (menu != null && todayLunch == null)) menu?.days?.firstOrNull { it.date > today.toString() && it.options.isNotEmpty() } else null
    val day = next ?: todayLunch
    val dayDate = day?.date?.let { LocalDate.parse(it) } ?: today
    val window = if (next != null) myWeeks.firstNotNullOfOrNull { it.day(dayDate) }?.lunchWindow() else todayWindow
    val mine = if (menu != null && day != null) menu.myMeal(day) else null
    val heading = when {
        next == null -> S.yourLunch
        dayDate == today.plus(1, DateTimeUnit.DAY) -> S.tomorrowsLunch
        else -> S.lunchOn(dayDate)
    }
    Column(Modifier.padding(horizontal = 20.dp).pressable(onClick = onOpen)) {
        Hairline(strong = true)
        Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LunchFork(Modifier.size(width = 22.dp, height = 7.dp).onGloballyPositioned { intro?.fork = it.boundsInRoot() })
                AnimatedContent(heading, Modifier.weight(1f).padding(start = 10.dp), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lunchHead") {
                    Eyebrow(it, color = T.accent)
                }
                if (window != null) {
                    Text(
                        if (window.afterSchool) S.afterSchoolCaps else "${window.start.hm()} – ${window.end.hm()}",
                        style = Type.eyebrow, color = T.accent,
                    )
                }
            }
            val title = when {
                menu == null -> if (lunch.loading) S.loading else S.menuUnavailable
                day == null -> S.noLunchPlanned
                mine == null -> S.nothingOrdered
                else -> shortDish(mine.first.name)
            }
            AnimatedContent(title, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "dish") {
                Text(it, style = Type.title, color = T.text)
            }
            if (mine != null && menu?.source == LunchSource.Website) {
                Text(S.notConfirmed(mine.first.number), style = Type.small, color = T.text3)
            }
            day?.soup?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Ic.Soup, null, Modifier.size(16.dp), tint = T.text2)
                    Text(it, style = Type.small.copy(fontSize = Type.body.fontSize * 0.95f), color = T.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Hairline(strong = true)
    }
}

/** What "lunch" means for a day, so two classes can be compared: the gap's periods, or "after school at X". */
private fun TimetableDay.lunchKey(): String? {
    if (dayOff || realSlots().isEmpty()) return null
    val g = gaps().firstOrNull { it.kind == Gap.Kind.Lunch }
    return if (g != null) "gap:" + periodsIn(g.start, g.end).ifEmpty { listOf(g.start) }.joinToString(",")
    else "after:" + endTime()
}

@Composable
private fun ClassRow(
    name: String,
    isYou: Boolean,
    label: String?,
    st: UiState<Timetable>?,
    myDay: TimetableDay?,
    today: LocalDate,
    now: Int,
    nowFrac: Float?,
    progress: Float,
    onClick: () -> Unit,
    onStrip: (androidx.compose.ui.geometry.Rect) -> Unit,
    onSegs: (List<StripSeg>) -> Unit,
) {
    val day = st?.data?.day(today)
    val hasSchool = day != null && !day.dayOff && day.realSlots().isNotEmpty()
    val lunchGap = day?.gaps()?.firstOrNull { it.kind == Gap.Kind.Lunch }
    val lunchMatch = day != null && myDay != null && hasSchool && day.lunchKey() == myDay.lunchKey()
    val endMatch = day != null && myDay != null && hasSchool && day.endTime() == myDay.endTime()

    Column(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Type.bodyStrong, color = if (isYou) T.text2 else T.text, modifier = Modifier.width(NAME_W))
            val segs = remember(day, lunchGap) { if (hasSchool) stripSegs(day!!, lunchGap) else emptyList() }
            // Reported apart from the strip's position: the position callback doesn't run again when a
            // timetable arrives after layout, which left the launch animation without pills to land on.
            LaunchedEffect(segs) { onSegs(segs) }
            DayStrip(
                if (hasSchool) day else null, lunchGap, lunchMatch, progress, nowFrac,
                Modifier.weight(1f).height(14.dp).onGloballyPositioned { onStrip(it.boundsInRoot()) },
            )
        }
        Row(Modifier.padding(start = NAME_W), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val (status, color) = statusLine(day, now)
            Text(
                (label?.let { "$it · " } ?: "") + status, style = Type.small, color = if (isYou) T.text2 else color,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (hasSchool) {
                val lunchText = lunchGap?.let { g -> g.start.hm() + (periodText(periodsIn(g.start, g.end))?.let { " · $it" } ?: "") } ?: S.afterSchool
                Outlined(lunchText, T.accent, lunchMatch)
                Outlined(day!!.endTime()?.hm() ?: "—", T.text, endMatch)
            }
        }
    }
}

@Composable
private fun Outlined(text: String, color: Color, outlined: Boolean) {
    Text(
        text, style = Type.small, color = color,
        modifier = if (outlined) Modifier.offset(x = 0.dp).border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
        else Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun statusLine(day: TimetableDay?, now: Int): Pair<String, Color> = when (val s = day.status(now)) {
    ClassStatus.NoSchool -> S.noLessonsToday to T.text3
    is ClassStatus.BeforeSchool -> S.from(s.startsAt.hm()) to T.text2
    is ClassStatus.InLesson -> {
        val subj = s.slot.lessons.filter { !it.removed }.map { it.abbrev.ifEmpty { it.subject } }.distinct().joinToString(" / ")
        S.until(subj, s.until.hm()) to T.now
    }
    is ClassStatus.OnBreak -> when (s.gap.kind) {
        Gap.Kind.Lunch -> S.until(S.statusLunch, s.gap.end.hm()) to T.accent
        Gap.Kind.Free -> S.until(S.statusFree, s.gap.end.hm()) to T.good
        Gap.Kind.Short -> S.until(S.statusBreak, s.gap.end.hm()) to T.text2
    }
    is ClassStatus.Finished -> S.doneForToday to T.text3
}

/** The mini timeline: grey lessons, outlined lunch gap. [progress] reveals it left → right. */
@Composable
fun DayStrip(day: TimetableDay?, lunch: Gap?, lunchMatch: Boolean, progress: Float, nowFrac: Float?, modifier: Modifier) {
    val nowColor = T.now
    val block = T.block
    val track = T.line
    val accent = T.accent
    val accentSoft = T.accentSoft
    val match = T.text
    Canvas(modifier) {
        val w = size.width
        fun x(t: Int) = ((t - AXIS_START).toFloat() / (AXIS_END - AXIS_START)).coerceIn(0f, 1f) * w
        val limit = w * progress
        drawRoundRect(track, Offset(0f, size.height / 2 - 1.dp.toPx()), Size(w, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        if (nowFrac != null) {
            // the now-tick: same height on every row, never over the text below
            val nx = w * nowFrac
            drawLine(nowColor, Offset(nx, -5.dp.toPx()), Offset(nx, size.height + 5.dp.toPx()), 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        }
        if (day == null) return@Canvas
        val r = CornerRadius(3.dp.toPx())
        val top = 1.dp.toPx()
        val h = size.height - 2 * top
        day.realSlots().forEach { s ->
            val a = x(s.start); val b = minOf(x(s.end), limit)
            if (b > a) drawRoundRect(block, Offset(a, top), Size(b - a, h), r)
        }
        if (lunch != null) {
            val a = x(lunch.start); val b = minOf(x(lunch.end), limit)
            if (b > a) {
                drawRoundRect(accentSoft, Offset(a, top), Size(b - a, h), r)
                val sw = 1.5.dp.toPx()
                drawRoundRect(if (lunchMatch) match else accent, Offset(a + sw / 2, top + sw / 2), Size(b - a - sw, h - sw), r, style = Stroke(sw))
            }
        }
    }
}

/** Pills of a day strip as fractions of its width (lessons and the lunch gap), left to right. */
private fun stripSegs(day: TimetableDay, lunch: Gap?): List<StripSeg> {
    fun f(t: Int) = ((t - AXIS_START).toFloat() / (AXIS_END - AXIS_START)).coerceIn(0f, 1f)
    val list = day.realSlots().map { StripSeg(f(it.start), f(it.end), false) } +
        listOfNotNull(lunch?.let { StripSeg(f(it.start), f(it.end), true) })
    return list.filter { it.b > it.a }.sortedBy { it.a }
}
