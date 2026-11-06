package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalFocusManager

/** What a hardware key is, as far as a stray press is concerned (N-55). */
public enum class StrayKey {
    /** A printable character (letters, digits, punctuation): the person is typing. */
    Typing,

    /** Backspace / Delete: erasing. Some keyboards turn an unhandled Backspace into Back. */
    Erase,

    /** Space / Enter / D-pad centre: the keys that press a focused button. */
    Press,

    /** Tab, arrows, D-pad, Page/Home/End: keyboard navigation. */
    Move,

    /** Escape / Back: the platform's own Back. */
    Back,

    /** Modifier keys, shortcuts (Ctrl/Alt/Meta), media and function keys. */
    Other,
}

/** What the shell does with a key nothing on the page used. */
public enum class StrayKeyAction {
    /** Not the shell's business: the platform keeps its meaning (focus moves, Back, shortcuts). */
    Pass,

    /** Used up here so it can never press a button or turn into Back; focus parks on the page. */
    Swallow,

    /** Used up, and the conversation's message field takes focus so the next keys land there. */
    FocusComposer,
}

/**
 * N-55 (E20-S04): hardware keys typed while no field has focus never navigate. A letter used to land focus on the
 * first control (Servers on Home, Back in a chat) and the next Space pressed it; an unhandled Backspace turned into
 * Back. Pure rules, so every case is a plain test.
 */
public object ShellKeys {
    public fun classify(key: Key, codePoint: Int, ctrl: Boolean, alt: Boolean, meta: Boolean): StrayKey =
        when {
            key == Key.Escape || key == Key.Back -> StrayKey.Back
            ctrl || alt || meta -> StrayKey.Other
            key == Key.Backspace || key == Key.Delete -> StrayKey.Erase
            key == Key.Spacebar ||
                key == Key.Enter ||
                key == Key.NumPadEnter ||
                key == Key.DirectionCenter -> StrayKey.Press
            key in moves -> StrayKey.Move
            codePoint > 0x1F && codePoint != 0x7F -> StrayKey.Typing
            else -> StrayKey.Other
        }

    /** [onChat]: the page on top is a conversation (it has a message field to type into). */
    public fun decide(kind: StrayKey, onChat: Boolean): StrayKeyAction =
        when (kind) {
            StrayKey.Typing -> if (onChat) StrayKeyAction.FocusComposer else StrayKeyAction.Swallow
            StrayKey.Erase,
            StrayKey.Press -> StrayKeyAction.Swallow
            StrayKey.Move,
            StrayKey.Back,
            StrayKey.Other -> StrayKeyAction.Pass
        }

    private val moves =
        setOf(
            Key.Tab,
            Key.DirectionUp,
            Key.DirectionDown,
            Key.DirectionLeft,
            Key.DirectionRight,
            Key.PageUp,
            Key.PageDown,
            Key.MoveHome,
            Key.MoveEnd,
        )
}

/**
 * The shell's focus parking spot (N-55): one plain focus target around every page. It holds focus whenever nothing
 * inside does, so a stray key reaches [ShellKeys] instead of the first button or the platform's Back fallback. A key
 * a focused control or field uses never gets here (a focused button still presses with Space after Tab; a field still
 * erases with Backspace). [onChat] is read when a key arrives; a conversation steers [FocusDirection.Enter] into its
 * message field.
 */
@Composable
public fun ShellKeyPark(onChat: () -> Boolean, content: @Composable () -> Unit) {
    val park = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val chat by rememberUpdatedState(onChat)
    // Re-park after focus left the tree (a field cleared its focus); asked outside the focus change itself.
    var lost by remember { mutableIntStateOf(0) }
    LaunchedEffect(lost) { park.tryPark() }
    Box(
        Modifier.focusRequester(park)
            .onFocusChanged { if (!it.hasFocus) lost++ }
            .focusTarget()
            .onKeyEvent { event -> handle(event, chat(), park, focus) }
    ) {
        content()
    }
}

private fun FocusRequester.tryPark() {
    // A window without focus (a system dialog on top) refuses; the next key or focus change parks again.
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        // Not attached yet.
    }
}

private fun handle(
    event: KeyEvent,
    onChat: Boolean,
    park: FocusRequester,
    focus: androidx.compose.ui.focus.FocusManager,
): Boolean {
    val kind =
        ShellKeys.classify(
            event.key,
            event.utf16CodePoint,
            event.isCtrlPressed,
            event.isAltPressed,
            event.isMetaPressed,
        )
    val action = ShellKeys.decide(kind, onChat)
    if (action == StrayKeyAction.Pass) return false
    // Both halves of a swallowed key are used up, so neither can become a fallback Back.
    if (event.type != KeyEventType.KeyDown) return true
    park.tryPark()
    if (action == StrayKeyAction.FocusComposer) focus.moveFocus(FocusDirection.Enter)
    return true
}
