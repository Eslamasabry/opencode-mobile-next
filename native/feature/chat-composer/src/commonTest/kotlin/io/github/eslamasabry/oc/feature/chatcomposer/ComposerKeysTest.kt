package io.github.eslamasabry.oc.feature.chatcomposer

import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerKeys
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EnterKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plan 09 §10.1 keys and the after-send focus rule. */
class ComposerKeysTest {
    @Test
    fun `issue 148 a real keyboard Enter sends and Shift+Enter is a newline`() {
        assertEquals(EnterKey.Send, ComposerKeys.enter(fromSoftKeyboard = false, shift = false, ctrlOrMeta = false))
        assertEquals(EnterKey.Newline, ComposerKeys.enter(fromSoftKeyboard = false, shift = true, ctrlOrMeta = false))
    }

    @Test
    fun `issue 148 the on-screen keyboard Enter is a newline and Ctrl or Cmd+Enter always sends`() {
        assertEquals(EnterKey.Newline, ComposerKeys.enter(fromSoftKeyboard = true, shift = false, ctrlOrMeta = false))
        assertEquals(EnterKey.Send, ComposerKeys.enter(fromSoftKeyboard = true, shift = false, ctrlOrMeta = true))
        assertEquals(EnterKey.Send, ComposerKeys.enter(fromSoftKeyboard = false, shift = true, ctrlOrMeta = true))
    }

    @Test
    fun `issue 148 both composers share the kit's one Enter rule`() {
        for (soft in listOf(false, true)) for (shift in listOf(false, true)) for (ctrl in listOf(false, true)) {
            val sends = io.github.eslamasabry.oc.ui.kit.input.kitEnterSends(soft, shift, ctrl)
            assertEquals(if (sends) EnterKey.Send else EnterKey.Newline, ComposerKeys.enter(soft, shift, ctrl))
        }
    }

    @Test
    fun `P09-10_1 after a send a phone lets the keyboard go, a wide window with a hardware keyboard keeps focus`() {
        assertFalse(ComposerKeys.keepFocusAfterSend(wide = false, hardwareKeyboard = false))
        assertFalse(ComposerKeys.keepFocusAfterSend(wide = false, hardwareKeyboard = true))
        assertFalse(ComposerKeys.keepFocusAfterSend(wide = true, hardwareKeyboard = false))
        assertTrue(ComposerKeys.keepFocusAfterSend(wide = true, hardwareKeyboard = true))
    }
}
