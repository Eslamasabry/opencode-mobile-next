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
import androidx.compose.ui.platform.LocalFocusManager
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTypingTarget
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible

/*
 * Who types in a conversation (sweep 2026-11-11, P79/P80). The message box is the conversation's field, but it never
 * takes typing meant for another field (Find, a sheet's or a dialog's field), and it never takes typing while a sheet
 * or a dialog covers it.
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
internal fun Modifier.chatTypingLanding(typing: ComposerTypingTarget, covered: () -> Boolean): Modifier {
    // Set by the group's onEnter and read in the same focus change; every focus change of the target clears it, so a
    // field inside that asked through the group never leaves it set.
    var steered = false
    return focusProperties {
            canFocus = false
            onEnter = { steered = requestedFocusDirection == FocusDirection.Enter }
        }
        .focusTarget()
        .onFocusChanged {
            val steer = steered
            steered = false
            if (it.isFocused && steer && !covered()) typing.focus()
        }
        .focusTarget()
}

/**
 * While [open] (Find, or a sheet or dialog over the conversation), another field owns typing: if the message box
 * holds focus it lets go ([release]: a covering sheet; Find's field takes focus itself). When [open] ends, the box
 * takes focus back only if it had it when [open] began, and the person had not put the keyboard away: the box keeps
 * its focus when the keyboard is closed with Back, and taking focus again opens the keyboard, which would pop up after
 * a sheet the person opened with the keyboard down. A keyboard never seen in this conversation (a hardware keyboard,
 * a desktop) does not count as put away. [mayTakeBack] is asked at that moment: another field that opened meanwhile
 * (Find opened as a sheet closed; a sheet over Find as it closed) keeps its focus.
 */
@Composable
internal fun ComposerYields(
    open: Boolean,
    typing: ComposerTypingTarget,
    release: Boolean,
    mayTakeBack: () -> Boolean,
) {
    val focusManager = LocalFocusManager.current
    val keyboardUp = kitKeyboardVisible()
    var keyboardSeen by remember(typing) { mutableStateOf(false) }
    if (keyboardUp && !keyboardSeen) SideEffect { keyboardSeen = true }
    // Read in the composition that opens it: the box's focus is still as it was (a layout switch or a field taking
    // focus only changes it when this composition is applied).
    val hadFocus = remember(open) { open && typing.focused && (keyboardUp || !keyboardSeen) }
    var giveBack by remember(typing) { mutableStateOf(false) }
    val releases by rememberUpdatedState(release)
    val takeBack by rememberUpdatedState(mayTakeBack)
    LaunchedEffect(open, typing) {
        if (open) {
            if (hadFocus) giveBack = true
            if (releases && typing.focused) focusManager.clearFocus()
        } else if (giveBack) {
            giveBack = false
            // The closing field's focus leaves the tree first and the shell parks it on the next frame; take it after.
            repeat(SETTLE_FRAMES) { withFrameNanos {} }
            if (takeBack()) typing.focus()
        }
    }
}

/** Frames to wait after a field closes before the box takes focus back (the shell re-parks focus one frame later). */
private const val SETTLE_FRAMES = 2
