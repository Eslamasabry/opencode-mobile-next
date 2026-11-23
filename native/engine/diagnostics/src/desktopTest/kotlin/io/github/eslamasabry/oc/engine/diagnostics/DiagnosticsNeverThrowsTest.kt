package io.github.eslamasabry.oc.engine.diagnostics

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-STATE-073: a crash while recording diagnostics must not break the recorder for the entries after it. */
class DiagnosticsNeverThrowsTest {
    @Test
    fun `B-STATE-073 a record listener that runs out of memory never breaks record`() {
        val d = Diagnostics(Redactor(), TestClock())
        d.onRecorded { throw OutOfMemoryError("test") }
        var caught: Throwable? = null
        try {
            d.record("test", "first")
        } catch (t: Throwable) {
            caught = t
        }
        assertNull(caught, "record threw at its listener: $caught")
        caught = null
        try {
            d.record("test", "second")
        } catch (t: Throwable) {
            caught = t
        }
        assertNull(caught, "record threw after an earlier failure: $caught")
        assertEquals("second", d.errors.value.last().message)
    }
}
