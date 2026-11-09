package app.schoolday.ui

import androidx.compose.runtime.Composable
import app.schoolday.ui.theme.Tokens

// iOS has no system back gesture for this overlay; the glass back button closes Settings.
@Composable
actual fun SimpleBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun ProgressBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit) {}
