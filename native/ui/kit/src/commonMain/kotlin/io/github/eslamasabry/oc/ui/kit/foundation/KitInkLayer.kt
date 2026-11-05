package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** The ripple's opacity over the part's own ink (Dart `KitInk.alpha`). */
public const val KIT_INK_ALPHA: Float = 0.12f // F: lib/ui/kit/kit_ink_layer.dart:17

/**
 * A layer over [content] that carries the press ripple of a part, clipped to [shape].
 *
 * States: none of its own. At rest it draws nothing (a ripple only exists while a finger is down), so the parts
 * inside look exactly as they do without it; pressed, released and cancelled come from the host's press
 * (`KitPress`, `KitTappable`). The tree is the same on every platform: only [content] is composed here.
 *
 * Replaces: KitInkLayer (lib/ui/kit/kit_ink_layer.dart)
 */
@Composable
public fun KitInkLayer(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) { content() }
}
