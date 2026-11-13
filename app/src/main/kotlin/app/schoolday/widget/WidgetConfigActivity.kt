package app.schoolday.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schoolday.ui.AppViewModel
import app.schoolday.ui.Eyebrow
import app.schoolday.ui.Hairline
import app.schoolday.ui.IconBtn
import app.schoolday.ui.PickerSheet
import app.schoolday.ui.S
import app.schoolday.ui.SettingRow
import app.schoolday.ui.WidgetSettings
import app.schoolday.ui.theme.AppTheme
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.SchoolDayTheme
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * Opened from a placed widget (hold it, then the launcher's widget settings): what this widget
 * shows (any of the three types, without removing it) and the shared background and transparency.
 * Also allowed, but never required, when a widget is first added.
 */
class WidgetConfigActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Settings are optional: leaving this screen always keeps the widget.
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val placedAs = Widgets.defaultKind(this, appWidgetId)
        enableEdgeToEdge()

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            var kind by remember { mutableStateOf(placedAs) }
            var picker by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { Widgets.kindOf(this@WidgetConfigActivity, glanceId)?.let { kind = it } }

            SchoolDayTheme(AppTheme.of(settings.theme)) {
                Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Ic.Back, S.back, { finish() }, tint = T.text)
                        Text(S.widgetSettings, style = Type.title, color = T.text)
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                        Spacer(Modifier.height(20.dp))
                        Eyebrow(S.widgetShows, color = T.text3)
                        Spacer(Modifier.height(4.dp))
                        Hairline(strong = true)
                        WidgetKind.entries.forEach { k ->
                            SettingRow(S.widgetKindName(k), onClick = {
                                kind = k
                                scope.launch { Widgets.setKind(this@WidgetConfigActivity, glanceId, k) }
                            }) {
                                if (kind == k) Icon(Ic.Check, null, Modifier.size(20.dp), tint = T.accent)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Eyebrow(S.widgets, color = T.text3)
                        Spacer(Modifier.height(4.dp))
                        Hairline(strong = true)
                        WidgetSettings(vm, settings.widgetStyle, settings.widgetOpacity) { picker = true }
                        Text(S.widgetSharedNote, style = Type.small, color = T.text3)
                    }
                }
                if (picker) PickerSheet(
                    S.widgetBackground, WidgetStyle.entries, { it.id == settings.widgetStyle }, { S.widgetStyleName(it.id) },
                    onPick = { w -> vm.updateSettings { it.copy(widgetStyle = w.id) }; picker = false },
                    onDismiss = { picker = false },
                    leading = { Box(Modifier.size(20.dp).clip(CircleShape).background(it.bg).border(1.dp, T.lineStrong, CircleShape)) },
                )
            }
        }
    }
}
