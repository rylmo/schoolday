package app.schoolday.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.schoolday.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.schoolday.app
import app.schoolday.logic.nowMinutes
import app.schoolday.notify.LunchNotifier
import app.schoolday.ui.MainActivity
import app.schoolday.ui.S
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Which of the three Schoolday widgets. */
enum class WidgetKind { Both, Timetable, Menu }

private val MODE = stringPreferencesKey("mode")
/** What this widget shows, chosen in its settings; unset = the type it was placed as. */
private val KIND = stringPreferencesKey("kind")
private val VERSION = longPreferencesKey("version")
private val OpenKey = ActionParameters.Key<String>(LunchNotifier.EXTRA_OPEN)
private val ModeKey = ActionParameters.Key<String>("mode")

/** Values for [LunchNotifier.EXTRA_OPEN] when a widget is tapped. */
const val OPEN_TIMETABLE = "timetable"
const val OPEN_MENU = "menu"

/**
 * A scrolling list in the style of the Bakaláři widget: day headings, one rounded row per lesson
 * (period, start/end, subject, "group | room | teacher"), changed lessons in pink, your lunch in
 * orange and days off in blue. Colours come from Settings > Widgets.
 */
abstract class SchoolWidget(private val placedAs: WidgetKind) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // A running widget only recomposes on update(), so the data is reloaded whenever
            // Widgets.refresh bumps this number (new timetable, menu or colours).
            val version = currentState(VERSION) ?: 0L
            val data = remember(version) { WidgetData.load(context) }
            val kind = currentState(KIND)?.let { k -> WidgetKind.entries.firstOrNull { it.name == k } } ?: placedAs
            val menuMode = when (kind) {
                WidgetKind.Timetable -> false
                WidgetKind.Menu -> true
                WidgetKind.Both -> currentState(MODE) == OPEN_MENU
            }
            WidgetBody(kind, menuMode, data)
        }
    }
}

class CombinedWidget : SchoolWidget(WidgetKind.Both)
class TimetableWidget : SchoolWidget(WidgetKind.Timetable)
class MenuWidget : SchoolWidget(WidgetKind.Menu)

class CombinedWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CombinedWidget() }
class TimetableWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TimetableWidget() }
class MenuWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MenuWidget() }

/** The Timetable / Menu switch of the combined widget (remembered per widget). */
class SetModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[ModeKey] ?: return
        updateAppWidgetState(context, glanceId) { it[MODE] = mode }
        Widgets.updateOne(context, glanceId)
    }
}

private fun c(color: Color) = ColorProvider(color)

@Composable
private fun WidgetBody(kind: WidgetKind, menuMode: Boolean, data: WidgetData) {
    val st = data.style
    val open = actionStartActivity<MainActivity>(actionParametersOf(OpenKey to if (menuMode) OPEN_MENU else OPEN_TIMETABLE))
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(st.bg.copy(alpha = st.bg.alpha * data.opacity))
            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
    ) {
        if (kind == WidgetKind.Both) {
            Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (menuMode) (if (data.menuTitleAll) S.menuTab else S.lunch).uppercase(S.locale) else data.className,
                    style = TextStyle(color = c(st.text), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                )
                Row(GlanceModifier.cornerRadius(14.dp).background(st.row).padding(2.dp)) {
                    IconPill(R.drawable.ic_widget_timetable, S.timetable, !menuMode, st, OPEN_TIMETABLE)
                    IconPill(R.drawable.ic_widget_lunch, if (data.menuTitleAll) S.menuTab else S.lunch, menuMode, st, OPEN_MENU)
                }
            }
        }
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(if (menuMode) data.menu else data.timetable) { item ->
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp).clickable(open)) { ItemView(item, st) }
            }
            item { Spacer(GlanceModifier.height(6.dp)) }
        }
    }
}

/** One half of the Timetable / Lunch switch: the app's tab icon, filled orange when chosen. */
@Composable
private fun IconPill(icon: Int, label: String, on: Boolean, st: WidgetStyle, mode: String) {
    Box(
        GlanceModifier.size(width = 36.dp, height = 26.dp).cornerRadius(12.dp)
            .background(if (on) st.accent else Color.Transparent)
            .clickable(actionRunCallback<SetModeAction>(actionParametersOf(ModeKey to mode))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(17.dp),
            colorFilter = ColorFilter.tint(c(if (on) Color(0xFF2A1A08) else st.text2)),
        )
    }
}

@Composable
private fun ItemView(item: WidgetItem, st: WidgetStyle) {
    when (item) {
        is WidgetItem.Header -> Text(
            item.text, maxLines = 1, modifier = GlanceModifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
            style = TextStyle(color = c(st.text), fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        is WidgetItem.Note -> RowBox(if (item.blue) st.dayOff.copy(alpha = 0.16f) else st.row) {
            Text(item.text, style = TextStyle(color = c(if (item.blue) st.dayOff else st.text2), fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
        is WidgetItem.Lesson -> {
            val fg = if (item.changed) st.changed else st.text
            val strike = if (item.cancelled) TextDecoration.LineThrough else TextDecoration.None
            RowBox(if (item.changed) st.changed.copy(alpha = 0.18f) else st.row) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.period, modifier = GlanceModifier.width(18.dp), style = TextStyle(color = c(fg), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                    Column(GlanceModifier.width(44.dp)) {
                        Text(item.start, style = TextStyle(color = c(if (item.changed) st.changed else st.text2), fontSize = 12.sp))
                        Text(item.end, style = TextStyle(color = c(if (item.changed) st.changed else st.text2), fontSize = 12.sp))
                    }
                    Column(GlanceModifier.defaultWeight()) {
                        Text(item.subject, maxLines = 1, style = TextStyle(color = c(st.text), fontSize = 15.sp, fontWeight = FontWeight.Bold, textDecoration = strike))
                        item.details.forEach {
                            Text(it, maxLines = 1, style = TextStyle(color = c(st.text2), fontSize = 12.sp, textDecoration = strike))
                        }
                    }
                }
            }
        }
        is WidgetItem.Lunch -> RowBox(st.accent.copy(alpha = 0.16f)) {
            Column {
                Text(item.title, maxLines = 1, style = TextStyle(color = c(st.accent), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                item.dish?.let { Text(it, maxLines = 2, style = TextStyle(color = c(st.text2), fontSize = 12.sp)) }
            }
        }
        is WidgetItem.Meal -> RowBox(if (item.ordered) st.accent.copy(alpha = 0.16f) else st.row) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.number != null) {
                    Box(
                        GlanceModifier.size(22.dp).cornerRadius(11.dp).background(if (item.ordered) st.accent else st.row),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.number.toString(),
                            style = TextStyle(color = c(if (item.ordered) Color(0xFF2A1A08) else st.text2), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(GlanceModifier.width(10.dp))
                } else {
                    Text(
                        item.label.orEmpty(), modifier = GlanceModifier.width(72.dp), maxLines = 1,
                        style = TextStyle(color = c(st.text2), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
                Text(
                    item.text, maxLines = 2, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = c(if (item.ordered || item.number == null) st.text else st.text2), fontSize = 14.sp,
                        fontWeight = if (item.ordered) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RowBox(bg: Color, content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(bg).padding(horizontal = 10.dp, vertical = 8.dp)) { content() }
}

/** Redraws the widgets after new data or settings, and again when the current lesson ends. */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { refreshNow(app) }
    }

    private val all get() = listOf(CombinedWidget(), TimetableWidget(), MenuWidget())

    /** Redraws one widget, whichever of the three it was placed as. */
    suspend fun updateOne(ctx: Context, id: GlanceId) {
        val m = GlanceAppWidgetManager(ctx)
        all.firstOrNull { id in m.getGlanceIds(it.javaClass) }?.update(ctx, id)
    }

    /** The type a widget was placed as (before any change in its settings). */
    fun defaultKind(ctx: Context, appWidgetId: Int): WidgetKind =
        when (AppWidgetManager.getInstance(ctx).getAppWidgetInfo(appWidgetId)?.provider?.className) {
            TimetableWidgetReceiver::class.java.name -> WidgetKind.Timetable
            MenuWidgetReceiver::class.java.name -> WidgetKind.Menu
            else -> WidgetKind.Both
        }

    suspend fun kindOf(ctx: Context, id: GlanceId): WidgetKind? =
        getAppWidgetState(ctx, PreferencesGlanceStateDefinition, id)[KIND]?.let { k -> WidgetKind.entries.firstOrNull { it.name == k } }

    suspend fun setKind(ctx: Context, id: GlanceId, kind: WidgetKind) {
        updateAppWidgetState(ctx, id) { it[KIND] = kind.name; it[VERSION] = System.currentTimeMillis() }
        updateOne(ctx, id)
    }

    suspend fun refreshNow(ctx: Context) {
        runCatching {
            val m = GlanceAppWidgetManager(ctx)
            val stamp = System.currentTimeMillis()
            for (widget in listOf(CombinedWidget(), TimetableWidget(), MenuWidget())) {
                for (id in m.getGlanceIds(widget.javaClass)) {
                    updateAppWidgetState(ctx, id) { it[VERSION] = stamp }
                    widget.update(ctx, id)
                }
            }
        }
        scheduleNext(ctx)
    }

    private suspend fun anyPlaced(ctx: Context): Boolean {
        val m = GlanceAppWidgetManager(ctx)
        return listOf(CombinedWidget::class.java, TimetableWidget::class.java, MenuWidget::class.java)
            .any { m.getGlanceIds(it).isNotEmpty() }
    }

    /** At the end of the current lesson (so it drops off the top), or just after midnight. */
    private suspend fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, WidgetTickReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (!anyPlaced(ctx)) return
        val s = ctx.app.prefs.value
        val today = LocalDate.now()
        val now = nowMinutes()
        val ends = listOf(app.schoolday.data.TimetableMode.Actual, app.schoolday.data.TimetableMode.Next)
            .mapNotNull { ctx.app.repo.cachedTimetable(s.myClass, it) }
            .flatMap { it.days }.firstOrNull { it.date == today.toString() }
            ?.slots?.map { it.end }.orEmpty()
        val next = ends.filter { it > now }.minOrNull()
        val zone = ZoneId.systemDefault()
        val at = if (next != null) today.atStartOfDay(zone).toInstant().toEpochMilli() + next * 60_000L
        else today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000L
        am.setWindow(AlarmManager.RTC, at, 2 * 60_000L, pi)
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { Widgets.refreshNow(context.applicationContext) } finally { done.finish() }
        }
    }
}
