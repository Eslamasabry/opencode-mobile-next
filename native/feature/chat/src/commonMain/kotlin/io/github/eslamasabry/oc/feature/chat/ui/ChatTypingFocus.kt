package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
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
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTypingTarget
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible

/*
 * Who types in a conversation (sweep 2026-11-11, P79/P80). The message box is the conversation's field, but it never
 * takes typing meant for another field (Find, a sheet's or a dialog's field), and it never takes typing while a sheet
 * or a dialog covers it (the kit's sheet host keeps the page out of focus then).
 */

/**
 * N-55's landing spot: the conversation itself is one focus target. The shell steers a stray typed key here
 * (`moveFocus(Enter)` from its park); when this node itself takes focus FOR THAT STEER it hands it to the message box,
 * unless a sheet or a dialog covers the conversation ([covered]), where the key goes nowhere.
 *
 * It replaces a `focusProperties { onEnter }` redirect on a focus group. `onEnter` runs for every focus request that
 * enters the group while the page holds no focus, and `FocusRequester.requestFocus()` asks with
 * `FocusDirection.Enter` by default, so Find's own field (and a tap into it) was redirected to the message box. A
 * request for a node inside never stops on this target, so only the shell's steer reaches [ComposerTypingTarget].
 *
 * Keyboard travel (gate, w4-focus): Tab, Shift+Tab or an arrow that reaches the page itself (from the park, or
 * Shift+Tab from the first control) stops on it once, as on the park, and the next press goes on to the page's first
 * or last control. Handing every arrival to the message box made Tab with nothing focused land in the box (whose
 * multiline field then types a tab), and Tab and Shift+Tab each reached only one side of the box. The plain group
 * around the target says how focus arrives: its `onEnter` runs just before the target takes focus.
 */
internal fun Modifier.chatTypingLanding(
    typing: ComposerTypingTarget,
    page: ChatPageFocus,
    covered: () -> Boolean,
): Modifier {
    // Set by the group's onEnter and read in the same focus change; every focus change of the target clears it, so a
    // field inside that asked through the group never leaves it set.
    var steered = false
    return onFocusChanged { page.has = it.hasFocus }
        .focusProperties {
            canFocus = false
            onEnter = { steered = requestedFocusDirection == FocusDirection.Enter }
        }
        .focusTarget()
        .onFocusChanged {
            page.landing = it.isFocused
            val steer = steered
            steered = false
            if (it.isFocused && steer && !covered()) typing.focus()
        }
        // N-55 on the page's own stop (gate, w4-focus2): Tab with nothing focused stops here once, and the shell's park
        // steers a letter only while the park itself holds focus (a focused field keeps its letters), so a letter typed
        // on this stop would go nowhere. It is used up here and the message box takes focus, as the park's steer does.
        .onKeyEvent { event ->
            if (!page.landing || covered() || !event.isStrayLetter()) return@onKeyEvent false
            if (event.type == KeyEventType.KeyDown) typing.focus()
            true
        }
        .focusTarget()
}

/** A printable character typed with no Ctrl/Alt/Meta (the shell's `StrayKey.Typing`, without Space). */
private fun KeyEvent.isStrayLetter(): Boolean {
    if (isCtrlPressed || isAltPressed || isMetaPressed) return false
    if (key == Key.Spacebar || key == Key.Enter || key == Key.NumPadEnter || key == Key.Tab) return false
    val code = utf16CodePoint
    return code > 0x20 && code != 0x7F
}

/**
 * While [open] (Find), Find's field owns typing: it takes focus itself. When [open] ends, the message box takes focus
 * back only if it had it when [open] began, and the person had not put the keyboard away, before Find opened or while
 * it was open: the box keeps its focus when the keyboard is closed with Back, and taking focus again opens the
 * keyboard, which would pop up unasked. A keyboard never seen in this conversation (a hardware keyboard, a desktop)
 * does not count as put away. [mayTakeBack] is asked at that moment: a sheet over Find as it closed keeps the focus.
 *
 * Sheets and dialogs are the kit's: `KitSheetHost` takes the page's focus when one shows and hands it back on the
 * same terms when it hides (w4-focus2; this used to be done here too, and the two collided).
 */
@Composable
internal fun ComposerYields(open: Boolean, typing: ComposerTypingTarget, mayTakeBack: () -> Boolean) {
    val keyboardUp = kitKeyboardVisible()
    var keyboardSeen by remember(typing) { mutableStateOf(false) }
    if (keyboardUp && !keyboardSeen) SideEffect { keyboardSeen = true }
    // Read in the composition that opens it: the box's focus is still as it was (a layout switch or a field taking
    // focus only changes it when this composition is applied).
    val hadFocus = remember(open) { open && typing.focused && (keyboardUp || !keyboardSeen) }
    // Read in the composition that closes it, before the closing field's keyboard starts to go: the person may have
    // put the keyboard away while Find was open (Back), and focus on the box would bring it up again (device B11).
    val keyboardAtClose = remember(open) { !open && (keyboardUp || !keyboardSeen) }
    var giveBack by remember(typing) { mutableStateOf(false) }
    val takeBack by rememberUpdatedState(mayTakeBack)
    LaunchedEffect(open, typing) {
        if (open) {
            if (hadFocus) giveBack = true
        } else if (giveBack) {
            giveBack = false
            // The closing field's focus leaves the tree first and the shell parks it on the next frame; take it after.
            repeat(SETTLE_FRAMES) { withFrameNanos {} }
            if (keyboardAtClose && takeBack()) typing.focus()
        }
    }
}

/** Where focus is on the conversation page: anywhere in it ([has]), or on the landing spot itself ([landing]). */
internal class ChatPageFocus {
    var has: Boolean = false
    var landing: Boolean = false
}

/**
 * The kit's sheet host takes the page's focus while a sheet or dialog shows and hands it back when it has gone
 * (KitSheetHost). Its hand-back finds the element by its place in the composition; the message box can move while it
 * is covered (the conversation's layout changes once the box loses focus), and then nothing gets focus back. So when
 * [covered] ends, the box takes focus back, a few frames later, only if it had it when the cover came (with the
 * keyboard up, or none ever seen) and NOTHING on the page holds focus by then (the kit's hand-back worked, or Find or
 * a rename that opened meanwhile has it: they keep it), and [mayTakeBack] agrees.
 */
@Composable
internal fun ComposerAfterCover(
    covered: Boolean,
    typing: ComposerTypingTarget,
    page: ChatPageFocus,
    mayTakeBack: () -> Boolean,
) {
    val keyboardUp = kitKeyboardVisible()
    var keyboardSeen by remember(typing) { mutableStateOf(false) }
    if (keyboardUp && !keyboardSeen) SideEffect { keyboardSeen = true }
    val hadFocus = remember(covered) { covered && typing.focused && (keyboardUp || !keyboardSeen) }
    var giveBack by remember(typing) { mutableStateOf(false) }
    val takeBack by rememberUpdatedState(mayTakeBack)
    LaunchedEffect(covered, typing) {
        if (covered) {
            if (hadFocus) giveBack = true
        } else if (giveBack) {
            giveBack = false
            repeat(SETTLE_FRAMES) { withFrameNanos {} }
            if (!typing.focused && (!page.has || page.landing) && takeBack()) typing.focus()
        }
    }
}

/** Frames to wait after a field closes before the box takes focus back (the shell re-parks focus one frame later). */
private const val SETTLE_FRAMES = 2
