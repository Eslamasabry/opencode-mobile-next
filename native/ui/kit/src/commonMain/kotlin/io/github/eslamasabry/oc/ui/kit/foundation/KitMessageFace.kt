package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced

/**
 * The face a held message shows (F4, option C, owner 2026-11-20): its menu opens on that face, a step clear of it, and
 * the page dims around it with the face left bright. A message's actions ([kitMenuHold] with this) know the content's
 * own box ([kitMessageContent]); a visible surface inside it (the prompt's bubble) reports itself ([kitMessageFace])
 * and is the face instead. Read when the message is pressed, so it is where the message is drawn at that moment.
 */
@Stable
public class KitMessageFace {
    internal var content: FacePlace? = null
    internal var surface: FacePlace? = null

    /** The face in window px, with its outline: the reported surface, else the content's box; null before placement. */
    public fun face(): KitFocusFace? {
        val place = surface?.takeIf { it.attached } ?: content?.takeIf { it.attached } ?: return null
        val at = place.coordinates ?: return null
        return KitFocusFace(at.boundsInWindow().toIntRect(), place.shape)
    }
}

/** One placed part that may be a message's face, and its outline. */
internal class FacePlace(var shape: Shape) {
    var coordinates: LayoutCoordinates? = null
    val attached: Boolean get() = coordinates?.isAttached == true
}

/** The face of the message being composed; null outside a message's actions. */
public val LocalKitMessageFace: ProvidableCompositionLocal<KitMessageFace?> = staticCompositionLocalOf { null }

/** The message's own content box, its face (outlined by [shape]) unless a surface inside it reports one. */
@Composable
public fun Modifier.kitMessageContent(face: KitMessageFace, shape: Shape): Modifier {
    val place = remember(face) { FacePlace(shape).also { face.content = it } }
    place.shape = shape
    return this.onPlaced { place.coordinates = it }
}

/**
 * A message's visible surface (the prompt's bubble), outlined by [shape]: inside a message's actions its menu opens
 * on this surface and the dim leaves it bright. Nothing outside a message's actions.
 */
@Composable
public fun Modifier.kitMessageFace(shape: Shape): Modifier {
    val face = LocalKitMessageFace.current ?: return this
    val place = remember(face) { FacePlace(shape).also { face.surface = it } }
    place.shape = shape
    return this.onPlaced { place.coordinates = it }
}
