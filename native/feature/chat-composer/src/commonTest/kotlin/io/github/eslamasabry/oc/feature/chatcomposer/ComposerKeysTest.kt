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
    fun `P09-10_1 with a fine pointer Enter sends and Shift+Enter is a newline`() {
        assertEquals(EnterKey.Send, ComposerKeys.enter(finePointer = true, shift = false, ctrlOrMeta = false))
        assertEquals(EnterKey.Newline, ComposerKeys.enter(finePointer = true, shift = true, ctrlOrMeta = false))
    }

    @Test
    fun `P09-10_1 on touch Enter is a newline and Ctrl or Cmd+Enter always sends`() {
        assertEquals(EnterKey.Newline, ComposerKeys.enter(finePointer = false, shift = false, ctrlOrMeta = false))
        assertEquals(EnterKey.Send, ComposerKeys.enter(finePointer = false, shift = false, ctrlOrMeta = true))
        assertEquals(EnterKey.Send, ComposerKeys.enter(finePointer = true, shift = true, ctrlOrMeta = true))
    }

    @Test
    fun `P09-10_1 after a send a phone lets the keyboard go, a wide window with a hardware keyboard keeps focus`() {
        assertFalse(ComposerKeys.keepFocusAfterSend(wide = false, hardwareKeyboard = false))
        assertFalse(ComposerKeys.keepFocusAfterSend(wide = false, hardwareKeyboard = true))
        assertFalse(ComposerKeys.keepFocusAfterSend(wide = true, hardwareKeyboard = false))
        assertTrue(ComposerKeys.keepFocusAfterSend(wide = true, hardwareKeyboard = true))
    }
}
