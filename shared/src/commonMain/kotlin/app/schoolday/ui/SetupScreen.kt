package app.schoolday.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type

/** First launch: your class → friends' classes → optional iCanteen login. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    val classes by vm.classes.collectAsState()
    val settings by vm.settings.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var mine by rememberSaveable { mutableStateOf<String?>(null) }
    var shown by rememberSaveable { mutableStateOf(listOf<String>()) }

    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i -> Box(Modifier.width(if (i == step) 22.dp else 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) T.accent else T.lineStrong)) }
        }
        AnimatedContent(
            step, Modifier.weight(1f), label = "setup",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { dir * it / 5 } + fadeIn(tween(220, 80))) togetherWith
                    (slideOutHorizontally(tween(320)) { -dir * it / 5 } + fadeOut(tween(120)))
            },
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
                when (s) {
                    0 -> {
                        Eyebrow(S.welcome)
                        Text(S.whichClass, style = Type.display, color = T.text, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        Text(S.whichClassSub, style = Type.body, color = T.text2)
                        Spacer(Modifier.height(24.dp))
                        ClassGrid(classes, setOfNotNull(mine)) { mine = it }
                    }
                    1 -> {
                        Eyebrow(S.friends)
                        Text(S.whoseDay, style = Type.display, color = T.text, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        Text(S.whoseDaySub, style = Type.body, color = T.text2)
                        Spacer(Modifier.height(24.dp))
                        ClassGrid(classes.filter { it.id != mine }, shown.toSet()) { id -> shown = if (id in shown) shown - id else shown + id }
                    }
                    else -> {
                        Eyebrow(S.optional)
                        Text(S.orderHere, style = Type.display, color = T.text, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        Text(S.orderHereSub, style = Type.body, color = T.text2)
                        Spacer(Modifier.height(16.dp))
                        ICanteenLogin(vm, settings.icUser, settings.hasICanteen, onDone = { vm.finishSetup(mine!!, shown) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) Text(S.back, style = Type.bodyStrong, color = T.text2, modifier = Modifier.pressable { step-- }.padding(vertical = 12.dp, horizontal = 4.dp))
            Spacer(Modifier.weight(1f))
            val canNext = when (step) { 0 -> mine != null; else -> true }
            val label = when (step) { 0, 1 -> if (step == 1 && shown.isEmpty()) S.skip else S.next; else -> if (settings.hasICanteen) S.done else S.skipForNow }
            Text(
                label, style = Type.bodyStrong, color = if (canNext) T.onAccent else T.text3,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (canNext) T.accent else T.raised)
                    .pressable(canNext) { if (step < 2) step++ else vm.finishSetup(mine!!, shown) }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}
