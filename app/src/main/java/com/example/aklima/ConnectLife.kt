package com.example.aklima

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ConnectLife (Hisense / hiJuConn) cloud client.
 *
 * Auth  : OAuth2 authorization-code flow against oauth.hijuconn.com (Gigya-backed login page).
 * API   : juapi-3rd.hijuconn.com, every request HMAC-SHA256 signed.
 * Both schemes were reverse-engineered from Hisense's own Home Assistant plugin and verified live.
 */
object Cl {
    const val CLIENT_ID = "9793620883275788"
    const val CLIENT_SECRET = "7h1m3gZVlILyBvIFBNmzXwoFYLhkGqG9NQd2jBzuZCqJKCTyCtYwQtXi4tVBjg9B"
    const val REDIRECT_URI = "http://homeassistant.local:8123/auth/external/callback"
    const val OAUTH_AUTHORIZE = "https://oauth.hijuconn.com/login"
    const val OAUTH_TOKEN = "https://oauth.hijuconn.com/oauth/token"
    const val API_BASE = "https://juapi-3rd.hijuconn.com"
    const val API_DEVICE_LIST = "/clife-svc/pu/get_device_status_list"
    const val API_DEVICE_CONTROL = "/device/pu/property/set"

    // ---- native password login (the same endpoint the ConnectLife web login page posts to) ----
    // Verified live 2026-09-27: the endpoint checks the credentials even when `sign` is bogus or
    // absent, so the app can log in without hosting the web SPA in a WebView.
    const val LOGIN_URL = "https://oauth.hijuconn.com/oauth-web/account/acc/login_pwd"
    const val LOGIN_APP_ID = "47110567231535"
    const val LOGIN_APP_SECRET = "xO_DaHf-K3VWvXuf6CgNqHkBwjF8EOpdATmdzNEGyYD0UpnKXrEPpf_tL6Y_ULV6"
    const val LOGIN_RSA_PUBLIC_KEY =
        "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAL1pyw5RThDowxOMDeV/p5vY3f8o5hgthurwD9Ybby5OVQl3gyHLPie4j6HVmDCMypWbGt94LvpYtVW3ZDVIAc0CAwEAAQ=="

    private const val EMPTY_BODY_DIGEST = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
    private val HEADER_KEY = "hi-params-encrypt"

    private val sourceId: String by lazy {
        "td001002000" + md5(UUID.randomUUID().toString() + System.currentTimeMillis())
    }

    // ---------------------------------------------------------------- helpers

    fun authorizeUrl(): String =
        "$OAUTH_AUTHORIZE?client_id=$CLIENT_ID&response_type=code&redirect_uri=" +
            URLEncoder.encode(REDIRECT_URI, "UTF-8")

    fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun sha256Base64(s: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        )

    /** base64(HMAC-SHA256(secret, data)) — the signature value. */
    fun hmacSha256Base64(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Canonical string that gets signed:
     * appId \n "METHOD path?query" \n "date: <RFC1123 GMT>" \n "hi-params-encrypt: appId" \n
     */
    fun signatureBase(appId: String, method: String, pathWithQuery: String, gmtDate: String): String =
        "$appId\n${method.uppercase(Locale.US)} $pathWithQuery\ndate: $gmtDate\n$HEADER_KEY: $appId\n"

    fun gmtDate(t: Date = Date()): String {
        val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("GMT")
        return f.format(t)
    }

    // ------------------------------------------------------------- http core

    class ApiException(val code: Int, message: String) : Exception(message)

    private fun readAll(c: HttpURLConnection): String {
        val stream = try {
            c.inputStream
        } catch (e: Exception) {
            c.errorStream ?: return ""
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun formEncode(form: Map<String, String>): String =
        form.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }

    private fun http(
        method: String,
        url: String,
        form: Map<String, String>? = null,
        json: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = false
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val body: ByteArray? = when {
            form != null -> {
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                formEncode(form).toByteArray(Charsets.UTF_8)
            }
            json != null -> {
                conn.setRequestProperty("Content-Type", "application/json")
                json.toByteArray(Charsets.UTF_8)
            }
            else -> null
        }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val text = readAll(conn)
        conn.disconnect()
        if (code >= 400 && text.isBlank()) throw ApiException(code, "HTTP $code")
        return text
    }

    // ----------------------------------------------------------------- oauth

    /** Token endpoint. Returns the parsed JSON (access_token / refresh_token / expires_in). */
    fun tokenRequest(grant: String, value: String): JSONObject {
        val form = mutableMapOf(
            "grant_type" to grant,
            "client_id" to CLIENT_ID,
            "client_secret" to CLIENT_SECRET,
            "redirect_uri" to REDIRECT_URI,
        )
        if (grant == "authorization_code") form["code"] = value else form["refresh_token"] = value
        val text = http("POST", OAUTH_TOKEN, form = form)
        val json = JSONObject(text)
        if (json.optString("access_token").isEmpty()) {
            throw ApiException(0, json.optString("error_description").ifEmpty { text.take(200) })
        }
        return json
    }

    /** Pulls ?code= out of the redirect URL the WebView lands on (or returns null). */
    fun codeFromRedirect(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        return q.split('&').firstOrNull { it.startsWith("code=") }?.substringAfter('=')
    }

    /** RSA PKCS#1 v1.5 encryption, base64 — what the web login hands to the server as `password`. */
    fun rsaEncryptBase64(publicKeyB64: String, plain: String): String {
        val spec = java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64))
        val key = java.security.KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun randomHex(len: Int = 32): String {
        val chars = "0123456789abcdef"
        return (1..len).map { chars.random() }.joinToString("")
    }

    /**
     * Email/password sign-in. Returns the `response` object of the server answer
     * (resultCode 0 + accessToken/refreshToken, or an errorCode).
     */
    fun loginWithPassword(email: String, password: String): JSONObject {
        val ts = System.currentTimeMillis()
        val hashed = md5(password).uppercase(Locale.US)
        val body = JSONObject()
        body.put("loginName", email.trim())
        body.put("password", rsaEncryptBase64(LOGIN_RSA_PUBLIC_KEY, hashed))
        body.put("appId", LOGIN_APP_ID)
        body.put("appSecret", LOGIN_APP_SECRET)
        body.put("sourceId", randomHex())
        body.put("language", "en")
        body.put("randStr", (1_000_000..9_999_999).random())
        body.put("srcType", 1)
        body.put("timeStamp", ts)
        body.put("version", "5.0")
        body.put("accessToken", JSONObject.NULL)
        body.put("sign", "")

        val text = http(
            "POST", LOGIN_URL, json = body.toString(),
            headers = mapOf(
                "Origin" to "https://oauth.hijuconn.com",
                "Referer" to "https://oauth.hijuconn.com/login",
            ),
        )
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException(0, "unexpected login response: ${text.take(160)}")
        }
        return json.optJSONObject("response")
            ?: throw ApiException(0, "no response object in login answer")
    }

    // -------------------------------------------------------------- signed api

    private fun systemParams(token: String?): JSONObject {
        val ts = System.currentTimeMillis()
        val p = JSONObject()
        p.put("timeStamp", ts.toString())
        p.put("version", "8.1")
        p.put("languageId", "1")
        p.put("timezone", TimeZone.getDefault().id)
        p.put("randStr", md5(UUID.randomUUID().toString() + ts))
        p.put("appId", CLIENT_ID)
        p.put("sourceId", sourceId)
        p.put("platformId", 5)
        if (token != null) p.put("accessToken", token)
        return p
    }

    /**
     * One signed API call. [body] is merged with the system parameters; on GET everything goes
     * into the query string, on POST everything goes into the JSON body.
     */
    fun apiCall(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val params = systemParams(token)
        val merged = JSONObject(body?.toString() ?: "{}")
        params.keys().forEach { k -> merged.put(k, params.get(k)) }

        val payload: String?
        val pathWithQuery: String
        if (method.uppercase(Locale.US) == "GET") {
            payload = null
            val qs = merged.keys().asSequence()
                .map { URLEncoder.encode(it, "UTF-8") + "=" + URLEncoder.encode(merged.get(it).toString(), "UTF-8") }
                .joinToString("&")
            pathWithQuery = "$path?$qs"
        } else {
            payload = merged.toString()
            pathWithQuery = path
        }

        val date = gmtDate()
        val sig = hmacSha256Base64(CLIENT_SECRET, signatureBase(CLIENT_ID, method, pathWithQuery, date))
        val digest = if (payload == null) EMPTY_BODY_DIGEST else sha256Base64(payload)

        val headers = mutableMapOf(
            HEADER_KEY to CLIENT_ID,
            "Date" to date,
            "Authorization" to
                "Signature signature=\"$sig\", keyId=\"$CLIENT_ID\",algorithm=\"hmac-sha256\", " +
                "headers=\"@request-target date $HEADER_KEY\"",
            "Content-Type" to "application/json",
            "Digest" to "SHA-256=$digest",
            "User-Agent" to "aKlima/1.0",
        )
        if (token != null && method.uppercase(Locale.US) == "GET") headers["accessToken"] = token

        val text = http(method, API_BASE + pathWithQuery, json = payload, headers = headers)
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException(0, "bad response: ${text.take(200)}")
        }
    }

    fun fetchDevices(token: String): List<ClDevice> {
        val res = apiCall("GET", API_DEVICE_LIST, token = token)
        val arr = res.optJSONArray("deviceList") ?: return emptyList()
        val out = ArrayList<ClDevice>(arr.length())
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            out.add(ClDevice.fromJson(d))
        }
        return out
    }

    /** Sets t_* properties on a unit. Returns the raw cloud answer (contains commandId). */
    fun setProperties(puid: String, props: Map<String, Any>, token: String): JSONObject {
        val body = JSONObject()
        body.put("puid", puid)
        val propsJson = JSONObject()
        props.forEach { (k, v) -> propsJson.put(k, v) }
        body.put("properties", propsJson)
        return apiCall("POST", API_DEVICE_CONTROL, body = body, token = token)
    }
}

/** A ConnectLife unit plus its last-known property map (all values arrive as strings). */
data class ClDevice(
    val puid: String,
    val name: String,
    val room: String,
    val typeCode: String,
    val featureCode: String,
    val status: Map<String, String>
) {
    val power: Boolean get() = status["t_power"] == "1"
    val targetTemp: Int? get() = status["t_temp"]?.toIntOrNull()
    val roomTemp: Int? get() = status["f_temp_in"]?.toIntOrNull()
    val mode: Int get() = status["t_work_mode"]?.toIntOrNull() ?: 1
    val fan: Int get() = status["t_fan_speed"]?.toIntOrNull() ?: 0
    fun flag(key: String): Boolean = status[key] == "1"

    companion object {
        fun fromJson(d: JSONObject): ClDevice {
            val status = mutableMapOf<String, String>()
            d.optJSONObject("statusList")?.let { sl ->
                sl.keys().forEach { k -> status[k] = sl.optString(k, "") }
            }
            return ClDevice(
                puid = d.optString("puid"),
                name = d.optString("deviceNickName").ifEmpty { "Unit" },
                room = d.optString("roomName"),
                typeCode = d.optString("deviceTypeCode"),
                featureCode = d.optString("deviceFeatureCode"),
                status = status
            )
        }
    }
}

/** Property keys as used on the wire. */
object Prop {
    const val POWER = "t_power"
    const val MODE = "t_work_mode"
    const val TEMP = "t_temp"
    const val FAN = "t_fan_speed"
    const val QUIET = "t_fan_mute"
    const val SWING = "t_up_down"
    const val ECO = "t_eco"
    const val TURBO = "t_super"
    const val SLEEP = "t_sleep"
}

object Mode {
    const val FAN = 0
    const val HEAT = 1
    const val COOL = 2
    const val DRY = 3
    const val AUTO = 4
    fun label(v: Int) = when (v) {
        FAN -> "Fan"; HEAT -> "Heat"; COOL -> "Cool"; DRY -> "Dry"; AUTO -> "Auto"; else -> "?"
    }
}

object Fan {
    val levels = listOf(0 to "Auto", 5 to "Super low", 6 to "Low", 7 to "Mid", 8 to "High", 9 to "Turbo")
    fun label(v: Int) = levels.firstOrNull { it.first == v }?.second ?: "Auto"
}
