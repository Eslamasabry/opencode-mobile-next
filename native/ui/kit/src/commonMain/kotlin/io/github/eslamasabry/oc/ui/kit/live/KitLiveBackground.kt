package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The ground of a page (Dart `KitLiveBackground`). Flutter's goldens run with motion loops off, so every shot holds the
 * flat ground and the page laid on it; the moving looks are an opt-in setting that is off by default and are not
 * painted here. Nothing is blurred or see-through: a card, row or sheet on top keeps its own solid surface.
 *
 * States: none, it draws a ground.
 */
@Composable
public fun KitLiveBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(LocalKitColors.current.ground)) { content() }
}
