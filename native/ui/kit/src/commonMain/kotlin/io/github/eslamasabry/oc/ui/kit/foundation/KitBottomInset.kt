package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** What the bottom of the screen and the start edge keep clear for a floating part (Dart `KitClearance`). */
@Immutable
public data class KitClearance(val bottom: Dp = 0.dp, val start: Dp = 0.dp)

private val LocalKitClearance = staticCompositionLocalOf<KitClearance?> { null }

@Composable
private fun roundToPhysicalPixel(value: Dp): Dp {
    val density = LocalDensity.current.density
    return if (density <= 0f) value else Dp((value.value * density).roundToInt() / density)
}

/**
 * The clearance a screen's floating parts keep (Dart `KitBottomInset.of`): the published one, or the system's own, the
 * larger of the navigation bar and the keyboard, rounded to a physical pixel.
 */
@Composable
public fun kitBottomInset(): KitClearance {
    return kitBottomInset(WindowInsets.navigationBars)
}

@Composable
internal fun kitBottomInset(navigation: WindowInsets): KitClearance {
    LocalKitClearance.current?.let { return it }
    val bars = navigation.asPaddingValues().calculateBottomPadding()
    val keyboard = io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardInsets().asPaddingValues().calculateBottomPadding()
    // The keyboard stands over the navigation bar, so the room is the larger of the two, not their sum.
    return KitClearance(bottom = roundToPhysicalPixel(maxOf(bars, keyboard)))
}

/** Publishes [insets] to everything under it (Dart `KitBottomInset`). Cap: wrapper */
@Composable
public fun KitBottomInset(insets: KitClearance, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitClearance provides insets, content = content)
}

/**
 * Adds [extraBottom] (and optionally a new [start]) to the clearance of the parent (Dart `KitBottomInset.add`): a part
 * that sits above another keeps the first one's room.
 */
@Composable
public fun KitBottomInsetAdd(extraBottom: Dp, start: Dp? = null, content: @Composable () -> Unit) {
    val parent = kitBottomInset()
    val bottom = roundToPhysicalPixel(parent.bottom + extraBottom)
    KitBottomInset(KitClearance(bottom = bottom, start = start ?: parent.start), content)
}
