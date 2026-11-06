package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals

/** N-55 (E20-S04): the shell's rules for a hardware key nothing on the page used. */
class ShellKeysTest {
    private fun kind(key: Key, char: Char? = null, ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
        ShellKeys.classify(key, char?.code ?: 0, ctrl, alt, meta)

    @Test
    fun `B-NAV-005 a typed letter focuses the message field in a chat and does nothing elsewhere`() {
        assertEquals(StrayKey.Typing, kind(Key.A, 'a'))
        assertEquals(StrayKey.Typing, kind(Key.One, '1'))
        assertEquals(StrayKey.Typing, kind(Key.Unknown, 'ع'), "Arabic letters are typing too")
        assertEquals(StrayKeyAction.FocusComposer, ShellKeys.decide(StrayKey.Typing, onChat = true))
        assertEquals(StrayKeyAction.Swallow, ShellKeys.decide(StrayKey.Typing, onChat = false))
    }

    @Test
    fun `B-NAV-005 Backspace and Delete never navigate, on any page`() {
        assertEquals(StrayKey.Erase, kind(Key.Backspace, '\b'))
        assertEquals(StrayKey.Erase, kind(Key.Delete, '\u007F'))
        for (onChat in listOf(true, false)) {
            assertEquals(StrayKeyAction.Swallow, ShellKeys.decide(StrayKey.Erase, onChat))
        }
    }

    @Test
    fun `B-NAV-005 a stray Space or Enter presses nothing`() {
        assertEquals(StrayKey.Press, kind(Key.Spacebar, ' '))
        assertEquals(StrayKey.Press, kind(Key.Enter, '\n'))
        assertEquals(StrayKey.Press, kind(Key.NumPadEnter))
        assertEquals(StrayKeyAction.Swallow, ShellKeys.decide(StrayKey.Press, onChat = false))
        assertEquals(StrayKeyAction.Swallow, ShellKeys.decide(StrayKey.Press, onChat = true))
    }

    @Test
    fun `B-NAV-005 keyboard navigation, Escape and shortcuts keep their platform meaning`() {
        assertEquals(StrayKey.Move, kind(Key.Tab, '\t'))
        assertEquals(StrayKey.Move, kind(Key.DirectionDown))
        assertEquals(StrayKey.Back, kind(Key.Escape))
        assertEquals(StrayKey.Other, kind(Key.C, 'c', ctrl = true))
        assertEquals(StrayKey.Other, kind(Key.Backspace, meta = true), "Meta+Backspace is the system's own Back")
        assertEquals(StrayKey.Other, kind(Key.ShiftLeft))
        for (k in listOf(StrayKey.Move, StrayKey.Back, StrayKey.Other)) {
            assertEquals(StrayKeyAction.Pass, ShellKeys.decide(k, onChat = true))
            assertEquals(StrayKeyAction.Pass, ShellKeys.decide(k, onChat = false))
        }
    }
}
