package app.schoolday.ui.theme

import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun systemTokens(dark: Boolean): Tokens? {
    val ctx = LocalContext.current
    val s = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    return Tokens(
        bg = s.surface, raised = s.surfaceContainer, line = s.surfaceContainerHigh, lineStrong = s.outlineVariant,
        text = s.onSurface, text2 = s.onSurfaceVariant, text3 = s.onSurfaceVariant.copy(alpha = 0.85f),
        block = s.surfaceContainerHighest, accent = s.tertiary, onAccent = s.onTertiary, now = s.primary,
        good = if (dark) Color(0xFF7FD1A8) else Color(0xFF1E8A4A), changed = if (dark) Color(0xFFF2C77A) else Color(0xFFA86F00),
        removed = if (dark) Color(0xFFFF9C8A) else Color(0xFFC62828), isDark = dark,
    )
}
