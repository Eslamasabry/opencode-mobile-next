package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.common.pace.timerTableProblems
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plan 13 §11.2 rule 3: the module's timer table follows PB-42, and nothing ticks while the app is hidden. */
class LinuxTimersTest {
    @Test
    fun `PB-42 the Linux pages' timers pass the table rules and stop while hidden`() {
        assertEquals(emptyList(), timerTableProblems(LinuxTimers.ALL))
        LinuxTimers.ALL.forEach { spec ->
            assertEquals(null, spec.cadence.periodFor(Visibility.HiddenWatching), spec.name)
            assertEquals(null, spec.cadence.periodFor(Visibility.HiddenIdle), spec.name)
        }
    }
}
