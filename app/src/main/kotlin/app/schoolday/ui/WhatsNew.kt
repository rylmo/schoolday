package app.schoolday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.schoolday.data.ChangelogEntry
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type

/** The changes in [entries] (newest first): after an update, or the full history from Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    // Only open or closed: with a "half open" stop, scrolling down from mid-list could make the
    // sheet bounce between the two positions without settling.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = T.raised, contentColor = T.text, dragHandle = {
        Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(T.lineStrong))
    }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Eyebrow(S.whatsNew, color = T.accent)
            entries.forEach { e ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schoolday ${e.name}", style = Type.heading, color = T.text)
                    S.notes(e).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.padding(top = 9.dp).size(5.dp).clip(CircleShape).background(T.accent))
                            Text(line, style = Type.body, color = T.text2, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    S.gotIt, style = Type.bodyStrong, color = T.onAccent,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(T.accent).pressable(onClick = onDismiss)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}
