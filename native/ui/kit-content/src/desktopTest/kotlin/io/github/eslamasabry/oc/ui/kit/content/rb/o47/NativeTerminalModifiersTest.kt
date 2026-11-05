package io.github.eslamasabry.oc.ui.kit.content.rb.o47

import io.github.eslamasabry.oc.ui.kit.content.terminal.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NativeTerminalModifiersTest {
    @Test
    fun `B-NAV-040 native latch toggles once and acknowledgement sync never dispatches input`() {
        val calls = mutableListOf<KitTerminalBarKey>()
        val keys = TerminalKeyBarController(calls::add)
        keys.toggle(KitTerminalBarKey.Ctrl)
        assertEquals(listOf(KitTerminalBarKey.Ctrl), calls)
        keys.sync(ctrl = false, alt = false)
        assertFalse(keys.ctrl)
        assertEquals(listOf(KitTerminalBarKey.Ctrl), calls)
        keys.toggle(KitTerminalBarKey.Alt)
        keys.take()
        assertFalse(keys.alt)
        assertEquals(listOf(KitTerminalBarKey.Ctrl, KitTerminalBarKey.Alt), calls)
    }
}
