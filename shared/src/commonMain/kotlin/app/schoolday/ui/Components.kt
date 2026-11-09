package app.schoolday.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type

val LocalBottomInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

val Soft = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** Clickable with a gentle press-down scale instead of a Material ripple. */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    this.composed {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Soft, label = "press")
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
    }

@Composable
fun Hairline(modifier: Modifier = Modifier, strong: Boolean = false) {
    Box(modifier.fillMaxWidth().height(1.dp).background(if (strong) T.lineStrong else T.line))
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = T.text2) {
    Text(text.uppercase(), style = Type.eyebrow, color = color, modifier = modifier)
}

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = T.text2, enabled: Boolean = true) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).pressable(enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (enabled) tint else tint.copy(alpha = 0.3f))
    }
}

/** Small rounded label, e.g. "Sub" or "Moved here". */
@Composable
fun Tag(text: String, fg: Color, bg: Color = Color.Transparent, outline: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = Type.tiny.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp)), color = fg,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .then(if (outline) Modifier.border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(5.dp)) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}


/** Simple pill toggle (no Material switch). */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) T.accent else T.lineStrong, label = "track")
    val knobX by animateDpAsState(if (checked) 20.dp else 2.dp, spring(stiffness = Spring.StiffnessMedium), label = "knob")
    Box(
        Modifier.size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(track)
            .clickable(role = Role.Switch) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = knobX, y = 2.dp).size(24.dp).clip(CircleShape).background(if (checked) T.onAccent else T.text2))
    }
}

/** A row with title + optional subtitle and something on the right. */
@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.body, color = T.text)
            if (subtitle != null) Text(subtitle, style = Type.small, color = T.text2)
        }
        trailing()
    }
}

/** Bottom-sheet picker used instead of Material dropdowns. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <V> PickerSheet(
    title: String,
    items: List<V>,
    selected: (V) -> Boolean,
    label: (V) -> String,
    onPick: (V) -> Unit,
    onDismiss: () -> Unit,
    section: ((V) -> String?)? = null,
    leading: (@Composable (V) -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = T.raised, contentColor = T.text, dragHandle = {
        Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(T.lineStrong))
    }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = Type.heading, color = T.text, modifier = Modifier.padding(bottom = 8.dp))
            androidx.compose.foundation.lazy.LazyColumn {
                items.forEachIndexed { i, v ->
                    val sec = section?.invoke(v)
                    if (sec != null && (i == 0 || section.invoke(items[i - 1]) != sec)) {
                        item(key = "sec-$sec") { Eyebrow(sec, Modifier.padding(top = 14.dp, bottom = 4.dp), color = T.text3) }
                    }
                    item(key = "i-$i") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(v) }.padding(vertical = 13.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            leading?.invoke(v)
                            Text(label(v), style = Type.body, color = T.text, modifier = Modifier.weight(1f))
                            if (selected(v)) Icon(Ic.Check, S.selected, Modifier.size(20.dp), tint = T.accent)
                        }
                    }
                }
            }
        }
    }
}

/** Inline "couldn't refresh" note. */
@Composable
fun ErrorNote(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(T.raised).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Offline, null, Modifier.size(16.dp), tint = T.text2)
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.small, color = T.text2)
    }
}

/** Light bar at the bottom with an optional "Undo" – replaces confirm dialogs. */
@Composable
fun UndoBar(text: String, onUndo: (() -> Unit)?, modifier: Modifier = Modifier) {
    val bg = if (T.isDark) T.text else Color(0xFF1B1C1F)
    val fg = if (T.isDark) T.bg else Color(0xFFF2F2F2)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.small.copy(fontSize = Type.body.fontSize), color = fg, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (onUndo != null) {
            Text(
                S.undo, style = Type.bodyStrong, color = T.accent.let { if (T.isDark) Color(0xFFA3530F) else it },
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUndo).padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

/** Text whose characters roll vertically when they change (times, dates). */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch, label = "roll$i",
                transitionSpec = {
                    (slideInVertically(tween(260)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutVertically(tween(260)) { -it } + fadeOut(tween(160)))
                },
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Pull-to-refresh with a small fork that tilts while pulling and spins while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForkRefresh(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing, onRefresh = onRefresh, state = state, modifier = modifier,
        indicator = { ForkIndicator(state, isRefreshing, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForkIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    val pull = state.distanceFraction.coerceIn(0f, 1.4f)
    val shown = if (refreshing) 1f else pull.coerceAtMost(1f)
    if (shown <= 0.01f) return
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spinAngle",
    )
    Box(
        modifier.offset(y = (if (refreshing) 56f else 56f * pull).dp - 36.dp)
            .graphicsLayer { alpha = shown; scaleX = 0.6f + 0.4f * shown; scaleY = 0.6f + 0.4f * shown }
            .size(36.dp).clip(CircleShape).background(T.raised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Ic.Lunch, null, Modifier.size(20.dp).rotate(if (refreshing) spin else pull * 200f), tint = T.accent)
    }
}

/** Bottom navigation: icon + label, with a short underline that slides to the active tab. */
@Composable
fun NavBar(items: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    var width by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = LocalDensity.current
    val cell = with(density) { (width / items.size.coerceAtLeast(1)).toDp() }
    val x by animateDpAsState(cell * selected + cell / 2 - 9.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "navX")
    Column(Modifier.fillMaxWidth().background(T.bg).navigationBarsPadding()) {
        Hairline()
        Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp)) {
                items.forEachIndexed { i, (label, icon) ->
                    val active = i == selected
                    val tint by animateColorAsState(if (active) T.text else T.text3, label = "navTint")
                    Column(
                        Modifier.weight(1f).pressable(role = Role.Tab) { onSelect(i) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(icon, null, Modifier.size(22.dp), tint = tint)
                        Text(label, style = Type.tiny.copy(fontSize = Type.small.fontSize.times(0.92f)), color = tint)
                    }
                }
            }
            if (width > 0) Box(Modifier.offset(x = x, y = 54.dp).size(width = 18.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(T.accent))
        }
    }
}

/** "Mgr. Pavlína Chovancová" → "Chovancová" (falls back to the short code). */
fun surname(full: String, short: String): String =
    full.split(' ').map { it.trim(',') }.lastOrNull { it.isNotEmpty() && !it.endsWith('.') } ?: short

val Dp.half get() = this / 2
