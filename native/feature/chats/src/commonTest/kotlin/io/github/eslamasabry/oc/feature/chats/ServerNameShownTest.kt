package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.ui.serverNameShown
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerNameShownTest {
    @Test
    fun `W2-026 the phone's saved English name reads in the person's language and other names stay`() {
        assertEquals("هذا الهاتف", serverNameShown("This phone", "هذا الهاتف"))
        assertEquals("Paseo", serverNameShown("Paseo", "هذا الهاتف"))
    }
}
