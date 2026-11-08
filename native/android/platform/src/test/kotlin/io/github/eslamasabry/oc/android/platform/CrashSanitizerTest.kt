package io.github.eslamasabry.oc.android.platform

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Plain JVM: what an uncaught-exception handler may hand Android's logger, its crash dropbox and Crashlytics. */
class CrashSanitizerTest {
    private class NoDefaultConstructor(secret: String) : IllegalStateException(secret)

    class Hostile : RuntimeException() {
        override val message: String
            get() = "token=fake-secret-code"
    }

    private fun thrown(block: () -> Unit): Throwable =
        try {
            block()
            error("did not throw")
        } catch (e: Throwable) {
            e
        }

    @Test
    fun `a copy is never a frameless cause-less wrapper`() {
        val crash = thrown { error("chat text: fake-secret") }
        val safe = CrashSanitizer.sanitize(crash)
        assertEquals<Class<*>>(IllegalStateException::class.java, safe.javaClass)
        assertNull(safe.message)
        assertTrue(safe.stackTrace.isNotEmpty())
        assertEquals(crash.stackTrace.toList(), safe.stackTrace.toList())
        assertEquals("java.lang.IllegalStateException", safe.toString())
    }

    @Test
    fun `causes and suppressed errors keep their classes and frames but no message`() {
        val root = IOException("Bearer fake-token")
        val middle = IllegalArgumentException("fake-password", root)
        val outer = RuntimeException("prompt words", middle)
        outer.addSuppressed(NullPointerException("fake-login-code"))

        val safe = CrashSanitizer.sanitize(outer)

        assertEquals<Class<*>>(RuntimeException::class.java, safe.javaClass)
        assertEquals<Class<*>>(IllegalArgumentException::class.java, safe.cause!!.javaClass)
        assertEquals<Class<*>>(IOException::class.java, safe.cause!!.cause!!.javaClass)
        assertEquals(middle.stackTrace.toList(), safe.cause!!.stackTrace.toList())
        assertEquals<Class<*>>(NullPointerException::class.java, safe.suppressed.single().javaClass)
        val printed = safe.stackTraceToString()
        for (secret in listOf("fake-token", "fake-password", "prompt words", "fake-login-code")) {
            assertFalse(secret in printed, printed)
        }
        assertTrue("Caused by: java.io.IOException" in printed, printed)
    }

    @Test
    fun `a class that cannot be rebuilt without a message prints as its own name`() {
        val crash = NoDefaultConstructor("fake-secret")
        val safe = CrashSanitizer.sanitize(crash)
        assertTrue(safe is SanitizedThrowable)
        assertNull(safe.message)
        assertEquals(NoDefaultConstructor::class.java.name, safe.toString())
        assertEquals(crash.stackTrace.toList(), safe.stackTrace.toList())

        val hostile = CrashSanitizer.sanitize(Hostile())
        assertTrue(hostile is SanitizedThrowable, "a no-argument constructor that still answers a message is refused")
        assertFalse("fake-secret" in hostile.stackTraceToString())
    }

    @Test
    fun `cycles end and sanitizing twice changes nothing`() {
        val a = IllegalStateException("a")
        val b = IllegalArgumentException("b", a)
        a.initCause(b)
        val safe = CrashSanitizer.sanitize(a)
        assertNotNull(safe.cause)
        assertSame(safe, safe.cause!!.cause)
        val named = CrashSanitizer.sanitize(NoDefaultConstructor("x"))
        assertSame(named, CrashSanitizer.sanitize(named))
    }
}
