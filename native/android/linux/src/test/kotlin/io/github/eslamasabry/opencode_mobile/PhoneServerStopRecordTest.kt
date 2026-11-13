package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.PhoneServerStopRecord.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who stopped the phone's OpenCode: only the person's own Stop keeps it off (owner rule 2026-11-11). */
class PhoneServerStopRecordTest {
    @Test
    fun onlyThePersonsStopIsRecordedAsTheirs() {
        assertEquals(
            mapOf(Cause.Person to true, Cause.Replace to false, Cause.Refused to false, Cause.SystemTimeout to false),
            Cause.values().associateWith { PhoneServerStopRecord.afterStop(before = false, cause = it) },
        )
    }

    @Test
    fun aStopOrRestartThePersonAskedForIsRecordedAsAskedButARefusalOrTimeoutIsNot() {
        assertEquals(
            mapOf(Cause.Person to true, Cause.Replace to true, Cause.Refused to false, Cause.SystemTimeout to false),
            Cause.values().associateWith { PhoneServerStopRecord.asked(it) },
        )
    }

    @Test
    fun anEarlierStopByThePersonStaysThroughTheAppsOwnStops() {
        Cause.values().forEach { assertTrue(it.name, PhoneServerStopRecord.afterStop(before = true, cause = it)) }
    }

    @Test
    fun nothingRecordedIsNotThePersonsStop() {
        assertFalse(PhoneServerStopRecord.stoppedByPerson(null))
        assertFalse(PhoneServerStopRecord.stoppedByPerson(false))
        assertTrue(PhoneServerStopRecord.stoppedByPerson(true))
    }

    @Test
    fun theOpeningStartIsHeldOnlyByThePersonsStopAStaleGenerationOrBeingBehindAnotherApp() {
        fun admitted(resumed: Boolean, byPerson: Boolean, current: Boolean) =
            PhoneServerStopRecord.openStartAdmitted(resumed, byPerson, current)
        assertTrue(admitted(resumed = true, byPerson = false, current = true))
        assertFalse(admitted(resumed = true, byPerson = true, current = true))
        assertFalse(admitted(resumed = true, byPerson = false, current = false))
        assertFalse(admitted(resumed = false, byPerson = false, current = true))
    }
}
