package app.schoolday.ui.theme

import androidx.compose.runtime.Composable

/** iOS has no app-readable wallpaper palette; "System" just follows light/dark mode. */
@Composable
actual fun systemTokens(dark: Boolean): Tokens? = null
