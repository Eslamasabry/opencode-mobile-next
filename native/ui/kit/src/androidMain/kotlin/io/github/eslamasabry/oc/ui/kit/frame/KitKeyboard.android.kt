package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable

/** Updated with every insets pass, also when the animation's own end never reaches this window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun kitImeTarget(): WindowInsets = WindowInsets.imeAnimationTarget
