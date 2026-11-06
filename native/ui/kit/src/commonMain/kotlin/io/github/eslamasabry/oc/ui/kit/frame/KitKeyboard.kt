package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/** Keyboard geometry belongs to the kit; screens only decide which optional chrome to show. */
@Composable
public fun kitKeyboardVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0
