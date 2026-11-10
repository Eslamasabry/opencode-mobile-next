package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PrintWriter
import java.io.StringWriter

/** PRIVACY.md: a native crash report carries code locations, never message text. */
class CrashSanitizerTest {
    private fun printed(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    @Test fun aCrashWithAUrlInItsMessageReachesTheNextHandlerWithoutIt() {
        val raw = IllegalStateException(
            "Download failed: https://admin:secret@vps.example.net:4096/x?token=abc from /data/user/0/app/files/linux",
            java.net.UnknownHostException("vps.example.net"),
        )
        var seen: Throwable? = null
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, error -> seen = error }
            val dir = java.nio.file.Files.createTempDirectory("crash").toFile()
            val handler = NativeCrashStore(dir).install()
            handler.uncaughtException(Thread.currentThread(), raw)
            dir.deleteRecursively()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        val text = printed(assertNotNullThrowable(seen))
        for (secret in listOf("https://", "vps.example.net", "secret", "token=", "/data/user", "Download failed")) {
            assertFalse("$secret leaked:\n$text", text.contains(secret))
        }
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("java.net.UnknownHostException"))
        assertTrue("frames kept", text.contains("CrashSanitizerTest.aCrashWithAUrlInItsMessage"))
    }

    @Test fun framesThatAreNotPlainCodeLocationsAreDropped() {
        val raw = RuntimeException("x")
        raw.stackTrace = arrayOf(
            StackTraceElement("com.example.Good", "run", "Good.kt", 12),
            StackTraceElement("https://evil.example/x", "run", "Bad.kt", 1),
            StackTraceElement("com.example.Path", "open", "/sdcard/secret.txt", 3),
        )
        val safe = CrashSanitizer.sanitize(raw)
        assertEquals("java.lang.RuntimeException", safe.message)
        assertEquals(2, safe.stackTrace.size)
        assertEquals("Good.kt", safe.stackTrace[0].fileName)
        assertEquals(null, safe.stackTrace[1].fileName)
        assertFalse(printed(safe).contains("secret"))
    }

    @Test fun aCyclicCauseChainEnds() {
        val a = RuntimeException("a")
        val b = IllegalArgumentException("b", a)
        a.initCause(b)
        val safe = CrashSanitizer.sanitize(a)
        var depth = 0
        var c: Throwable? = safe
        while (c != null) { depth++; c = c.cause }
        assertEquals(2, depth)
    }

    private fun assertNotNullThrowable(t: Throwable?): Throwable { assertNotNull(t); return t!! }
}
