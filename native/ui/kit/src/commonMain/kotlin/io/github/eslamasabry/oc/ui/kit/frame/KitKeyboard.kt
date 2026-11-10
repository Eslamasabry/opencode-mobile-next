package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity

/** A test's own answer to "is the keyboard up?" (a desktop test has no keyboard inset); null reads the window's. */
internal val LocalKitKeyboardShown: ProvidableCompositionLocal<Boolean?> = compositionLocalOf { null }

/** Keyboard geometry belongs to the kit; screens only decide which optional chrome to show. */
@Composable
public fun kitKeyboardVisible(): Boolean =
    LocalKitKeyboardShown.current ?: (WindowInsets.ime.getBottom(LocalDensity.current) > 0)
