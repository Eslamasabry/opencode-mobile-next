package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Clock

/**
 * 2026-11-08: the prompt editor crash reached Android's crash dropbox as "RuntimeException: Native application error"
 * with no frames and no cause, because this handler handed the chain a fresh category error. The chain must get the
 * crash's own classes and frames; no report may get its message.
 */
class DiagnosticsCrashHandlerTest {
    private fun crash(): Throwable {
        // A real thrown cause, so it carries real frames. The try's value is a String, so the cause is caught on its own.
        val cause = runCatching { listOf<String>().first { it == "password=fake-secret" } }.exceptionOrNull()
            as NoSuchElementException
        return IllegalStateException("prompt words fake-prompt", cause)
    }

    @Test
    fun `the next handler gets the crash's classes and frames, never a frameless wrapper`() {
        val diagnostics = Diagnostics(Redactor(), Clock.System)
        var forwarded: Throwable? = null
        var logged: Throwable? = null
        val original = crash()
        val handler = diagnosticsCrashHandler(diagnostics, { _, e -> forwarded = e }) { logged = it }

        handler.uncaughtException(Thread.currentThread(), original)

        val seen = forwarded!!
        assertEquals<Class<*>>(IllegalStateException::class.java, seen.javaClass)
        assertNull(seen.message)
        assertEquals(original.stackTrace.toList(), seen.stackTrace.toList())
        assertEquals<Class<*>>(NoSuchElementException::class.java, seen.cause!!.javaClass)
        assertEquals(original.cause!!.stackTrace.toList(), seen.cause!!.stackTrace.toList())
        assertFalse("fake-prompt" in seen.stackTraceToString())
        assertSame(original, logged, "this device's logcat keeps the original")
    }

    @Test
    fun `the kept report names the class and frames, never the message`() {
        val diagnostics = Diagnostics(Redactor(), Clock.System)
        diagnosticsCrashHandler(diagnostics, null).uncaughtException(Thread.currentThread(), crash())

        val kept = diagnostics.errors.value
        val text = kept.joinToString("\n") { "${it.source} ${it.message} ${it.stack}" }
        assertFalse("fake-prompt" in text, text)
        assertEquals("java.lang.IllegalStateException", kept.first().message)
        assertEquals(true, kept.first().stack.contains("DiagnosticsCrashHandlerTest"), text)
    }
}
