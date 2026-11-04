package io.github.eslamasabry.oc.feature.background

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLiveFacts
import io.github.eslamasabry.oc.feature.background.presentation.LiveStatusLine
import io.github.eslamasabry.oc.feature.background.presentation.PauseFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.oc.feature.background.presentation.SwitchFailure
import io.github.eslamasabry.oc.feature.background.presentation.backgroundUi
import io.github.eslamasabry.oc.feature.background.presentation.switchFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Background page's rules as plain data (Flutter notifications_settings_screen background section). */
class BackgroundUiTest {
    private val on = BackgroundLiveFacts(supported = true, enabled = true, active = true)

    @Test
    fun `B-ANDR-012 the switch reads the person's choice and the status row only says running once Android confirms`() {
        assertEquals(LiveStatusLine.Running, backgroundUi(on).status)
        assertEquals(LiveStatusLine.Starting, backgroundUi(on.copy(active = false)).status)
        assertEquals(LiveStatusLine.Off, backgroundUi(BackgroundLiveFacts(supported = true)).status)
        assertTrue(backgroundUi(on).switchOn)
        assertTrue(backgroundUi(on).batteryRow, "the battery row shows only while the switch is on")
        assertFalse(backgroundUi(BackgroundLiveFacts(supported = true)).batteryRow)
    }

    @Test
    fun `B-ANDR-012 a busy start cannot be toggled again until it settles`() {
        assertFalse(backgroundUi(on.copy(busy = true)).switchEnabled)
        assertTrue(backgroundUi(on).switchEnabled)
    }

    @Test
    fun `B-ANDR-012 a switch that did not land says why in plain words, never Android's text`() {
        assertNull(switchFailure(true, Outcome.Ok(true)))
        assertEquals(SwitchFailure.NotEnabled, switchFailure(true, Outcome.Ok(false)))
        assertEquals(SwitchFailure.NotDisabled, switchFailure(false, Outcome.Ok(true)))
        assertEquals(
            SwitchFailure.NotificationsDenied,
            switchFailure(true, Outcome.Failed(Failure.Refused(RefusalReason.NotificationsBlocked))),
        )
        assertEquals(SwitchFailure.NotEnabled, switchFailure(true, Outcome.Failed(Failure.TimedOut())))
        assertEquals(
            SwitchFailure.Unavailable,
            switchFailure(true, Outcome.Failed(Failure.Refused(RefusalReason.BackgroundLimit))),
        )
    }

    @Test
    fun `B-ANDR-014 Android's time limit shows the notice and the status row offers Restart`() {
        val stopped = backgroundUi(BackgroundLiveFacts(supported = true, stoppedByAndroid = true))
        assertTrue(stopped.timeoutNotice)
        assertTrue(stopped.restart)
        assertEquals(LiveStatusLine.StoppedByAndroid, stopped.status)
        // Turning it back on clears that notice.
        val back = backgroundUi(on)
        assertFalse(back.timeoutNotice)
        assertFalse(back.restart)
    }

    @Test
    fun `B-ANDR-018 a pause shows with its reason, and a resume in flight replaces it with the resuming line`() {
        val pause = PauseFacts(PauseReason.TimeLimit, clock = "13:10")
        val ui = backgroundUi(BackgroundLiveFacts(supported = true, pause = pause))
        assertEquals(pause, ui.pause)
        assertFalse(ui.resuming)
        assertTrue(backgroundUi(BackgroundLiveFacts(supported = true, pause = pause, resuming = true)).resuming)
    }
}
