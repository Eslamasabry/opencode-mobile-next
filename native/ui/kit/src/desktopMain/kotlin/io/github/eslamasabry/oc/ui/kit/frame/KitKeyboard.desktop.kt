package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable

/** A desktop window's keyboard does not animate: where it is heading is where it is. */
@Composable internal actual fun kitImeTarget(): WindowInsets = WindowInsets.ime
