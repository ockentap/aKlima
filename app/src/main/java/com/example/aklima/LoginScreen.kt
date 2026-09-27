package com.example.aklima

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * ConnectLife sign-in.
 * Two routes, because a phone-side WebView can fail in ways that are invisible on a black-box device:
 *  1. the in-app WebView (normal path) — the login page is a Gigya-backed SPA, and the app reads the
 *     OAuth code off the redirect to homeassistant.local, which never resolves;
 *  2. a fallback — open the same URL in the phone's own browser and paste the URL you land on back
 *     into the app. Same code, same result, no WebView involved.
 *
 * The status line keeps the last URL / console line / load error on screen so a screenshot is enough
 * to diagnose a blank page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onCode: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("starting…") }
    var retryToken by remember { mutableStateOf(0) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }

    val webView = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/126.0.0.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val code = Cl.codeFromRedirect(url)
                    if (code != null || request.url.host == "homeassistant.local") {
                        if (code != null) {
                            status = "got code, finishing sign-in…"
                            onCode(code)
                        }
                        return true
                    }
                    status = "→ $url"
                    return false
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    status = "loading $url"
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    status = "loaded ${url?.take(140)}"
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loading = false
                        status = "ERROR ${error.errorCode} ${error.description} @ ${request.url}"
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                    if (request.isForMainFrame) status = "HTTP ${response.statusCode} @ ${request.url}"
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    status = "console[${msg.messageLevel()}]: ${msg.message().take(170)}"
                    return true
                }

                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
                ): Boolean = false
            }
        }
    }

    LaunchedEffect(retryToken) {
        loading = true
        status = "loading ${Cl.authorizeUrl().take(70)}…"
        webView.loadUrl(Cl.authorizeUrl())
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sign in to ConnectLife", style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("aKlima webview", status))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton(onClick = { retryToken++ }) { Text("Reload") }
                    TextButton(onClick = onCancel) { Text("Close") }
                }
            }

            Text(
                status,
                Modifier.fillMaxWidth().heightIn(max = 84.dp).padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )

            Box(Modifier.weight(1f)) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { webView })
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Blank here? Sign in on your browser",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("aKlima login link", Cl.authorizeUrl()))
                    status = "login link copied — paste it into any browser (a laptop works too)"
                }) { Text("Copy link") }
                TextButton(onClick = { openInBrowser(ctx, Cl.authorizeUrl()) { status = it } }) { Text("Browser") }
                TextButton(onClick = { pasted = ""; pasteOpen = true }) { Text("Paste code") }
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
                        "After signing in the browser shows a homeassistant.local page that cannot " +
                            "load. Copy the whole address (it ends with ?code=…) and paste it here. " +
                            "The code alone works too.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("URL or code") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = pasted.trim()
                    val code = Cl.codeFromRedirect(raw)
                        ?: raw.takeIf { it.matches(Regex("[A-Za-z0-9_-]{16,}")) }
                    pasteOpen = false
                    if (code != null) onCode(code) else status = "no code found in what was pasted"
                }) { Text("Use this") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } },
        )
    }
}

/** Browsers to try, most likely first. Chrome as a Custom Tab is the nicest; anything VIEW-able works. */
internal val BROWSER_PACKAGES = listOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
    "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.microsoft.emmx",
    "com.brave.browser", "com.opera.browser", "com.android.browser", "com.mi.globalbrowser",
)

/**
 * Hands the login URL to a real browser. Necessary for Google-linked ConnectLife accounts: Google
 * refuses OAuth inside embedded WebViews, so the sign-in has to happen in a browser — and because the
 * registered redirect URI is fixed to homeassistant.local (which never resolves), the user copies the
 * address they land on and pastes it back into the app.
 */
internal fun openInBrowser(ctx: Context, url: String, onStatus: (String) -> Unit) {
    for (pkg in BROWSER_PACKAGES) {
        if (ctx.packageManager.getLaunchIntentForPackage(pkg) == null) continue
        try {
            val intent = CustomTabsIntent.Builder().setShowTitle(true).build().intent
            intent.data = Uri.parse(url)
            intent.setPackage(pkg)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            onStatus("opened in $pkg — sign in (Google works there), then copy the address bar and tap Paste code")
            return
        } catch (e: Throwable) {
            // try the next candidate
        }
    }
    try {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(
            Intent.createChooser(view, "Open the ConnectLife login page")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        onStatus("pick a browser, sign in, then paste the address you land on")
    } catch (e: Throwable) {
        onStatus("no browser app found — tap Copy link and open it on any device")
    }
}
