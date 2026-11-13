package app.schoolday.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schoolday.data.Groups
import app.schoolday.data.TimetableMode
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type

/**
 * Settings › Your groups: every split subject of your class (ASW1 / ASW2, Nj / Šj…) with a row of
 * chips, "All" first. Picking a group hides the other groups' lessons in Timetable, Today, the
 * widgets and the lunch notification. Same choices as the iPhone version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsSheet(vm: AppViewModel, only: String? = null, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val raw by vm.rawTimetables.collectAsStateWithLifecycle()
    // The permanent timetable has every split, without this week's changes.
    LaunchedEffect(settings.myClass) { vm.loadTimetable(settings.myClass, TimetableMode.Permanent) }
    val perm = raw[TtKey(settings.myClass, TimetableMode.Permanent)]
    val tt = perm?.data ?: raw[TtKey(settings.myClass, TimetableMode.Actual)]?.data
    val all = tt?.let { Groups.choices(it) }.orEmpty()
    // Opened from a lesson's "!": just that subject (also found in this or next week if the
    // permanent timetable splits it differently), and the sheet closes once you pick.
    val choices = if (only == null) all else (all + listOf(TimetableMode.Actual, TimetableMode.Next).flatMap { m ->
        raw[TtKey(settings.myClass, m)]?.data?.let { Groups.choices(it) }.orEmpty()
    }).filter { it.key == only }.distinctBy { it.key }.take(1).ifEmpty { all }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = T.raised, contentColor = T.text, dragHandle = {
        Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(T.lineStrong))
    }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(S.yourGroups, style = Type.title, color = T.text)
                Text("${vm.className(settings.myClass)} · ${S.yourGroupsSub}", style = Type.small, color = T.text2)
            }
            if (choices.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val loading = tt == null || perm?.loading == true
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = T.accent)
                    Text(if (loading) S.loading else S.noSplits, style = Type.body, color = T.text2)
                }
            }
            choices.forEach { c ->
                val picked = settings.groups[c.key]?.takeIf { pick -> c.options.any { it.code == pick } }.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(c.title, style = Type.bodyStrong, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(if (c.sameSubject) S.yourGroup else S.yourSubject, style = Type.small, color = T.text3, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val options = listOf("" to (S.allGroups to S.everyGroup)) + c.options.map { it.code to (it.code to it.detail) }
                        options.forEach { (code, label) ->
                            GroupChip(label.first, label.second, code == picked, Modifier.weight(1f).fillMaxHeight()) {
                                vm.updateSettings { s -> s.copy(groups = if (code.isEmpty()) s.groups - c.key else s.groups + (c.key to code)) }
                                if (only != null) scope.launch { delay(250); onDismiss() }
                            }
                        }
                    }
                }
            }
            if (only == null) Text(S.praxeNote, style = Type.small, color = T.text3)
        }
    }
}

@Composable
private fun GroupChip(name: String, detail: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) T.accent else T.bg, label = "chipBg")
    val fg by animateColorAsState(if (on) T.onAccent else T.text, label = "chipFg")
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(bg).pressable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = Type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1, textAlign = TextAlign.Center)
        Text(detail, style = Type.small, color = fg.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
