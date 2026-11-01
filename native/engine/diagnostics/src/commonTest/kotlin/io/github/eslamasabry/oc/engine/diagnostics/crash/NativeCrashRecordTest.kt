package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.TestTimeSource

internal const val FRAME = "io.github.eslamasabry.opencode_mobile.PhoneAgentHost.start(PhoneAgentHost.kt:42)"
internal const val UNSAFE = "https://login.example/?token=fake-secret-code /home/oc/.claude/credentials"
internal val CRASH_AT: Instant = Instant.parse("2026-10-04T10:03:00Z")

internal fun rawCrash(): Map<String, Any?> =
    mapOf(
        "timestamp" to CRASH_AT.toEpochMilliseconds(),
        "exceptionClass" to "java.lang.IllegalStateException",
        "message" to "Invalid state",
        "frames" to listOf(FRAME, "java.lang.Thread.run(Thread.java:101)"),
    )

class NativeCrashRecordTest {
    @Test
    fun `B-ANDR-036 reads the latest JVM crash`() {
        val crash = assertNotNull(NativeCrashRecord.from(rawCrash()))
        assertEquals("java.lang.IllegalStateException", crash.exceptionClass)
        assertEquals(CRASH_AT, crash.timestamp)
        assertEquals("Invalid state", crash.message)
        assertEquals(2, crash.frames.size)
        assertEquals("PhoneAgentHost.start (PhoneAgentHost.kt:42)", crash.traceAttrs["frame0"])
        // The native package prefix is accepted too.
        val native = NativeCrashRecord.from(rawCrash() + ("exceptionClass" to "io.github.eslamasabry.oc.engine.Boom"))
        assertNotNull(native)
    }

    @Test
    fun `B-ANDR-036 invalid native input fails closed without paths or sign-in data`() {
        val crash =
            assertNotNull(
                NativeCrashRecord.from(
                    rawCrash() + mapOf(
                        "message" to UNSAFE,
                        "frames" to
                            listOf(
                                FRAME, UNSAFE, "java.lang.Thread.run(/home/oc/secret.java:42)",
                                "java.lang.Thread.run(Thread.java:42)\n$UNSAFE", null, 12,
                            ),
                    ),
                ),
            )
        assertEquals(NativeCrashRecord.REDACTED, crash.message)
        assertEquals(listOf(FRAME), crash.frames)
        assertFalse("fake-secret-code" in crash.diagnosticMessage)
        assertFalse("/home/oc" in crash.diagnosticStack)
        assertFalse("login.example" in crash.traceAttrs.toString())
        for (raw in listOf<Any?>(
            null,
            false,
            rawCrash() + ("timestamp" to 0L),
            rawCrash() + ("timestamp" to Double.POSITIVE_INFINITY),
            rawCrash() + ("timestamp" to 1.5),
            rawCrash() + ("timestamp" to 253_402_300_800_000L),
            rawCrash() + ("exceptionClass" to UNSAFE),
            rawCrash() + ("exceptionClass" to "untrusted.user.Text"),
        )) {
            assertNull(NativeCrashRecord.from(raw), raw.toString())
        }
    }

    @Test
    fun `B-ANDR-036 bounded metadata never converts unknown objects to text`() {
        val crash =
            assertNotNull(
                NativeCrashRecord.from(rawCrash() + mapOf("message" to mapOf("token" to "fake-secret-code"), "frames" to List(100) { FRAME })),
            )
        assertEquals(NativeCrashRecord.REDACTED, crash.message)
        assertEquals(NativeCrashRecord.MAX_FRAMES, crash.frames.size)
        assertEquals(6, crash.traceAttrs.keys.count { it.startsWith("frame") })
    }

    @Test
    fun `B-ANDR-036 a wrapped service crash keeps the bounded safe permission cause`() {
        val serviceFrame = "android.app.ContextImpl.startForegroundService(ContextImpl.java:1824)"
        val crash =
            assertNotNull(
                NativeCrashRecord.from(
                    rawCrash() + mapOf(
                        "exceptionClass" to "java.lang.RuntimeException",
                        "message" to UNSAFE,
                        "causes" to
                            listOf(
                                mapOf("exceptionClass" to "java.lang.SecurityException", "message" to "Permission denied", "frames" to List(20) { serviceFrame }),
                                mapOf("exceptionClass" to "java.lang.IllegalStateException", "message" to UNSAFE, "frames" to listOf(UNSAFE, FRAME)),
                                mapOf("exceptionClass" to UNSAFE),
                                mapOf("exceptionClass" to "java.lang.NullPointerException", "message" to "Missing value", "frames" to listOf(FRAME)),
                            ),
                    ),
                ),
            )
        assertEquals(2, crash.causes.size)
        assertEquals(NativeCrashRecord.MAX_CAUSE_FRAMES, crash.causes.first().frames.size)
        assertEquals(NativeCrashRecord.REDACTED, crash.causes.last().message)
        assertEquals(listOf(FRAME), crash.causes.last().frames)

        val redactor = Redactor()
        val clock = TestClock()
        val diagnostics = Diagnostics(redactor, clock)
        diagnostics.record("crash.last", crash.diagnosticMessage, crash.diagnosticStack)
        val trace = PerfTrace(redactor, clock, TestTimeSource())
        trace.mark("crash.last", crash.traceAttrs)
        val entry = diagnostics.errors.value.single()
        assertTrue("Caused by java > lang > SecurityException" in entry.stack)
        assertTrue("ContextImpl. startForegroundService" in entry.stack)
        assertTrue("Permission denied" in entry.stack)
        assertFalse("fake-secret-code" in entry.stack)
        val mark = trace.spans().single()
        assertEquals("SecurityException", mark.attrs["cause0"])
        assertTrue("startForegroundService" in mark.attrs["causeFrame0"]!!)
        assertTrue("ContextImpl.java:1824" in mark.attrs["causeFrame0"]!!)
        assertFalse("/home/oc" in trace.reportText())
    }
}
