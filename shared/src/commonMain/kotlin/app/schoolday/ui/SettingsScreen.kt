package app.schoolday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import app.schoolday.data.SchoolClass
import app.schoolday.ui.theme.AppTheme
import app.schoolday.ui.theme.Ic
import app.schoolday.ui.theme.T
import app.schoolday.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, showBack: Boolean, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val classes by vm.classes.collectAsState()
    val clipboard = LocalClipboardManager.current
    var picker by remember { mutableStateOf<String?>(null) } // "add" | "mine" | "theme"
    var copied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = if (showBack) 8.dp else 72.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconBtn(Ic.Back, S.back, onBack, tint = T.text)
            Text(S.settings, style = Type.title, color = T.text)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Section(S.iCanteen) { ICanteenLogin(vm, settings.icUser, settings.hasICanteen) }

            Section(S.classes) {
                settings.shownClasses.forEach { id ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.className(id), style = Type.bodyStrong, color = T.text, modifier = Modifier.weight(1f))
                        IconBtn(Ic.Close, S.remove(vm.className(id)), { vm.updateSettings { s -> s.copy(shownClasses = s.shownClasses - id) } })
                    }
                }
                Row(
                    Modifier.pressable { picker = "add" }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Ic.Plus, null, Modifier.size(18.dp), tint = T.accent)
                    Text(S.addClass, style = Type.bodyStrong, color = T.accent)
                }
                Hairline(Modifier.padding(vertical = 6.dp))
                SettingRow(S.yourClass, S.yourClassSub, onClick = { picker = "mine" }) {
                    Text(vm.className(settings.myClass), style = Type.bodyStrong, color = T.text)
                    Icon(Ic.ChevronDown, null, Modifier.padding(start = 4.dp).size(18.dp), tint = T.text2)
                }
                SettingRow(S.showMyClass) {
                    Toggle(settings.showMyClass) { v -> vm.updateSettings { it.copy(showMyClass = v) } }
                }
            }

            Section(S.app) {
                SettingRow(S.lunchNotification, S.lunchNotificationSub) {
                    Toggle(settings.lunchNotification) { v -> vm.updateSettings { it.copy(lunchNotification = v) } }
                }
                val theme = AppTheme.of(settings.theme)
                SettingRow(S.colours, S.themeName(theme.id), onClick = { picker = "theme" }) {
                    Swatch(theme)
                    Icon(Ic.ChevronDown, null, Modifier.padding(start = 6.dp).size(18.dp), tint = T.text2)
                }
                SettingRow(S.language, langLabel(settings.lang), onClick = { picker = "lang" }) {
                    Icon(Ic.ChevronDown, null, Modifier.size(18.dp), tint = T.text2)
                }
            }

            Section(S.help) {
                val update by vm.update.collectAsState()
                val appVersion = vm.versionName
                SettingRow(
                    S.checkUpdates,
                    when (update) {
                        UpdateUi.Checking -> S.checkingUpdates
                        UpdateUi.UpToDate -> S.upToDate
                        else -> S.versionLabel(appVersion)
                    },
                    onClick = { vm.checkForUpdate(manual = true) },
                )
                SettingRow(S.copyDebug, if (copied) S.copied else S.copyDebugSub, onClick = {
                    clipboard.setText(AnnotatedString(vm.debugInfo()))
                    copied = true
                }) { Icon(Ic.Copy, null, Modifier.size(20.dp), tint = T.text2) }
            }

            val version = vm.versionName
            Text(
                S.footer(version),
                style = Type.tiny.copy(fontSize = Type.small.fontSize * 0.92f, lineHeight = Type.small.lineHeight), color = T.text3,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }

    when (picker) {
        "add" -> PickerSheet(
            S.addClass, classes.filter { it.id !in settings.shownClasses && it.id != settings.myClass }, { false }, { it.name },
            onPick = { c -> vm.updateSettings { s -> s.copy(shownClasses = s.shownClasses + c.id) }; picker = null },
            onDismiss = { picker = null },
        )
        "mine" -> PickerSheet(
            S.yourClass, classes, { it.id == settings.myClass }, { it.name },
            onPick = { c -> vm.updateSettings { s -> s.copy(myClass = c.id, shownClasses = s.shownClasses - c.id) }; picker = null },
            onDismiss = { picker = null },
        )
        "theme" -> PickerSheet(
            S.colours, AppTheme.entries, { it.id == settings.theme }, { S.themeName(it.id) },
            onPick = { t -> vm.updateSettings { it.copy(theme = t.id) }; picker = null },
            onDismiss = { picker = null },
            section = { when (it.dark) { null -> null; true -> S.dark; false -> S.light } },
            leading = { Swatch(it) },
        )
        "lang" -> PickerSheet(
            S.language, listOf("system", "cs", "en"), { it == settings.lang }, { langLabel(it) },
            onPick = { l -> vm.updateSettings { it.copy(lang = l) }; picker = null },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Eyebrow(title, color = T.text3)
        Spacer(Modifier.height(4.dp))
        Hairline(strong = true)
        Column(Modifier.padding(top = 4.dp)) { content() }
    }
}

/** Three overlapping dots: background, accent, lunch. */
@Composable
fun Swatch(theme: AppTheme) {
    val colors = theme.preview.ifEmpty { listOf(T.bg, T.now, T.accent) }
    Box(Modifier.size(width = 44.dp, height = 20.dp)) {
        colors.forEachIndexed { i, c ->
            Box(Modifier.padding(start = (i * 12).dp).size(20.dp).clip(CircleShape).background(c).border(1.dp, T.lineStrong, CircleShape))
        }
    }
}

@Composable
fun ICanteenLogin(vm: AppViewModel, savedUser: String, loggedIn: Boolean, onDone: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    var user by rememberSaveable { mutableStateOf(savedUser) }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (loggedIn) {
        SettingRow(savedUser, S.loggedIn) {
            Text(S.logOut, style = Type.bodyStrong, color = T.accent, modifier = Modifier.pressable { vm.logoutICanteen(); pass = "" }.padding(8.dp))
        }
        return
    }
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(user, { user = it }, S.username)
        Field(pass, { pass = it }, S.password, password = true, show = show, onToggleShow = { show = !show })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val enabled = !busy && user.isNotBlank() && pass.isNotEmpty()
            Text(
                S.logIn, style = Type.bodyStrong, color = if (enabled) T.onAccent else T.text3,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) T.accent else T.raised)
                    .pressable(enabled) {
                        busy = true; error = null
                        scope.launch {
                            error = vm.saveICanteen(user, pass)
                            busy = false
                            if (error == null) onDone?.invoke()
                        }
                    }.padding(horizontal = 18.dp, vertical = 11.dp),
            )
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = T.accent)
            error?.let { Text(it, style = Type.small, color = T.removed, modifier = Modifier.weight(1f)) }
        }
        Text(S.savedEncrypted, style = Type.small, color = T.text3)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, show: Boolean = false, onToggleShow: (() -> Unit)? = null) {
    Column {
        Text(label, style = Type.small, color = T.text2)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value, onChange, singleLine = true, textStyle = Type.body.copy(color = T.text), cursorBrush = SolidColor(T.accent),
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            if (onToggleShow != null) IconBtn(if (show) Ic.EyeOff else Ic.Eye, if (show) S.hide else S.show, onToggleShow)
        }
        Hairline(strong = true)
    }
}

/** Big tappable class grid used in first-launch setup. */
@Composable
fun ClassGrid(classes: List<SchoolClass>, selected: Set<String>, onToggle: (String) -> Unit) {
    val rows = classes.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val sel = c.id in selected
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) T.accent else T.raised)
                            .pressable { onToggle(c.id) }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.name, style = Type.bodyStrong, color = if (sel) T.onAccent else T.text) }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private fun langLabel(l: String) = when (l) { "cs" -> "Čeština"; "en" -> "English"; else -> S.langSystem }
