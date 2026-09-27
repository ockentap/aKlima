package com.example.aklima

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why this exists: aKlima is installed on phones that are nowhere near a laptop, so a crash on a
 * family member's device has to be able to report itself. Any uncaught throwable (and anything the
 * Compose startup catches) is written to a file in the app's private storage; the next launch shows
 * it as plain text with a Copy button instead of crashing again.
 */
object CrashLog {

    private const val FILE = "last_crash.txt"
    private const val STALE_MS = 7L * 24 * 60 * 60 * 1000

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun record(ctx: Context, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val head = buildString {
                appendLine("aKlima crash report")
                appendLine("time   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("app    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("device : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("abi    : ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("thread : ${Thread.currentThread().name}")
                appendLine("----")
            }
            file(ctx).writeText(head + sw.toString())
        } catch (ignored: Throwable) {
            // never let reporting itself throw
        }
    }

    /** Non-null when a recent crash is stored: the text to show. */
    fun pending(ctx: Context): String? {
        val f = file(ctx)
        if (!f.exists()) return null
        return try {
            if (System.currentTimeMillis() - f.lastModified() > STALE_MS) {
                f.delete()
                null
            } else {
                f.readText()
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun clear(ctx: Context) {
        try {
            file(ctx).delete()
        } catch (ignored: Throwable) {
        }
    }
}
