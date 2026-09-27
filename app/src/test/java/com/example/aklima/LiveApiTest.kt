package com.example.aklima

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Live end-to-end check of the app's network layer, using the same classes the app ships.
 * Runs only when a tokens file is present (never committed): set AKLIMA_TOKEN_FILE or drop
 * a ~/.connectlife.json with {"access_token":..., "refresh_token":...}.
 */
class LiveApiTest {

    private fun tokens(): JSONObject? {
        val path = System.getenv("AKLIMA_TOKEN_FILE")
            ?: "${System.getProperty("user.home")}/.connectlife.json"
        val f = File(path)
        if (!f.isFile) return null
        return JSONObject(f.readText())
    }

    @Test
    fun `live device list contains a controllable unit`() {
        val tok = tokens()
        Assume.assumeNotNull(tok)
        var access = tok!!.optString("access_token")
        if (access.isEmpty()) access = tok.optString("refresh_token")
        val devices = Cl.fetchDevices(access)
        assertTrue("expected at least one appliance", devices.isNotEmpty())
        println("LIVE units: " + devices.joinToString { "${it.name}(${it.puid})" })
        devices.forEach { println("  status: ${it.status}") }
    }

    @Test
    fun `live no-op write is accepted`() {
        val tok = tokens()
        Assume.assumeNotNull(tok)
        val access = tok!!.optString("access_token")
        val device = Cl.fetchDevices(access).firstOrNull() ?: return
        val current = device.targetTemp ?: 24
        val res = Cl.setProperties(device.puid, mapOf(Prop.TEMP to current), access)
        println("LIVE set result: $res")
        assertTrue("cloud rejected the command: $res", res.optInt("resultCode") == 0)
    }
}
