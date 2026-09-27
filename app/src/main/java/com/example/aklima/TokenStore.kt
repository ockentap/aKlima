package com.example.aklima

import android.content.Context
import org.json.JSONObject

/** Token storage. App-private prefs; the only secret kept is the OAuth session. */
class TokenStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("aklima_auth", Context.MODE_PRIVATE)

    fun save(json: JSONObject) {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token").ifEmpty { refreshToken ?: "" }
        val expiresIn = json.optLong("expires_in", 1440L)
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .putLong("expires_at", System.currentTimeMillis() / 1000L + expiresIn)
            .apply()
    }

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(v) = prefs.edit().putString("access_token", v).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(v) = prefs.edit().putString("refresh_token", v).apply()

    var expiresAt: Long
        get() = prefs.getLong("expires_at", 0L)
        set(v) = prefs.edit().putLong("expires_at", v).apply()

    fun signedIn(): Boolean = !refreshToken.isNullOrEmpty()

    fun clear() = prefs.edit().clear().apply()
}
