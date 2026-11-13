package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import kotlin.math.roundToInt

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
 * How long a closing keyboard may still hold the page up before the kit lets it down on its own: longer than the
 * system's own hide animation (about 250–300 ms), so a normal close is never cut short.
 */
internal const val KEYBOARD_CLOSE_HOLD_MS: Long = 400L

/**
 * Review item 8 (the message box left at keyboard height): the window's keyboard inset is the animated one, and it is
 * only brought to rest by the end of the keyboard's animation. When that end never reaches this window (a focusable
 * popup or menu took the keyboard's control mid-way, the window lost focus while it closed), the inset stays at the
 * keyboard's last height although the keyboard is gone: the page stood one keyboard high over an empty screen.
 *
 * The keyboard's room as the kit lays pages out by it: the animated inset while the keyboard is opening, open, or
 * closing normally; once the animation's target says the keyboard is gone, the kit lets the page down itself after
 * [KEYBOARD_CLOSE_HOLD_MS], whatever the animated inset still says. Every kit part that keeps room for the keyboard
 * reads this, never `WindowInsets.ime`.
 */
@Composable
internal fun kitKeyboardInsets(): WindowInsets {
    val source = LocalKitKeyboardSource.current
    val now = source?.now ?: WindowInsets.ime
    val target = source?.target ?: kitImeTarget()
    val density = LocalDensity.current
    val settle = remember { KitKeyboardSettle() }
    val closing by
        remember(now, target, density) {
            derivedStateOf { target.getBottom(density) == 0 && now.getBottom(density) > 0 }
        }
    val latestNow = rememberUpdatedState(now)
    val latestDensity = rememberUpdatedState(density)
    val spec = KitMotion.specs.spatial<Float>()
    val latestSpec = rememberUpdatedState(spec)
    LaunchedEffect(settle, closing) {
        if (!closing) {
            settle.capped = false
            return@LaunchedEffect
        }
        settle.cap.snapTo(latestNow.value.getBottom(latestDensity.value).toFloat())
        settle.capped = true
        val start = withFrameMillis { it }
        while (withFrameMillis { it } - start < KEYBOARD_CLOSE_HOLD_MS) Unit
        settle.cap.animateTo(0f, latestSpec.value)
    }
    return remember(now, settle) { KitSettledKeyboard(now, settle) }
}

/** The system bars and the cutout, without the keyboard (the page adds [kitKeyboardInsets] itself). */
@Composable
internal fun kitSystemInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/** Pads by the keyboard's room as the kit reads it ([kitKeyboardInsets]): the kit's `imePadding`. */
@Composable internal fun Modifier.kitKeyboardPadding(): Modifier = windowInsetsPadding(kitKeyboardInsets())

private class KitKeyboardSettle {
    var capped by mutableStateOf(false)
    val cap = Animatable(0f)
}

private class KitSettledKeyboard(private val now: WindowInsets, private val settle: KitKeyboardSettle) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0

    override fun getTop(density: Density): Int = 0

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0

    override fun getBottom(density: Density): Int {
        val bottom = now.getBottom(density)
        return if (settle.capped) minOf(bottom, settle.cap.value.roundToInt().coerceAtLeast(0)) else bottom
    }
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
