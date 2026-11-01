package io.github.eslamasabry.oc.feature.chatcomposer.presentation

enum class EnterKey { Send, Newline }

/** Plan 09 §10.1: the keys and the after-send focus rule, as pure decisions (the screen reads the key events). */
object ComposerKeys {
    /** Ctrl/Cmd+Enter sends; with a fine pointer Enter sends and Shift+Enter is a newline; on touch Enter is a newline. */
    fun enter(finePointer: Boolean, shift: Boolean, ctrlOrMeta: Boolean): EnterKey =
        when {
            ctrlOrMeta -> EnterKey.Send
            finePointer && !shift -> EnterKey.Send
            else -> EnterKey.Newline
        }

    /** After a send the phone lets the keyboard go; a wide window with a hardware keyboard keeps focus. */
    fun keepFocusAfterSend(wide: Boolean, hardwareKeyboard: Boolean): Boolean = wide && hardwareKeyboard
}
