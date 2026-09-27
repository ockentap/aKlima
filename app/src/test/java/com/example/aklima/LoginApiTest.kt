package com.example.aklima

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Live check of the native login path with a throwaway address: it must reach the endpoint, be
 * understood (not rejected as a malformed request) and come back with the credentials error.
 * A real sign-in obviously needs real credentials, so 600904 is the success condition here.
 */
class LoginApiTest {

    @Test
    fun `live login endpoint understands our request shape`() {
        val resp = Cl.loginWithPassword("aklima-probe-does-not-exist@example.com", "not-a-real-password")
        println("LIVE login response: $resp")
        assertEquals("request rejected instead of credentials checked", 1, resp.optInt("resultCode"))
        assertEquals(600904, resp.optInt("errorCode"))
    }

    @Test
    fun `password is rsa encrypted to 64 bytes like the web page`() {
        val enc = Cl.rsaEncryptBase64(Cl.LOGIN_RSA_PUBLIC_KEY, Cl.md5("hunter2").uppercase())
        assertEquals("RSA-512 ciphertext is 64 bytes -> 88 base64 chars", 88, enc.length)
    }
}
