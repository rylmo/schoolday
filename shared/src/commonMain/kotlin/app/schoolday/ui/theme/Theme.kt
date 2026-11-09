package app.schoolday.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.schoolday.res.Res
import app.schoolday.res.geologica
import org.jetbrains.compose.resources.Font

/** Every colour the UI uses. Screens read these via [T] – never Material's palette directly. */
@Immutable
data class Tokens(
    val bg: Color,
    val raised: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val block: Color,
    val accent: Color,
    val onAccent: Color,
    val now: Color,
    val good: Color,
    val changed: Color,
    val removed: Color,
    val isDark: Boolean,
) {
    val accentSoft get() = accent.copy(alpha = if (isDark) 0.13f else 0.12f)
    val accentLine get() = accent.copy(alpha = 0.38f)
    val nowSoft get() = now.copy(alpha = 0.16f)
    val changedSoft get() = changed.copy(alpha = 0.14f)
    val removedSoft get() = removed.copy(alpha = 0.14f)
}

val LocalTokens = staticCompositionLocalOf { midnight }

/** Shorthand used everywhere: `T.text`, `T.accent` … */
val T: Tokens @Composable get() = LocalTokens.current

/** Colour themes offered in Settings. [preview] = background, accent, lunch – used for swatches. */
enum class AppTheme(val id: String, val label: String, val dark: Boolean?) {
    System("system", "System (One UI)", null),
    Midnight("midnight", "Midnight", true),
    Black("black", "Pure black", true),
    Forest("forest", "Forest", true),
    Ocean("ocean", "Ocean", true),
    Plum("plum", "Plum", true),
    Paper("paper", "Paper", false),
    Sand("sand", "Sand", false),
    Mint("mint", "Mint", false);

    val preview: List<Color> get() = tokensFor(this)?.let { listOf(it.bg, it.now, it.accent) } ?: emptyList()

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Midnight
    }
}

private fun c(v: Long) = Color(v)

private fun dark(
    bg: Long, raised: Long, line: Long, lineStrong: Long, text: Long, text2: Long, text3: Long, block: Long,
    now: Long, accent: Long = 0xFFF2A65A, good: Long = 0xFF7FD1A8,
) = Tokens(c(bg), c(raised), c(line), c(lineStrong), c(text), c(text2), c(text3), c(block),
    c(accent), c(bg), c(now), c(good), c(0xFFF2C77A), c(0xFFFF9C8A), true)

private fun light(
    bg: Long, line: Long, lineStrong: Long, text: Long, text2: Long, text3: Long, block: Long,
    now: Long, accent: Long,
) = Tokens(c(bg), Color.White, c(line), c(lineStrong), c(text), c(text2), c(text3), c(block),
    c(accent), Color.White, c(now), c(0xFF1E8A4A), c(0xFFA86F00), c(0xFFC62828), false)

val midnight = dark(0xFF0F1012, 0xFF17181B, 0xFF1E1F23, 0xFF26272B, 0xFFECE9E2, 0xFF9A968E, 0xFF8A867E, 0xFF3A3C42, 0xFF8FB8FF)

private fun tokensFor(t: AppTheme): Tokens? = when (t) {
    AppTheme.System -> null
    AppTheme.Midnight -> midnight
    AppTheme.Black -> dark(0xFF000000, 0xFF0E0E10, 0xFF161618, 0xFF1F1F22, 0xFFEDEBE6, 0xFF9A968E, 0xFF86827B, 0xFF343438, 0xFFA8C7FA)
    AppTheme.Forest -> dark(0xFF0D1210, 0xFF141A17, 0xFF1A221E, 0xFF233029, 0xFFE6ECE8, 0xFF97A39C, 0xFF82908A, 0xFF34413A, 0xFF8FD3FF, accent = 0xFFFFC074, good = 0xFF9BE7B8)
    AppTheme.Ocean -> dark(0xFF0B1216, 0xFF111A1F, 0xFF172227, 0xFF1F2D34, 0xFFE4ECEF, 0xFF93A4AC, 0xFF7F9199, 0xFF2E3E46, 0xFF6FD6E8, accent = 0xFFFFB86B)
    AppTheme.Plum -> dark(0xFF120E15, 0xFF1A151E, 0xFF211A26, 0xFF2B2231, 0xFFECE6F0, 0xFFA196A8, 0xFF8D8294, 0xFF3D3345, 0xFFD0A8FF, accent = 0xFFFFB38A)
    AppTheme.Paper -> light(0xFFF7F8FB, 0xFFE3E6EC, 0xFFD3D8E0, 0xFF1A1C21, 0xFF5A6170, 0xFF6B7280, 0xFFCDD3DD, 0xFF2F5FD0, 0xFFB85E00)
    AppTheme.Sand -> light(0xFFFAF6F0, 0xFFECE4D8, 0xFFDDD3C4, 0xFF23201B, 0xFF6B6257, 0xFF776E62, 0xFFD9CFC0, 0xFF2E7D74, 0xFFB5541B)
    AppTheme.Mint -> light(0xFFF5FAF7, 0xFFE1EBE5, 0xFFD0DDD5, 0xFF18201B, 0xFF55635A, 0xFF66746B, 0xFFC9D8CF, 0xFF1F7A55, 0xFFB4610F)
}

/** Filled in by [SchoolDayTheme] once the bundled font has loaded. */
private var geologica by mutableStateOf<FontFamily>(FontFamily.Default)

@Composable
private fun rememberGeologica(): FontFamily = FontFamily(
    Font(Res.font.geologica, FontWeight.Normal),
    Font(Res.font.geologica, FontWeight.Medium),
    Font(Res.font.geologica, FontWeight.SemiBold),
    Font(Res.font.geologica, FontWeight.Bold),
)

/** Theme colours built from the phone's dynamic palette; null where there is none (iOS). */
@Composable
expect fun systemTokens(dark: Boolean): Tokens?

/** Text styles (Geologica everywhere, tabular digits so times line up). */
object Type {
    private val base get() = TextStyle(fontFamily = geologica, fontFeatureSettings = "tnum")
    val display get() = base.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 38.sp)
    val title get() = base.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 29.sp)
    val tab get() = base.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
    val heading get() = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val bodyStrong get() = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
    val body get() = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val small get() = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val tiny get() = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)
    val eyebrow get() = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.9.sp)
    val time get() = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun AppTheme.isDarkNow(): Boolean = dark ?: isSystemInDarkTheme()

@Composable
fun SchoolDayTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = theme.isDarkNow()
    val tokens = tokensFor(theme) ?: systemTokens(dark) ?: if (dark) midnight else (tokensFor(AppTheme.Paper) ?: midnight)
    geologica = rememberGeologica()
    // Material widgets that remain (text fields, sheets) borrow the same colours.
    val scheme = (if (tokens.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = tokens.now, onPrimary = tokens.bg, secondary = tokens.accent,
        background = tokens.bg, surface = tokens.bg, onBackground = tokens.text, onSurface = tokens.text,
        surfaceVariant = tokens.raised, onSurfaceVariant = tokens.text2,
        surfaceContainerLowest = tokens.bg, surfaceContainerLow = tokens.raised, surfaceContainer = tokens.raised,
        surfaceContainerHigh = tokens.raised, surfaceContainerHighest = tokens.lineStrong,
        outline = tokens.lineStrong, outlineVariant = tokens.line, error = tokens.removed,
    )
    val typography = Typography().let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.copy(fontFamily = geologica), bodyMedium = t.bodyMedium.copy(fontFamily = geologica),
            bodySmall = t.bodySmall.copy(fontFamily = geologica), labelLarge = t.labelLarge.copy(fontFamily = geologica),
            labelMedium = t.labelMedium.copy(fontFamily = geologica), labelSmall = t.labelSmall.copy(fontFamily = geologica),
            titleMedium = t.titleMedium.copy(fontFamily = geologica), titleLarge = t.titleLarge.copy(fontFamily = geologica),
            headlineSmall = t.headlineSmall.copy(fontFamily = geologica),
        )
    }
    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
