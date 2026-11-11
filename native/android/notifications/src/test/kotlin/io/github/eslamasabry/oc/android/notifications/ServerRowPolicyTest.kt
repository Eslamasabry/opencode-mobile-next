package io.github.eslamasabry.oc.android.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone server's row never claims "nothing running" while a conversation works or waits for the person. */
class ServerRowPolicyTest {
    private fun step(
        shown: Boolean,
        lent: Boolean = false,
        watch: Boolean = false,
        live: Boolean = false,
        visible: Boolean = false,
    ) = ServerRowPolicy.step(shown, lent, watch, live, visible)

    @Test
    fun `a run waiting for the person after the watch ended puts the conversations' words in the server row`() {
        assertEquals(ServerRowStep.Lend, step(shown = true))
    }

    @Test
    fun `a run in progress puts them there too, and nothing running leaves the server's own words`() {
        assertEquals(ServerRowStep.Lend, step(shown = true, lent = true))
        assertEquals(ServerRowStep.Leave, step(shown = false))
    }

    @Test
    fun `after the answer, when nothing runs or waits, the server's own row comes back once`() {
        assertEquals(ServerRowStep.Restore, step(shown = false, lent = true))
        assertEquals(ServerRowStep.Leave, step(shown = false, lent = false))
    }

    @Test
    fun `the reply watch and live mode write the row themselves, so nothing is lent beside them`() {
        assertEquals(ServerRowStep.Leave, step(shown = true, watch = true))
        assertEquals(ServerRowStep.Leave, step(shown = true, live = true, lent = true))
    }

    @Test
    fun `coming back to the app takes a lent row away again`() {
        assertEquals(ServerRowStep.Restore, step(shown = true, lent = true, visible = true))
        assertEquals(ServerRowStep.Leave, step(shown = true, visible = true))
    }
}
