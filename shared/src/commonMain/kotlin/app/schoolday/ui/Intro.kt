package app.schoolday.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.schoolday.data.Diag
import app.schoolday.ui.theme.T
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow
import kotlin.time.TimeSource

/** One pill of a day strip, as fractions of the strip's width. */
data class StripSeg(val a: Float, val b: Float, val lunch: Boolean)

/**
 * Startup animation: the launch-screen icon (lesson bars + fork in the gap) turns into the
 * Today screen. The fork flies up and lands on the little fork next to "Your lunch"; each
 * bar stretches across its class row and then breaks into that class's lesson pills.
 *
 * It waits until it can be smooth instead of testing once: startup work makes the first
 * frames slow on every phone, so the icon holds still until the Today layout is measured and
 * frames arrive on time. Only a phone that never settles, has animations turned off, or is a
 * low-RAM device skips it, and the launch screen then simply fades away.
 */
class IntroState(val willPlay: Boolean) {
    var fork by mutableStateOf<Rect?>(null)
    val strips = mutableStateMapOf<Int, Rect>()
    val segs = mutableStateMapOf<Int, List<StripSeg>>()
    var finished by mutableStateOf(!willPlay)
    val targetsReady get() = fork != null && strips.isNotEmpty()
}

// Same geometry as res/drawable/splash_icon.xml: the 160-unit glyph drawn in a 128dp box at screen centre.
private const val GLYPH = 160f
private val BOX = 128.dp
private val SPLASH_BG = Color(0xFF0F1012)
private val SPLASH_INK = Color(0xFFECE9E2)
private val SPLASH_GREY = Color(0xFF5C5E66)
private val SPLASH_FORK = Color(0xFFF2A65A)
private val bars = listOf(Rect(36f, 34f, 124f, 48f), Rect(36f, 56f, 124f, 70f), Rect(36f, 100f, 124f, 114f))
private val greyBar = Rect(36f, 122f, 92f, 136f)
private val forkGlyph = Rect(44f, 78f, 118f, 92f)

private const val LAYOUT_TIMEOUT_MS = 2_500L
private const val SETTLE_TIMEOUT_NS = 1_500_000_000L
private const val FRAME_OK_NS = 34_000_000L
private const val CALM_FRAMES = 6

/** Stroke of the small fork beside "Your lunch" (TodayScreen draws it with the same value). */
val LUNCH_FORK_STROKE = 1.6.dp

@Composable
fun IntroOverlay(state: IntroState) {
    val t = remember { Animatable(0f) }
    var skip by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 1. wait (briefly) for the Today screen underneath to be laid out
        val began = TimeSource.Monotonic.markNow()
        val ready = withTimeoutOrNull(LAYOUT_TIMEOUT_MS) { snapshotFlow { state.targetsReady }.first { it } } != null
        // 2. wait for frames to settle: the overlay shows the same picture as the launch screen
        //    meanwhile, so the wait is invisible. A run of on-time frames means startup work is over.
        var calm = 0
        var slowest = 0L
        var last = withFrameNanos { it }
        val deadline = last + SETTLE_TIMEOUT_NS
        while (ready && calm < CALM_FRAMES && last < deadline) {
            val now = withFrameNanos { it }
            val gap = now - last
            last = now
            slowest = maxOf(slowest, gap)
            calm = if (gap <= FRAME_OK_NS) calm + 1 else 0
        }
        val waitedMs = began.elapsedNow().inWholeMilliseconds
        if (ready && calm >= CALM_FRAMES) {
            Diag.intro = "played after ${waitedMs} ms wait (slowest frame ${slowest / 1_000_000} ms)"
            t.animateTo(1f, tween(1400, easing = LinearEasing))
        } else {
            Diag.intro = if (!ready) "skipped: Today screen not ready after ${waitedMs} ms"
            else "skipped: frames never settled (slowest ${slowest / 1_000_000} ms)"
            skip = true
            fade.animateTo(0f, tween(220))
        }
        state.finished = true
    }

    val bg = T.bg; val block = T.block; val accent = T.accent
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val box = BOX.toPx()
        val ox = size.width / 2 - box / 2
        val oy = size.height / 2 - box / 2
        val k = box / GLYPH
        fun g(r: Rect) = Rect(ox + r.left * k, oy + r.top * k, ox + r.right * k, oy + r.bottom * k)
        fun seg(a: Float, b: Float) = ease(((p - a) / (b - a)).coerceIn(0f, 1f))

        if (skip) {
            drawRect(SPLASH_BG.copy(alpha = fade.value))
            bars.forEach { r -> val x = g(r); drawRoundRect(SPLASH_INK.copy(alpha = fade.value), x.topLeft, x.size, CornerRadius(7f * k)) }
            val gr = g(greyBar); drawRoundRect(SPLASH_GREY.copy(alpha = fade.value), gr.topLeft, gr.size, CornerRadius(7f * k))
            drawFork(g(forkGlyph), SPLASH_FORK.copy(alpha = fade.value), 5f * k)
            return@Canvas
        }

        // Background: splash colour → theme colour, then it dissolves to reveal the Today text.
        val bgAlpha = 1f - seg(0.62f, 0.9f)
        drawRect(lerp(SPLASH_BG, bg, seg(0f, 0.3f)).copy(alpha = bgAlpha))
        // Everything still on the overlay melts into the identical real UI at the very end.
        val endAlpha = 1f - seg(0.9f, 1f)

        // The short grey bar drops away first.
        val gr = g(greyBar)
        val drop = seg(0.04f, 0.22f)
        drawRoundRect(
            SPLASH_GREY.copy(alpha = 1f - drop), gr.topLeft + Offset(0f, 18.dp.toPx() * drop), gr.size, CornerRadius(7f * k),
        )

        // Lesson bars: stretch across the class row, then break into the pills.
        val stretch = seg(0.1f, 0.46f)
        val stripH = 12.dp.toPx()
        val pillR = 3.dp.toPx()
        bars.forEachIndexed { i, r ->
            val from = g(r)
            val strip = state.strips[i] ?: return@forEachIndexed
            val track = Rect(strip.left, strip.top + 1.dp.toPx(), strip.right, strip.top + 1.dp.toPx() + stripH)
            val color = lerp(SPLASH_INK, block, seg(0.25f, 0.55f)).copy(alpha = endAlpha)
            if (p < 0.44f) {
                val rect = lerpRect(from, track, stretch)
                drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(mix(7f * k, pillR, stretch)))
                return@forEachIndexed
            }
            val segs = state.segs[i].orEmpty()
            val w = track.width
            fun x(f: Float) = track.left + f * w
            // Pieces tile the bar with no gaps, then shrink to the exact pills one after another.
            segs.forEachIndexed { n, s ->
                val startEdge = if (n == 0) 0f else (segs[n - 1].b + s.a) / 2
                val endEdge = if (n == segs.lastIndex) s.b else (s.b + segs[n + 1].a) / 2
                val q = seg(0.46f + 0.035f * n, 0.66f + 0.035f * n)
                val left = mix(x(startEdge), x(s.a), q)
                val right = mix(x(endEdge), x(s.b), q)
                // small "pop": the pill swells a touch while it separates
                val swell = 1f + 0.35f * kotlin.math.sin(q * kotlin.math.PI).toFloat()
                val h = stripH * swell
                val top = track.center.y - h / 2
                if (s.lunch) {
                    // The grey piece just fades out while the orange outline fades in – never a filled
                    // orange pill. The soft tint comes from the real strip underneath as the overlay clears.
                    drawRoundRect(color.copy(alpha = (1f - q) * endAlpha), Offset(left, top), androidx.compose.ui.geometry.Size(right - left, h), CornerRadius(pillR))
                    val sw = 1.5.dp.toPx()
                    drawRoundRect(
                        accent.copy(alpha = q * endAlpha), Offset(left + sw / 2, top + sw / 2),
                        androidx.compose.ui.geometry.Size(right - left - sw, h - sw), CornerRadius(pillR), style = Stroke(sw),
                    )
                } else {
                    drawRoundRect(color, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, h), CornerRadius(pillR))
                }
            }
            // The rest of the bar past the last lesson slides off to the right and fades.
            val tailStart = segs.lastOrNull()?.b ?: 0f
            val tq = seg(0.46f, 0.78f)
            val slide = w * 0.25f * tq
            val tl = x(tailStart) + slide
            if (track.right + slide > tl && tq < 1f) {
                drawRoundRect(
                    color.copy(alpha = (1f - tq) * endAlpha), Offset(tl, track.top),
                    androidx.compose.ui.geometry.Size(track.right + slide - tl, stripH), CornerRadius(pillR),
                )
            }
        }

        // Fork: lifts off and lands on the small fork beside "Your lunch" – which stays there.
        val f = seg(0.04f, 0.5f)
        val forkFrom = g(forkGlyph)
        val forkTo = state.fork ?: forkFrom
        // a gentle arc upwards on the way
        val arc = -40.dp.toPx() * kotlin.math.sin(f * kotlin.math.PI).toFloat()
        val fr = lerpRect(forkFrom, forkTo, f).translate(0f, arc)
        drawFork(fr, lerp(SPLASH_FORK, accent, f).copy(alpha = endAlpha), mix(5f * k, LUNCH_FORK_STROKE.toPx(), f))
    }
}

/** The fork from the icon, fitted into [r] (tines on the left, handle to the right). */
fun DrawScope.drawFork(r: Rect, color: Color, stroke: Float) {
    fun x(u: Float) = r.left + (u - 44f) / 74f * r.width
    fun y(v: Float) = r.top + (v - 78f) / 14f * r.height
    drawLine(color, Offset(x(118f), y(85f)), Offset(x(66f), y(85f)), stroke, StrokeCap.Round)
    drawLine(color, Offset(x(66f), y(78f)), Offset(x(66f), y(92f)), stroke, StrokeCap.Round)
    listOf(78f, 85f, 92f).forEach { v -> drawLine(color, Offset(x(66f), y(v)), Offset(x(44f), y(v)), stroke, StrokeCap.Round) }
}

private fun lerpRect(a: Rect, b: Rect, f: Float) = Rect(mix(a.left, b.left, f), mix(a.top, b.top, f), mix(a.right, b.right, f), mix(a.bottom, b.bottom, f))

/** Smooth in-out curve. */
private fun ease(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2.0 * x + 2).pow(3.0).toFloat() / 2

private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
