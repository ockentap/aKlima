package com.example.aklima

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface UiState {
    /** App just opened, nothing loaded yet. */
    data object Booting : UiState

    /** No stored ConnectLife session — one action: sign in. */
    data object NeedsLogin : UiState

    data class Ready(
        val units: List<ClDevice>,
        val index: Int = 0,
        val busy: Boolean = false,
        val error: String? = null,
    ) : UiState {
        val unit: ClDevice? get() = units.getOrNull(index)
    }
}

sealed interface LoginState {
    data object Idle : LoginState
    data object Busy : LoginState
    data class Error(val message: String) : LoginState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val tokens = TokenStore(app)
    private val _state = MutableStateFlow<UiState>(UiState.Booting)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login: StateFlow<LoginState> = _login.asStateFlow()

    /** Truth lives only here — surfaced in Help & diagnostics, never on the main screen. */
    var diagnostics: String = ""
        private set

    private var pollJob: Job? = null

    init {
        bootstrap()
    }

    // ------------------------------------------------------------------ setup

    private fun bootstrap() {
        if (!tokens.signedIn()) {
            _state.value = UiState.NeedsLogin
            return
        }
        refresh(silent = true)
    }

    /** Called with the ?code= the WebView landed on (or the user pasted). */
    fun onAuthCode(code: String) {
        viewModelScope.launch {
            _state.value = UiState.Ready(emptyList(), busy = true)
            try {
                val json = withContext(Dispatchers.IO) { Cl.tokenRequest("authorization_code", code) }
                tokens.save(json)
                load(silent = true)
            } catch (e: Exception) {
                fail("Sign-in failed: ${e.message}")
            }
        }
    }

    fun cancelLogin() {
        _login.value = LoginState.Idle
        _state.value = if (tokens.signedIn()) UiState.Ready(emptyList(), busy = true) else UiState.NeedsLogin
        if (tokens.signedIn()) refresh(silent = true)
    }

    /**
     * Email/password sign-in against ConnectLife's own login endpoint — no WebView, no browser.
     * The password is RSA-encrypted (as the web page does) and only ever goes to ConnectLife.
     */
    fun nativeLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _login.value = LoginState.Error("Enter your ConnectLife email and password")
            return
        }
        viewModelScope.launch {
            _login.value = LoginState.Busy
            try {
                val resp = withContext(Dispatchers.IO) { Cl.loginWithPassword(email, password) }
                if (resp.optInt("resultCode", 1) != 0) {
                    _login.value = LoginState.Error(loginErrorText(resp))
                    return@launch
                }
                // Preferred: exchange the returned OAuth code for a token pair (that family's
                // refresh flow is the one this app already uses everywhere else).
                val code = resp.optString("code")
                var saved = false
                if (code.isNotEmpty()) {
                    try {
                        val tok = withContext(Dispatchers.IO) { Cl.tokenRequest("authorization_code", code) }
                        tokens.save(tok)
                        saved = true
                    } catch (e: Exception) {
                        diagnostics = "code exchange failed (${e.message}); using login tokens"
                    }
                }
                if (!saved) {
                    val access = resp.optString("accessToken")
                    if (access.isEmpty()) {
                        _login.value = LoginState.Error("Signed in, but the server returned no token")
                        return@launch
                    }
                    val tok = JSONObject()
                    tok.put("access_token", access)
                    tok.put("refresh_token", resp.optString("refreshToken"))
                    tok.put("expires_in", resp.optLong("accessTokenExpiredTime", 86_400L))
                    tokens.save(tok)
                }
                _login.value = LoginState.Idle
                load(silent = true)
            } catch (e: Exception) {
                _login.value = LoginState.Error(e.message ?: "sign-in failed")
            }
        }
    }

    private fun loginErrorText(resp: JSONObject): String = when (resp.optInt("errorCode")) {
        600904 -> "Wrong email or password"
        600902 -> "No ConnectLife account uses that email"
        206001 -> "That account has not accepted the terms yet — sign in once on the website"
        else -> resp.optString("errorDesc")
            .ifEmpty { "Sign-in failed (code ${resp.optInt("errorCode")})" }
    }

    fun signOut() {
        stopPolling()
        tokens.clear()
        _state.value = UiState.NeedsLogin
    }

    // ------------------------------------------------------------------ token

    private fun ensureToken(): String {
        val at = tokens.accessToken
        val fresh = at != null && System.currentTimeMillis() / 1000L < tokens.expiresAt - 120
        if (fresh) return at!!
        val rt = tokens.refreshToken ?: throw IllegalStateException("not signed in")
        val json = Cl.tokenRequest("refresh_token", rt)
        tokens.save(json)
        return json.getString("access_token")
    }

    // ------------------------------------------------------------------- data

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch { load(silent) }
    }

    private suspend fun load(silent: Boolean) {
        val current = _state.value
        if (!silent || current !is UiState.Ready) {
            _state.value = UiState.Ready((current as? UiState.Ready)?.units ?: emptyList(), busy = true)
        } else {
            _state.value = current.copy(busy = true)
        }
        try {
            val units = withContext(Dispatchers.IO) {
                val token = ensureToken()
                val devices = Cl.fetchDevices(token)
                diagnostics = "signed in · token expires in ${tokens.expiresAt - System.currentTimeMillis() / 1000}s\n" +
                    "units: ${devices.joinToString { "${it.name}(${it.puid.takeLast(6)})" }}"
                devices
            }
            val keep = (_state.value as? UiState.Ready)?.index ?: 0
            _state.value = UiState.Ready(
                units = units,
                index = keep.coerceIn(0, (units.size - 1).coerceAtLeast(0)),
                busy = false,
                error = null,
            )
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                _state.value = UiState.NeedsLogin
            } else {
                fail(e.message ?: e.toString(), silent)
            }
        }
    }

    private fun fail(msg: String, silent: Boolean = false) {
        val current = _state.value as? UiState.Ready
        if (current == null) {
            _state.value = UiState.Ready(emptyList(), busy = false, error = msg)
        } else {
            _state.value = current.copy(busy = false, error = if (silent) current.error else msg)
        }
    }

    fun select(index: Int) {
        val s = _state.value as? UiState.Ready ?: return
        if (index in s.units.indices) _state.value = s.copy(index = index)
    }

    // ---------------------------------------------------------------- control

    fun send(vararg props: Pair<String, Any>) {
        val s = _state.value as? UiState.Ready ?: return
        val unit = s.unit ?: return
        val map = props.toMap()
        // optimistic: show the intent immediately, reconcile on the next read
        val optimistic = unit.status.toMutableMap()
        map.forEach { (k, v) -> optimistic[k] = when (v) { is Boolean -> if (v) "1" else "0"; else -> v.toString() } }
        val patched = unit.copy(status = optimistic)
        _state.value = s.copy(units = s.units.toMutableList().also { it[s.index] = patched }, busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Cl.setProperties(unit.puid, map, ensureToken())
                }
                delay(1200)         // give the cloud a moment, then read the real state back
                load(silent = true)
            } catch (e: Exception) {
                fail("Command failed: ${e.message}")
                load(silent = true)
            }
        }
    }

    fun togglePower() {
        val unit = (_state.value as? UiState.Ready)?.unit ?: return
        send(Prop.POWER to if (unit.power) 0 else 1)
    }

    fun stepTemp(delta: Int) {
        val unit = (_state.value as? UiState.Ready)?.unit ?: return
        val now = unit.targetTemp ?: 24
        val next = (now + delta).coerceIn(16, 32)
        if (next != now) send(Prop.TEMP to next)
    }

    // ---------------------------------------------------------------- polling

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                if (_state.value is UiState.Ready && (_state.value as UiState.Ready).units.isNotEmpty()) {
                    load(silent = true)
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        const val POLL_MS = 20_000L
    }
}
