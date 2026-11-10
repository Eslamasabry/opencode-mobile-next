package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlin.math.ceil

/*
 * Frost of what lies behind a pane, drawn with the platform's common blur (Android 12+, desktop), no lens: a glass look
 * without a lens (Frosted) on the panels over the page, and the Glass look picker's previews wherever there is no lens.
 * The backdrop is recorded at [FROST_PANE_SCALE] of the pane's size and blurred there (the blur hides the lower
 * resolution), then drawn back clipped to the pane's shape, under the pane's tint, sheen and specular and its 1 dp rim
 * light, all at full resolution.
 */

/** The share of a frost pane's pixels (per side) its blurred backdrop is drawn at. */
internal const val FROST_PANE_SCALE: Float = .5f

/**
 * [backdrop] blurred by [sigmaDp] (saturation [saturation]) under [spec]'s face, shaped [shape]. Where the platform has
 * no blur, the backdrop shows unblurred under the face.
 */
@Composable
internal fun Modifier.kitFrostPane(
    spec: KitLiquidPaneSpec,
    backdrop: KitLiquidBackdrop,
    shape: CornerBasedShape,
    sigmaDp: Float,
    saturation: Float,
): Modifier {
    val platform = LocalKitPlatform.current
    val density = LocalDensity.current.density
    val k = FROST_PANE_SCALE
    // The blur runs on the reduced copy, so its radius is scaled with it.
    val blur = remember(platform, density, sigmaDp, saturation) { platform.frostEffect(FrostSpec(sigmaDp, saturation, 0f), density * k) }
    val at = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Where the pane lies in its window, as state. The frost is a picture of what is behind the pane where it is, so it
    // is drawn again when the pane is placed elsewhere: a sheet sliding in drew it once while still below the page and,
    // over a page that stood still, kept that picture of bare ground at rest (KitFrostedPanelTest, dark).
    val place = remember { mutableStateOf(Offset.Unspecified) }
    return this.onGloballyPositioned {
            at[0] = it
            val now = it.positionInWindow()
            if (now != place.value) place.value = now
        }
        .drawWithCache {
            val layer = obtainGraphicsLayer()
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val rim = spec.rimDp.dp.toPx()
            val small = IntSize(ceil(size.width * k).toInt().coerceAtLeast(1), ceil(size.height * k).toInt().coerceAtLeast(1))
            onDrawBehind {
                // Read in the draw: the pane draws again whenever its backdrop records again.
                backdrop.version()
                place.value
                val c = at[0]?.takeIf { it.isAttached }
                if (c != null) {
                    layer.renderEffect = blur
                    layer.record(small) { scale(k, k, Offset.Zero) { backdrop.draw(this, c) } }
                    clipPath(clip) { scale(1f / k, 1f / k, Offset.Zero) { drawLayer(layer) } }
                }
                clipPath(clip) {
                    drawLiquidTint(spec, shape)
                    // The rim light, wholly inside the edge (twice its width, half of it clipped away).
                    if (spec.rim.alpha > 0f && rim > 0f) {
                        drawOutline(outline, spec.rim, style = Stroke(rim * 2f), blendMode = if (spec.rimAdds) BlendMode.Plus else BlendMode.SrcOver)
                    }
                }
            }
        }
}
