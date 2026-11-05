package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.kitZoomAtMax
import io.github.eslamasabry.oc.ui.words.res.kitZoomAtStart
import io.github.eslamasabry.oc.ui.words.res.kitZoomIn
import io.github.eslamasabry.oc.ui.words.res.kitZoomOut
import io.github.eslamasabry.oc.ui.words.res.kitZoomReset
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
            propagateMinConstraints = true, // F: lib/ui/kit/kit_image.dart:1285-1302 (the child is laid out in the viewport's tight constraints)
            content = content,
        )
    }
}

/**
 * The one pinch, pan and zoom viewer with its floating controls (Dart `KitZoom`, lib/ui/kit/kit_image.dart:792): the
 * content fills the area at 1 ×, and a stadium of Zoom out, Reset and Zoom in floats at the bottom-end corner.
 */
@Composable
public fun KitZoomView(
    label: String,
    modifier: Modifier = Modifier,
    state: KitZoomState = rememberKitZoomState(),
    controls: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val atStart = stringResource(Res.string.kitZoomAtStart)
    val atMax = stringResource(Res.string.kitZoomAtMax)
    Box(modifier.semantics { contentDescription = label }) {
        KitZoom(state, Modifier.matchParentSize(), content)
        if (controls) {
            val colors = LocalKitColors.current
            val look = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Floating, KitShapeId.Pill, SurfaceLevel.Surface2, ownFill = colors.surface2))
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.S4.dp, bottom = Space.S4.dp) // F: lib/ui/kit/kit_image.dart:1381-1382
                    .then(look.modifier)
                    .clip(look.shape)
                    .padding(horizontal = Space.S2.dp, vertical = Space.S1.dp), // F: lib/ui/kit/kit_image.dart:1389-1392
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: lib/ui/kit/kit_image.dart:1403
            ) {
                KitIconButton(
                    KitIcons.collapse, stringResource(Res.string.kitZoomOut), state::zoomOut,
                    enabled = KitEnabled.of(state.canZoomOut, atStart),
                )
                KitIconButton(
                    KitIcons.retry, stringResource(Res.string.kitZoomReset), state::fit,
                    enabled = KitEnabled.of(state.canZoomOut, atStart),
                )
                KitIconButton(
                    KitIcons.expand, stringResource(Res.string.kitZoomIn), state::zoomIn,
                    enabled = KitEnabled.of(state.canZoomIn, atMax),
                )
            }
        }
    }
}
