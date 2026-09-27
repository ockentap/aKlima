package com.example.aklima

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A crash on a phone far from a laptop has to be readable by the person holding the phone:
        // show the stored trace as plain text (no Compose involved) instead of crashing again.
        CrashLog.pending(this)?.let { stored ->
            showCrashScreen(stored)
            return
        }

        try {
            setContent {
                AKlimaTheme {
                    val vm: AppViewModel = viewModel()
                    Root(vm)
                }
            }
        } catch (t: Throwable) {
            CrashLog.record(this, t)
            showCrashScreen(CrashLog.pending(this) ?: t.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showCrashScreen(report: String) {
        val pad = (resources.displayMetrics.density * 16).toInt()
        val view = TextView(this).apply {
            this.text = report
            textSize = 11f
            setTextColor(0xFFE6EEF6.toInt())
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val scroller = ScrollView(this).apply {
            setBackgroundColor(0xFF0B1520.toInt())
            addView(view)
        }
        val copy = Button(this).apply {
            this.text = "Copy log"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("aKlima crash", report))
                Toast.makeText(this@MainActivity, "Copied — paste it into Telegram", Toast.LENGTH_LONG).show()
            }
        }
        val share = Button(this).apply {
            this.text = "Share log"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "aKlima crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(send, "Send crash report"))
            }
        }
        val dismiss = Button(this).apply {
            this.text = "Clear and open app"
            setOnClickListener {
                CrashLog.clear(this@MainActivity)
                recreate()
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF0B1520.toInt())
            addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(share, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(dismiss, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(column)
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf("native") }

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    if (signingIn) {
        when (route) {
            "webview" -> LoginScreen(
                onCode = { code ->
                    signingIn = false
                    route = "native"
                    vm.onAuthCode(code)
                },
                onCancel = {
                    signingIn = false
                    route = "native"
                    vm.cancelLogin()
                },
            )
            "browser" -> BrowserSignInScreen(
                onCode = { code ->
                    signingIn = false
                    route = "native"
                    vm.onAuthCode(code)
                },
                onTryInAppPage = { route = "webview" },
                onCancel = {
                    signingIn = false
                    route = "native"
                    vm.cancelLogin()
                },
            )
            else -> NativeLoginScreen(
                vm = vm,
                onUseWebInstead = { route = "browser" },
                onCancel = {
                    signingIn = false
                    route = "native"
                    vm.cancelLogin()
                },
            )
        }
        return
    }

    when (val s = state) {
        UiState.Booting -> Splash()
        UiState.NeedsLogin -> Welcome(onSignIn = { signingIn = true })
        is UiState.Ready -> Home(s, vm)
    }
}

@Composable
private fun Splash() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Welcome(onSignIn: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("aKlima", fontSize = 40.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your Hisense air conditioners, without the ConnectLife app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Sign in", fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Sign in with your ConnectLife email and password — it goes straight to ConnectLife.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Home(s: UiState.Ready, vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (s.units.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (s.error != null) {
                        Text(s.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp))
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else {
                UnitScreen(s, vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitScreen(s: UiState.Ready, vm: AppViewModel) {
    val unit = s.unit!!
    var sheet by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- header: unit switcher + overflow
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s.units.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.units.forEachIndexed { i, u ->
                        FilterChip(
                            selected = i == s.index,
                            onClick = { vm.select(i) },
                            label = { Text(u.name) },
                        )
                    }
                }
            } else {
                Column {
                    Text(unit.name, style = MaterialTheme.typography.titleMedium)
                    if (unit.room.isNotEmpty()) {
                        Text(unit.room, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { sheet = true }) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "More")
            }
        }

        Spacer(Modifier.weight(1f))

        // ---- room temperature (the fact you glance at)
        Text(
            text = "${unit.roomTemp ?: "--"}°",
            fontSize = 76.sp,
            fontWeight = FontWeight.ExtraLight,
        )
        Text(
            "in the room",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        // ---- one primary action
        IconButton(
            onClick = { vm.togglePower() },
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(
                    if (unit.power) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = if (unit.power) "Turn off" else "Turn on",
                tint = if (unit.power) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (unit.power) "${Mode.label(unit.mode)} · ${Fan.label(unit.fan)}" else "Off",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(30.dp))

        // ---- target temperature
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { vm.stepTemp(-1) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Lower")
            }
            Text(
                "${unit.targetTemp ?: 24}°",
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            OutlinedIconButton(onClick = { vm.stepTemp(1) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Raise")
            }
        }
        Text(
            "target",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        if (s.error != null) {
            Text(
                s.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (sheet) {
        MoreSheet(unit, vm, onClose = { sheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MoreSheet(unit: ClDevice, vm: AppViewModel, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiag by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {

            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Mode.HEAT, Mode.COOL, Mode.DRY, Mode.FAN, Mode.AUTO).forEach { m ->
                    FilterChip(
                        selected = unit.mode == m,
                        onClick = { vm.send(Prop.POWER to 1, Prop.MODE to m) },
                        label = { Text(Mode.label(m)) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Fan", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fan.levels.forEach { (v, label) ->
                    FilterChip(
                        selected = unit.fan == v,
                        onClick = { vm.send(Prop.FAN to v) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Divider()
            ToggleRow("Quiet", unit.flag(Prop.QUIET)) { vm.send(Prop.QUIET to if (it) 1 else 0) }
            ToggleRow("Swing", unit.flag(Prop.SWING)) { vm.send(Prop.SWING to if (it) 1 else 0) }
            ToggleRow("Eco", unit.flag(Prop.ECO)) { vm.send(Prop.ECO to if (it) 1 else 0) }
            ToggleRow("Turbo", unit.flag(Prop.TURBO)) { vm.send(Prop.POWER to 1, Prop.TURBO to if (it) 1 else 0) }
            ToggleRow("Sleep", unit.flag(Prop.SLEEP)) { vm.send(Prop.SLEEP to if (it) 1 else 0) }

            Divider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showDiag = !showDiag }) { Text("Help & diagnostics") }
                TextButton(onClick = { vm.signOut(); onClose() }) { Text("Sign out") }
            }
            if (showDiag) {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        appendLine(vm.diagnostics)
                        appendLine()
                        appendLine("raw unit state:")
                        unit.status.toSortedMap().forEach { (k, v) -> appendLine("  $k = $v") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
