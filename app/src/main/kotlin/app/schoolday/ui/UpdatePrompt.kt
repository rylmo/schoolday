package app.schoolday.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type

/** The "update available" panel. Shown above everything once the startup animation is done. */
@Composable
fun UpdatePrompt(state: UpdateUi, onUpdate: () -> Unit, onLater: () -> Unit, onAllow: () -> Unit, onBrowser: () -> Unit) {
    val visible = state is UpdateUi.Available || state is UpdateUi.NeedsPermission ||
        state is UpdateUi.Downloading || state is UpdateUi.Installing || state is UpdateUi.Failed
    val canDismiss = state !is UpdateUi.Downloading && state !is UpdateUi.Installing

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = canDismiss, onClick = onLater,
                ),
            )
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(), exit = slideOutVertically(tween(220)) { it } + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(T.raised)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val version = when (state) {
                    is UpdateUi.Available -> state.info.versionName
                    is UpdateUi.NeedsPermission -> state.info.versionName
                    is UpdateUi.Downloading -> state.info.versionName
                    is UpdateUi.Installing -> state.info.versionName
                    is UpdateUi.Failed -> state.info?.versionName
                    else -> null
                }
                Eyebrow(S.updateTitle, color = T.accent)
                Text(if (version != null) "Schoolday $version" else "Schoolday", style = Type.title, color = T.text)
                when (state) {
                    is UpdateUi.Available -> Text(S.updateCloses, style = Type.body, color = T.text2)
                    is UpdateUi.NeedsPermission -> Text(S.updateAllow, style = Type.body, color = T.text2)
                    is UpdateUi.Failed -> {
                        Text(state.message, style = Type.body, color = T.removed)
                        // If installing from the app keeps failing on a phone, the APK can still be installed by hand.
                        if (state.info != null) Text(
                            S.updateBrowser, style = Type.bodyStrong, color = T.accent,
                            modifier = Modifier.pressable(onClick = onBrowser).padding(vertical = 6.dp),
                        )
                    }
                    is UpdateUi.Downloading -> ProgressLine(state.progress)
                    is UpdateUi.Installing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = T.accent)
                        Text(S.updateInstalling, style = Type.body, color = T.text2)
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(10.dp))
                if (canDismiss) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(S.updateLater, style = Type.bodyStrong, color = T.text2, modifier = Modifier.pressable(onClick = onLater).padding(horizontal = 16.dp, vertical = 12.dp))
                        Spacer(Modifier.size(8.dp))
                        val needsAllow = state is UpdateUi.NeedsPermission
                        Text(
                            if (needsAllow) S.openSettings else S.updateNow, style = Type.bodyStrong, color = T.onAccent,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(T.accent)
                                .pressable(onClick = if (needsAllow) onAllow else onUpdate).padding(horizontal = 22.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(200), label = "updateProgress")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (progress >= 0f) "${S.updateDownloading} ${(progress * 100).toInt()} %" else S.updateDownloading, style = Type.body, color = T.text2)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(T.lineStrong)) {
            Box(Modifier.fillMaxWidth(if (progress >= 0f) p else 0.3f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(T.accent))
        }
    }
}
