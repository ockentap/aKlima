package com.example.aklima

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sign-in route that works for accounts created with Google: Google refuses to run its OAuth
 * inside an app's embedded WebView, and ConnectLife's return URL is fixed to a host that cannot
 * resolve — so the browser does the sign-in and the user pastes the address it ends on back here.
 *
 * Written for someone who has never seen the ConnectLife login page: the steps are on the screen.
 */
@Composable
fun BrowserSignInScreen(
    onCode: (String) -> Unit,
    onTryInAppPage: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("") }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sign in with your browser", style = MaterialTheme.typography.titleLarge)
            Text(
                "ConnectLife accounts made with Google cannot sign in inside another app — Google " +
                    "blocks that. So this happens once in your browser, and then aKlima keeps the " +
                    "session. It takes a minute and you never do it again on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Step(1, "Tap Open in browser below.")
            Step(2, "Sign in with Google on the ConnectLife page.")
            Step(3, "You land on a page that cannot load — that is expected, nothing is broken.")
            Step(4, "Copy that address: long-press the address bar, then Copy.")
            Step(5, "Come back here, tap Paste code, paste it, done.")

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openInBrowser(ctx, Cl.authorizeUrl()) { status = it } }) { Text("Open in browser") }
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("aKlima login link", Cl.authorizeUrl()))
                    status = "link copied — open it in any browser (a laptop works too)"
                }) { Text("Copy link") }
                TextButton(onClick = { pasted = ""; pasteOpen = true }) { Text("Paste code") }
            }

            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 100.dp),
                )
            }

            Text(
                "No browser on this phone, or it will not open? Tap Copy link, open it on a laptop, " +
                    "sign in there, then send that address to this phone (Telegram to yourself is " +
                    "fine) and paste it into Paste code.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))
            Divider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onTryInAppPage) { Text("Try the in-app page") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("Paste the address you landed on") },
            text = {
                Column {
                    Text(
                        "It starts with homeassistant.local and contains code=… . Pasting just the " +
                            "code works too.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("Address or code") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = pasted.trim()
                    val code = Cl.codeFromRedirect(raw)
                        ?: raw.takeIf { it.matches(Regex("[A-Za-z0-9_-]{16,}")) }
                    pasteOpen = false
                    if (code != null) {
                        onCode(code)
                    } else {
                        Toast.makeText(ctx, "No code found in that text", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Sign in") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
