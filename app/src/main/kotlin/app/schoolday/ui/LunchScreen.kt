package app.schoolday.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schoolday.data.ICanteenClient
import app.schoolday.data.LunchDay
import app.schoolday.data.LunchMenu
import app.schoolday.data.LunchSource
import app.schoolday.data.MealOption
import app.schoolday.logic.myMeal
import app.schoolday.logic.shortDish
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LunchScreen(vm: AppViewModel, sheetDate: String?, onSheetDate: (String?) -> Unit, onSettings: () -> Unit) {
    val lunch by vm.lunch.collectAsStateWithLifecycle()
    val busy by vm.lunchBusy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val menu = lunch.data
    val today = LocalDate.now().toString()

    LaunchedEffect(Unit) { vm.refreshLunch() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(S.lunchTitle, style = Type.display, color = T.text, modifier = Modifier.weight(1f))
            if (menu != null) {
                Row(Modifier.padding(bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (menu.source == LunchSource.ICanteen) T.good else T.text3))
                    Text(if (menu.source == LunchSource.ICanteen) "iCanteen" else S.schoolWebsite, style = Type.small, color = T.text3)
                }
            }
            IconBtn(Ic.External, S.openICanteen, {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ICanteenClient.LOGIN_PAGE)))
            })
        }

        AnimatedVisibility(menu?.source == LunchSource.Website) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(T.raised)
                    .then(if (!settings.hasICanteen) Modifier.pressable(onClick = onSettings) else Modifier)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Info, null, Modifier.size(16.dp), tint = T.text2)
                Text(
                    if (!settings.hasICanteen) S.viewOnlyLogin else S.viewOnlyDown,
                    style = Type.small, color = T.text2,
                )
            }
        }
        AnimatedVisibility(lunch.error != null && menu != null && menu.source != LunchSource.Website) {
            ErrorNote(S.offlineMenu, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
        }

        ForkRefresh(lunch.loading, onRefresh = { vm.refreshLunch(force = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (menu == null) {
                    item { Text(if (lunch.loading) S.loading else S.menuUnavailable, Modifier.padding(32.dp), color = T.text2, style = Type.body) }
                } else {
                    val days = menu.days.filter { it.date >= today }.ifEmpty { menu.days }
                    items(days, key = { it.date }) { day ->
                        DayBlock(menu, day, day.date == today, day.date in busy, Modifier.animateItem()) { opt ->
                            when {
                                opt.ordered && opt.cancelUrl != null -> vm.lunchAction(day.date, opt.number, cancel = true)
                                !opt.ordered && opt.orderUrl != null -> vm.lunchAction(day.date, opt.number, cancel = false)
                            }
                        }
                    }
                }
            }
        }
    }

    // Opened from the notification: today's full details.
    if (sheetDate != null) {
        val day = menu?.days?.firstOrNull { it.date == sheetDate }
        ModalBottomSheet(onDismissRequest = { onSheetDate(null) }, containerColor = T.raised, contentColor = T.text, dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(T.lineStrong))
        }) { TodayLunchDetail(menu, day) }
    }
}

@Composable
private fun DayBlock(menu: LunchMenu, day: LunchDay, isToday: Boolean, busy: Boolean, modifier: Modifier, onTap: (MealOption) -> Unit) {
    val date = LocalDate.parse(day.date)
    val ic = menu.source == LunchSource.ICanteen
    val locked = ic && day.options.none { it.orderUrl != null || it.cancelUrl != null }
    val dim by animateFloatAsState(if (locked) 0.45f else 1f, label = "dim")
    Column(modifier.padding(horizontal = 20.dp)) {
        Hairline(strong = true)
        Column(Modifier.padding(top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isToday) S.today else S.dayName(date), style = Type.heading, color = if (isToday) T.accent else T.text)
                Text("${date.dayOfMonth}. ${date.monthValue}.", style = Type.small, color = T.text3)
                Box(Modifier.weight(1f))
                if (!busy && locked && ic && day.ordered == null) Text(S.notOrdered, style = Type.small, color = T.changed)
                when {
                    busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = T.accent)
                    locked -> Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(T.raised).padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Ic.Lock, null, Modifier.size(13.dp), tint = T.text2)
                        Text(S.locked, style = Type.tiny.copy(fontWeight = FontWeight.SemiBold, fontSize = Type.small.fontSize * 0.92f), color = T.text2)
                    }
                    ic && day.ordered == null -> Text(S.notOrdered, style = Type.small, color = T.changed)
                }
            }
            day.soup?.let { Text(it, style = Type.small, color = T.text2) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                day.options.forEach { opt ->
                    val actionable = !busy && ((opt.ordered && opt.cancelUrl != null) || (!opt.ordered && opt.orderUrl != null))
                    // On locked days only the meals you didn't pick fade; your lunch stays bright.
                    MealLine(opt, highlight = opt.ordered || !ic && opt.number == 1, actionable, Modifier.alpha(if (opt.ordered) 1f else dim)) { onTap(opt) }
                }
            }
        }
    }
}

@Composable
private fun MealLine(opt: MealOption, highlight: Boolean, actionable: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val badgeBg by animateColorAsState(if (opt.ordered) T.accent else Color.Transparent, tween(260), label = "badgeBg")
    val badgeFg by animateColorAsState(if (opt.ordered) T.onAccent else T.text3, tween(260), label = "badgeFg")
    val text by animateColorAsState(if (opt.ordered) T.text else if (highlight) T.text else T.text2, tween(260), label = "mealText")
    val pop by animateFloatAsState(if (opt.ordered) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "pop")
    Row(
        modifier.fillMaxWidth().then(if (actionable) Modifier.pressable(onClick = onTap) else Modifier).padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.graphicsLayer { scaleX = pop; scaleY = pop }.size(24.dp).clip(CircleShape).background(badgeBg)
                .border(if (opt.ordered) 0.dp else 1.5.dp, T.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(opt.number.toString(), style = Type.tiny.copy(fontWeight = FontWeight.Bold, fontSize = Type.small.fontSize), color = badgeFg) }
        Text(
            shortDish(opt.name), style = if (opt.ordered) Type.body.copy(fontWeight = FontWeight.SemiBold) else Type.body,
            color = text, modifier = Modifier.weight(1f).padding(top = 1.dp),
        )
    }
}

@Composable
private fun TodayLunchDetail(menu: LunchMenu?, day: LunchDay?) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (menu == null || day == null) {
            Text(S.noLunchInfo, style = Type.heading, color = T.text)
            return
        }
        val mine = menu.myMeal(day)
        Eyebrow(S.today, color = T.accent)
        Text(mine?.first?.let { shortDish(it.name) } ?: S.nothingOrdered, style = Type.title, color = T.text)
        day.soup?.let {
            Column { Eyebrow(S.soup, color = T.text3); Text(it, style = Type.body, color = T.text) }
        }
        day.options.filter { it.number != mine?.first?.number }.forEach {
            Column { Eyebrow(S.lunchN(it.number), color = T.text3); Text(shortDish(it.name), style = Type.body, color = T.text) }
        }
    }
}
