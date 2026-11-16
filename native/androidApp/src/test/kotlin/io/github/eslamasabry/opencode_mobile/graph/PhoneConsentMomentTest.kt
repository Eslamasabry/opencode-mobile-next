package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Small-faults 301: when "Keep the server running?" may open. */
class PhoneConsentMomentTest {
    private fun quiet(
        top: io.github.eslamasabry.oc.ui.routes.AppRoute? = ChatsRoute.Home,
        modalOpen: Boolean = false,
        replyRunning: Boolean = false,
        keyboardUp: Boolean = false,
    ) = PhoneConsentMoment.quiet(top, modalOpen, replyRunning, keyboardUp)

    @Test
    fun `SMALL-301 it opens on the conversation list when nothing else is going on`() {
        assertTrue(quiet())
        assertTrue(quiet(top = ChatsRoute.All()))
    }

    @Test
    fun `SMALL-301 it never opens over another sheet, during a reply, over the keyboard or on another page`() {
        assertFalse(quiet(modalOpen = true))
        assertFalse(quiet(replyRunning = true))
        assertFalse(quiet(keyboardUp = true))
        assertFalse(quiet(top = AgentsRoute.Home))
        assertFalse(quiet(top = null))
    }
}
