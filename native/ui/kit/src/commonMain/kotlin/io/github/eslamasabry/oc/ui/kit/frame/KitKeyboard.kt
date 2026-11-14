package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A test's own answer to "is the keyboard up?" (a desktop test has no keyboard inset); null reads the window's. */
internal val LocalKitKeyboardShown: ProvidableCompositionLocal<Boolean?> = compositionLocalOf { null }

/**
 * A stand-in for the window's keyboard (a desktop test has none): the room it takes [now] (the animated inset) and the
 * room its animation is heading for ([target]). Null reads the window's.
 */
internal class KitKeyboardSource(val now: WindowInsets, val target: WindowInsets)

internal val LocalKitKeyboardSource: ProvidableCompositionLocal<KitKeyboardSource?> = compositionLocalOf { null }

/** Where the window's keyboard animation is heading (Android's `imeAnimationTarget`); the keyboard itself elsewhere. */
@Composable internal expect fun kitImeTarget(): WindowInsets

/**
 * How long the animated keyboard inset may disagree with where the keyboard really is before the kit follows the real
 * one: longer than the system's own show and hide animations (about 250–300 ms), so a normal one is never cut short.
 */
internal const val KEYBOARD_CLOSE_HOLD_MS: Long = 400L

/**
 * Review item 8 (the message box left at keyboard height) and audit 2026-11-14 tl2 (after the keyboard closed, the chat
 * stood in its keyboard-up layout for about 4 s on a loaded phone: the transcript a 200 px strip, the bottom 900 px
 * empty). The window's keyboard inset is the animated one, brought to rest only by the keyboard animation's own
 * callbacks; where they come late or never (a focusable popup took the keyboard's control, the window lost focus, a
 * busy phone), it keeps the keyboard's last height. The kit used to let the page down on its own after a hold counted
 * in frames and then a spring, so the page still waited on frames arriving.
 *
 * The keyboard's room as the kit lays pages out by it: the real inset ([kitImeTarget]: where the system says the
 * keyboard is, which it reports at once, without animation) is the truth. While the animated inset is on its way to
 * it, the page follows the animation; once the two have disagreed for [KEYBOARD_CLOSE_HOLD_MS] (a wall-clock wait, one
 * state change, no frame counting and no animation), the page takes the real inset outright, until the animated one
 * agrees again. Every kit part that keeps room for the keyboard reads this, never `WindowInsets.ime`.
 */
@Composable
internal fun kitKeyboardInsets(): WindowInsets {
    val source = LocalKitKeyboardSource.current
    val now = source?.now ?: WindowInsets.ime
    val target = source?.target ?: kitImeTarget()
    val density = LocalDensity.current
    val settle = remember { KitKeyboardSettle() }
    // Read where they are used, as derived values: composition runs again only when the real room changes or the two
    // start or stop disagreeing, never per frame of the animation.
    val real by remember(target, density) { derivedStateOf { target.getBottom(density) } }
    val apart by remember(now, target, density) { derivedStateOf { now.getBottom(density) != target.getBottom(density) } }
    LaunchedEffect(settle, real, apart) {
        settle.held = false
        if (!apart) return@LaunchedEffect
        delay(KEYBOARD_CLOSE_HOLD_MS)
        settle.held = true
    }
    return remember(now, target, settle) { KitSettledKeyboard(now, target, settle) }
}

/** The system bars and the cutout, without the keyboard (the page adds [kitKeyboardInsets] itself). */
@Composable
internal fun kitSystemInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/** Pads by the keyboard's room as the kit reads it ([kitKeyboardInsets]): the kit's `imePadding`. */
@Composable internal fun Modifier.kitKeyboardPadding(): Modifier = windowInsetsPadding(kitKeyboardInsets())

/** The kit's `imePadding` for screens: pads by the keyboard's room as the kit reads it, never the raw measurement. */
@Composable public fun Modifier.kitImePadding(): Modifier = kitKeyboardPadding()

/** The keyboard's room as the kit reads it, in dp; a caller is composed again as it moves, so read it only to size. */
@Composable
public fun kitKeyboardHeight(): Dp {
    LocalKitKeyboardShown.current?.let { shown -> if (!shown) return 0.dp }
    val keyboard = kitKeyboardInsets()
    val density = LocalDensity.current
    return with(density) { keyboard.getBottom(density).toDp() }
}

private class KitKeyboardSettle {
    /** The animated inset has disagreed with the real one for the whole hold: the page takes the real one. */
    var held by mutableStateOf(false)
}

private class KitSettledKeyboard(
    private val now: WindowInsets,
    private val target: WindowInsets,
    private val settle: KitKeyboardSettle,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0

    override fun getTop(density: Density): Int = 0

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0

    override fun getBottom(density: Density): Int =
        if (settle.held) target.getBottom(density) else now.getBottom(density)
}

/**
 * Keyboard geometry belongs to the kit; screens only decide which optional chrome to show. The keyboard's inset moves
 * every frame while it opens or closes; a caller is composed again only when the answer flips, never per frame.
 */
@Composable
public fun kitKeyboardVisible(): Boolean {
    LocalKitKeyboardShown.current?.let { return it }
    val keyboard = kitKeyboardInsets()
    val density = LocalDensity.current
    return remember(keyboard, density) { derivedStateOf { keyboard.getBottom(density) > 0 } }.value
}
