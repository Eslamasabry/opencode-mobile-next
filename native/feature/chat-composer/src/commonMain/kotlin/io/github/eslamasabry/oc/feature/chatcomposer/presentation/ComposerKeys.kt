package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import io.github.eslamasabry.oc.ui.kit.input.kitEnterSends

enum class EnterKey { Send, Newline }

/** Plan 09 §10.1: the keys and the after-send focus rule, as pure decisions (the screen reads the key events). */
object ComposerKeys {
    /**
     * The kit's one Enter rule ([kitEnterSends], shared with the chat's message box): Ctrl/Cmd+Enter sends; a
     * real keyboard's Enter sends and Shift+Enter is a newline; the on-screen keyboard's Enter is a newline.
     */
    fun enter(fromSoftKeyboard: Boolean, shift: Boolean, ctrlOrMeta: Boolean): EnterKey =
        if (kitEnterSends(fromSoftKeyboard, shift, ctrlOrMeta)) EnterKey.Send else EnterKey.Newline

    /** After a send the phone lets the keyboard go; a wide window with a hardware keyboard keeps focus. */
    fun keepFocusAfterSend(wide: Boolean, hardwareKeyboard: Boolean): Boolean = wide && hardwareKeyboard
}
