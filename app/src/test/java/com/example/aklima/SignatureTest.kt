package com.example.aklima

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The signing scheme is the part that silently breaks (server answers 401 "missing Authorization
 * header" and everything looks like a network problem). Reference vectors below were produced by the
 * independent Python implementation that talked to the live API, so this test pins the Kotlin port
 * to it.
 */
class SignatureTest {

    private val appId = Cl.CLIENT_ID
    private val secret = Cl.CLIENT_SECRET

    @Test
    fun `get signature matches the reference implementation`() {
        val date = "Fri, 24 Jan 2025 06:50:51 GMT"
        val path = "/clife-svc/pu/get_device_status_list?timeStamp=1737702651000&version=8.1"
        val base = Cl.signatureBase(appId, "GET", path, date)
        assertEquals(
            "$appId\nGET $path\ndate: $date\nhi-params-encrypt: $appId\n",
            base
        )
        assertEquals("76k0EU8aqD7R+bHUZp2u+e2I5uL4AAphoyvcKW7kx3U=", Cl.hmacSha256Base64(secret, base))
    }

    @Test
    fun `post signature matches the reference implementation`() {
        val date = "Fri, 24 Jan 2025 06:50:51 GMT"
        val base = Cl.signatureBase(appId, "post", "/device/pu/property/set", date)
        assertEquals(
            "$appId\nPOST /device/pu/property/set\ndate: $date\nhi-params-encrypt: $appId\n",
            base
        )
        assertEquals("mO75/BbeRsS2s2CWu7RqlrCVgr2BfAojO7t4Zxowerk=", Cl.hmacSha256Base64(secret, base))
    }

    @Test
    fun `empty body digest is the constant the API expects`() {
        assertEquals(
            "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=",
            Cl.sha256Base64("")
        )
    }

    @Test
    fun `authorize url carries the registered redirect uri`() {
        val url = Cl.authorizeUrl()
        assertEquals(true, url.startsWith("https://oauth.hijuconn.com/login?client_id=$appId"))
        assertEquals(true, url.contains("response_type=code"))
        assertEquals(true, url.contains("redirect_uri=http%3A%2F%2Fhomeassistant.local%3A8123%2Fauth%2Fexternal%2Fcallback"))
    }

    @Test
    fun `code is extracted from the redirect url`() {
        assertEquals(
            "b39287d378806c43fc9b31623476023b",
            Cl.codeFromRedirect("http://homeassistant.local:8123/auth/external/callback?code=b39287d378806c43fc9b31623476023b&state=")
        )
        assertEquals(null, Cl.codeFromRedirect("https://example.com/nothing"))
        assertEquals(null, Cl.codeFromRedirect(null))
    }

    @Test
    fun `device json is parsed into status flags`() {
        val json = org.json.JSONObject(
            """
            {"puid":"pu00009865005100020003000300000161c934129510","deviceNickName":"Vogosca",
             "roomName":"Living Room","deviceTypeCode":"009","deviceFeatureCode":"104",
             "statusList":{"t_power":"1","t_temp":"24","f_temp_in":"26","t_work_mode":"1",
                           "t_fan_speed":"8","t_fan_mute":"1","t_up_down":"0"}}
            """.trimIndent()
        )
        val d = ClDevice.fromJson(json)
        assertEquals("Vogosca", d.name)
        assertEquals(true, d.power)
        assertEquals(24, d.targetTemp)
        assertEquals(26, d.roomTemp)
        assertEquals(Mode.HEAT, d.mode)
        assertEquals("High", Fan.label(d.fan))
        assertEquals(true, d.flag(Prop.QUIET))
        assertEquals(false, d.flag(Prop.SWING))
    }
}
