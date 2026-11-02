package io.github.eslamasabry.oc.ui.kitchat.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port of Flutter `kit_mic_hold_test.dart`: tap, hold, drag-to-cancel, a drift that gives the touch up. */
class KitMicPressTest {
    private val events = mutableListOf<String>()
    private val press =
        KitMicPress(
            onHoldStart = { events += "start" },
            onHoldEnd = { events += "end:$it" },
            onArmedChanged = { events += "armed:$it" },
        )

    @Test
    fun `MEDIA-VOICE a quick touch is a tap and never starts a hold`() {
        press.down()
        assertEquals(KitMicPress.Outcome.Tap, press.up())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `MEDIA-VOICE holding starts once and letting go finishes`() {
        press.down()
        press.holdTimeReached()
        press.holdTimeReached()
        press.move(10f)
        assertEquals(KitMicPress.Outcome.HoldCommitted, press.up())
        assertEquals(listOf("start", "end:false"), events)
    }

    @Test
    fun `MEDIA-VOICE sliding 80 dp away arms the cancel, coming back disarms it`() {
        press.down()
        press.holdTimeReached()
        press.move(KitMicPress.CANCEL_DISTANCE_DP)
        press.move(20f)
        press.move(120f)
        assertEquals(KitMicPress.Outcome.HoldCancelled, press.up())
        assertEquals(listOf("start", "armed:true", "armed:false", "armed:true", "end:true"), events)
    }

    @Test
    fun `MEDIA-VOICE a drift past the slop before the hold gives the touch up`() {
        press.down()
        press.move(KitMicPress.SLOP_DP + 1f)
        press.holdTimeReached()
        assertEquals(KitMicPress.Outcome.None, press.up())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `MEDIA-VOICE the system taking the touch cancels a running hold`() {
        press.down()
        press.holdTimeReached()
        assertEquals(KitMicPress.Outcome.HoldCancelled, press.cancel())
        assertEquals(listOf("start", "end:true"), events)
    }
}
