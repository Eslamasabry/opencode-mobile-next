package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.toSize
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitZoomLevel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * A pinch-, drag- and double-tap-zoomable area (Dart `KitZoom`, fit mode; plan 06 §7.7). The content is laid out fitted
 * to the area; zoom runs from fit (1×) to 5× fit. Scale and offset are read in the draw phase only. Pinch is never the
 * only path: pair it with [KitZoomControls] (owner rule "teach what is hidden", A11Y-5).
 *
 * States: fitted, zoomed
 * Text: none (the zoom level is a state description, "250 %")
 * Cap: Fill
 * Motion: follows the fingers; buttons jump (no animation), so Calm and Off need nothing more
 * Hidden: pinch and double tap; the visible path is [KitZoomControls]
 * Replaces: KitZoom (lib/ui/kit/kit_image.dart)
 */
@Composable
public fun KitZoom(state: KitZoomState, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val level = stringResource(Res.string.kitZoomLevel, (state.scale * 100).roundToInt().toString())
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.viewport = it.toSize() }
            .pointerInput(state) { detectTapGestures(onDoubleTap = { state.toggle(it) }) }
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ -> state.transform(centroid, pan, zoom) }
            }
            .semantics { stateDescription = level },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
