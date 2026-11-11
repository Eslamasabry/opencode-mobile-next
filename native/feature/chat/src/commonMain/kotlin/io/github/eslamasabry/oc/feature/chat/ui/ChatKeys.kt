package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.ui.input.key.Key

/** What a hardware key does inside a conversation (F: chat_page.dart `_withChatShortcuts`, model_shortcuts.dart, shortcuts.dart). */
internal enum class ChatKey {
    /** Ctrl/⌘ + K: the commands launcher. */
    Launcher,

    /** Ctrl/⌘ + F: find in the conversation. */
    Find,

    /** F3: next match (only while find is open). */
    FindNext,

    /** Shift + F3. */
    FindPrevious,

    /** Ctrl/⌘ + `: the terminal. */
    Terminal,

    /** Ctrl + B: move the running work to the background. */
    Background,

    /** F2: the next recent model. */
    ModelNext,

    /** Shift + F2. */
    ModelPrevious,
}

/** What the conversation is showing when the key arrives; every flag is read at press time. */
internal data class ChatKeyScene(
    /** The offline demo offers no server actions. */
    val demo: Boolean = false,
    /** A sheet or menu of this conversation is open: it gets the keys, the page behind stays quiet. */
    val overlayOpen: Boolean = false,
    val findOpen: Boolean = false,
    /** The connection can open a terminal (capability, never the flavour). */
    val terminal: Boolean = false,
    /** Work is running that the server can background. */
    val canBackground: Boolean = false,
)

/**
 * The conversation's keys as pure rules, so every case is a plain test. Letters and the backquote need Ctrl or ⌘, F2 and
 * F3 are function keys: none of them types, so a focused message field keeps every plain character.
 */
internal object ChatKeys {
    fun of(key: Key, ctrl: Boolean, meta: Boolean, alt: Boolean, shift: Boolean): ChatKey? {
        if (alt) return null
        val accel = (ctrl || meta) && !shift
        return when {
            key == Key.F2 && !ctrl && !meta -> if (shift) ChatKey.ModelPrevious else ChatKey.ModelNext
            key == Key.F3 && !ctrl && !meta -> if (shift) ChatKey.FindPrevious else ChatKey.FindNext
            key == Key.K && accel -> ChatKey.Launcher
            key == Key.F && accel -> ChatKey.Find
            key == Key.Grave && accel -> ChatKey.Terminal
            key == Key.B && accel -> ChatKey.Background
            else -> null
        }
    }

    /** Is [key] on offer in [scene]? An unavailable key is left alone (it is not used up). */
    fun available(key: ChatKey, scene: ChatKeyScene): Boolean {
        if (scene.demo || scene.overlayOpen) return false
        return when (key) {
            ChatKey.Launcher,
            ChatKey.Find,
            ChatKey.ModelNext,
            ChatKey.ModelPrevious -> true
            ChatKey.FindNext,
            ChatKey.FindPrevious -> scene.findOpen
            ChatKey.Terminal -> scene.terminal
            ChatKey.Background -> scene.canBackground
        }
    }
}
