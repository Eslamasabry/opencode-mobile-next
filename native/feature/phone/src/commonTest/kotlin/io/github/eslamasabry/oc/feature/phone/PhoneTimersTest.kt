package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.common.pace.timerTableProblems
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneTimers
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plan 13 §11.2 rule 3: the module's timer table follows PB-42, and nothing ticks while the app is hidden. */
class PhoneTimersTest {
    @Test
    fun `plan 13 11-2 the phone pages' timers pass the table rules and stop while hidden`() {
        assertEquals(emptyList(), timerTableProblems(PhoneTimers.ALL))
        PhoneTimers.ALL.forEach { spec ->
            assertEquals(null, spec.cadence.periodFor(Visibility.HiddenWatching), spec.name)
            assertEquals(null, spec.cadence.periodFor(Visibility.HiddenIdle), spec.name)
        }
    }
}
