package app.schoolday.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun SimpleBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun ProgressBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit) {
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { onProgress(it.progress) }
            onBack()
        } catch (e: CancellationException) {
            onProgress(0f)
            throw e
        }
    }
}
