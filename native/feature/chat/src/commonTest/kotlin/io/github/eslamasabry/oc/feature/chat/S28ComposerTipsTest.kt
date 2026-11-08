package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingRegistry
import io.github.eslamasabry.oc.feature.chat.ui.ComposerTipRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * S28 story 15 (Flutter `_offerSendTip`, `_queueStopTip`; owner rule "teach what is hidden"): hold Send is taught
 * while words wait and no reply runs; hold Stop once a reply has run ten seconds and the hold offers Stop after this
 * step. Both are listed in Help › Tips, so they can be replayed.
 */
class S28ComposerTipsTest {
    @Test
    fun `B-NAV-S28-01 hold Send is taught only with words waiting, no reply running and Send later offered`() {
        assertTrue(ComposerTipRules.sendTipDue(typed = true, busy = false, canSendLater = true))
        assertFalse(ComposerTipRules.sendTipDue(typed = false, busy = false, canSendLater = true))
        assertFalse(ComposerTipRules.sendTipDue(typed = true, busy = true, canSendLater = true))
        assertFalse(ComposerTipRules.sendTipDue(typed = true, busy = false, canSendLater = false))
    }

    @Test
    fun `B-NAV-S28-02 hold Stop is taught after ten seconds of a reply whose Stop offers stop after this step`() {
        assertEquals(10_000L, ComposerTipRules.STOP_TIP_AFTER_MS)
        assertTrue(ComposerTipRules.stopTipDue(busy = true, canStopAfterStep = true))
        assertFalse(ComposerTipRules.stopTipDue(busy = true, canStopAfterStep = false))
        assertFalse(ComposerTipRules.stopTipDue(busy = false, canStopAfterStep = true))
    }

    @Test
    fun `B-NAV-S28-03 both tips are in Help Tips, anchored on the controls they teach`() {
        val send = assertNotNull(TeachingRegistry.defaults.entry(TeachingIds.SendLater))
        val stop = assertNotNull(TeachingRegistry.defaults.entry(TeachingIds.StopAfterStep))
        assertEquals(listOf("chat-send-button"), send.anchors)
        assertEquals(listOf("chat-stop-button"), stop.anchors)
    }
}
