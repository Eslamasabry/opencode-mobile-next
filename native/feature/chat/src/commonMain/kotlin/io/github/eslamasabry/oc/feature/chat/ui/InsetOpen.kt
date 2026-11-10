package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * Whether [insets] take any room at the bottom (for the keyboard: it is open). The inset itself animates every
 * frame while the keyboard moves; only this yes/no is exposed, so a reader recomposes when it flips, not per frame.
 */
@Composable
internal fun rememberBottomInsetOpen(insets: WindowInsets): State<Boolean> {
    val density = LocalDensity.current
    return remember(insets, density) { derivedStateOf { insets.getBottom(density) > 0 } }
}
