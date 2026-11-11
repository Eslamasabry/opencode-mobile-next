package io.github.eslamasabry.oc.engine.runtime.lifecycle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReturnToForegroundTest {
    private fun at(s: Long) = Instant.fromEpochSeconds(s)

    @Test
    fun aShortBlipWakesNothing() {
        val r = ReturnToForeground()
        r.left(at(100))
        assertFalse(r.returned(at(105)))
    }

    @Test
    fun aLongAbsenceWakesOnce() {
        val r = ReturnToForeground()
        r.left(at(100))
        assertTrue(r.returned(at(200)))
        assertFalse(r.returned(at(300)))
    }

    @Test
    fun repeatedPausesKeepTheFirstLeave() {
        val r = ReturnToForeground()
        r.left(at(100))
        r.left(at(190))
        assertTrue(r.returned(at(200)))
    }

    @Test
    fun aReturnWithoutALeaveIsIgnored() {
        assertFalse(ReturnToForeground().returned(at(50)))
    }
}
