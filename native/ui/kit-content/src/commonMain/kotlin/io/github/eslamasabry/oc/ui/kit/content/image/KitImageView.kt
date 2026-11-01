package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space

/**
 * A decoded picture with zoom (the viewer's Image body and a gallery page; plan 10 §11.3 / §9.3). The picture is fitted
 * to the area, never upscaled past it at rest, and zooms from fit to 5 × fit. The visible zoom buttons sit under it
 * unless [showControls] is false (a host that puts them in its own bar).
 *
 * Native: takes a decoded [Painter]. The `KitImage(ref: ImageRef)` loader is `:ui:kit`'s (C10-17) and the Coil loader
 * waits for the catalog's Coil entries; a host passes `painterResource`, `BitmapPainter` or the loader's painter.
 *
 * States: fitted, zoomed
 * Text: frame (the buttons)
 * Cap: Fill
 * Motion: see [KitZoom]
 * Hidden: pinch and double tap; the buttons are the visible path
 * Replaces: KitViewer image body + KitZoom (lib/ui/kit/kit_viewer_build.dart, kit_image.dart)
 */
@Composable
public fun KitImageView(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    state: KitZoomState = rememberKitZoomState(),
    showControls: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitZoom(state, Modifier.fillMaxWidth().weight(1f)) {
            Image(painter, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (showControls) KitZoomControls(state, Modifier.fillMaxWidth())
    }
}
