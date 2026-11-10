package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/** A test's own answer to "is the keyboard up?" (a desktop test has no keyboard inset); null reads the window's. */
internal val LocalKitKeyboardShown: ProvidableCompositionLocal<Boolean?> = compositionLocalOf { null }

/**
 * Keyboard geometry belongs to the kit; screens only decide which optional chrome to show. The keyboard's inset moves
 * every frame while it opens or closes; a caller is composed again only when the answer flips, never per frame.
 */
@Composable
public fun kitKeyboardVisible(): Boolean {
    LocalKitKeyboardShown.current?.let { return it }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }.value
}
