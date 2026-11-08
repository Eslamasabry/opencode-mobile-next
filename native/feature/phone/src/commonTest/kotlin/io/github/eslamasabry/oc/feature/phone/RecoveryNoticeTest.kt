package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReadiness
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneExitNotice
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** P-115: one notice per Android stop on This phone. */
class RecoveryNoticeTest {
    private fun report(readiness: LifecycleReadiness) =
        LifecycleReport(readiness = readiness, exitKind = PreviousExitKind.Killed, hadServices = true)

    private fun exit(restored: Boolean = false) =
        PhoneExitNotice(PhoneExitKind.Killed, Instant.fromEpochMilliseconds(0), recoveryAllowed = true, restored = restored)

    @Test
    fun `P-115 without an exit notice the recovery line shows as before`() {
        LifecycleReadiness.entries.forEach { readiness ->
            assertEquals(readiness, ThisPhoneUi(lifecycle = report(readiness)).recoveryNotice)
        }
        assertNull(ThisPhoneUi(lifecycle = report(LifecycleReadiness.Stopped).copy(dismissed = true)).recoveryNotice)
    }

    @Test
    fun `P-115 a recovery line that repeats the exit notice is not shown`() {
        listOf(
                LifecycleReadiness.Unchecked,
                LifecycleReadiness.Checking,
                LifecycleReadiness.Unavailable,
                LifecycleReadiness.Stopped,
                LifecycleReadiness.Unreachable,
            )
            .forEach { readiness ->
                assertNull(ThisPhoneUi(lifecycle = report(readiness), exitNotice = exit()).recoveryNotice, "$readiness")
            }
        // "OpenCode is running again." already says everything answers.
        assertNull(ThisPhoneUi(lifecycle = report(LifecycleReadiness.Ready), exitNotice = exit(restored = true)).recoveryNotice)
    }

    @Test
    fun `P-115 and B-113 a recovery line that adds something stays beside the exit notice`() {
        assertEquals(
            LifecycleReadiness.Partial,
            ThisPhoneUi(lifecycle = report(LifecycleReadiness.Partial), exitNotice = exit()).recoveryNotice,
        )
        // B-113: "answering again" beside "The app was closed" was the same news twice; the exit notice says it.
        val ready = ThisPhoneUi(lifecycle = report(LifecycleReadiness.Ready), exitNotice = exit())
        assertNull(ready.recoveryNotice)
        assertEquals(true, ready.exitSaysRunning)
        assertEquals(false, ThisPhoneUi(lifecycle = report(LifecycleReadiness.Stopped), exitNotice = exit()).exitSaysRunning)
    }
}
