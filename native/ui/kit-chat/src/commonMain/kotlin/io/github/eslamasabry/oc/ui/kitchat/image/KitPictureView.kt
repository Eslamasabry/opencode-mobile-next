package io.github.eslamasabry.oc.ui.kitchat.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitZoomFit
import io.github.eslamasabry.oc.ui.words.res.kitZoomIn
import io.github.eslamasabry.oc.ui.words.res.kitZoomOut
import androidx.compose.runtime.produceState
import io.github.eslamasabry.oc.ui.kit.image.KIT_VIEWER_EDGE
import io.github.eslamasabry.oc.ui.kit.image.KitImages
import io.github.eslamasabry.oc.ui.kit.image.LocalKitImageWork
import org.jetbrains.compose.resources.stringResource

/**
 * What the picture viewer shows: the bytes, still loading, or a plain line why not (the caller's
 * words).
 */
public sealed interface KitPictureContent {
    public data object Loading : KitPictureContent

    public class Bytes(public val bytes: ByteArray) : KitPictureContent

    public data object Unavailable : KitPictureContent
}

/**
 * P12-CHAT-FEEL: a picture from a reply, full width, pinch to zoom and pan, double-tap to fit again
 * (Flutter's picture viewer). Bytes that do not decode show [failedLabel], never an exception.
 * [description] is the picture's spoken name.
 */
@Composable
public fun KitPictureView(
    content: KitPictureContent,
    description: String,
    loadingLabel: String,
    failedLabel: String,
    modifier: Modifier = Modifier,
) {
    when (content) {
        KitPictureContent.Loading -> KitText(loadingLabel, modifier, tone = TextTone.Secondary)
        KitPictureContent.Unavailable -> KitText(failedLabel, modifier, tone = TextTone.Secondary)
        is KitPictureContent.Bytes -> {
            val work = LocalKitImageWork.current
            val loaded by
                produceState<Pair<Boolean, ImageBitmap?>>(false to null, content) {
                    value = true to KitImages.loadBytes(content.bytes, KIT_VIEWER_EDGE, work)
                }
            val bitmap = loaded.second
            if (!loaded.first) {
                KitText(loadingLabel, modifier, tone = TextTone.Secondary)
            } else if (bitmap == null) {
                KitText(failedLabel, modifier, tone = TextTone.Secondary)
            } else {
                ZoomableImage(bitmap, description, modifier)
            }
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: ImageBitmap, description: String, modifier: Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 120.dp, max = 640.dp)
                .clipToBounds()
                .testTag(PICTURE_VIEW_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier.fillMaxWidth()
                        .pointerInput(bitmap) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                    offset = Offset.Zero
                                }
                            )
                        }
                        .transformable(state)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
            )
        }
        FlowRow {
            KitButton(
                stringResource(Res.string.kitZoomOut),
                {
                    scale = (scale / 1.5f).coerceAtLeast(1f)
                    if (scale == 1f) offset = Offset.Zero
                },
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.kitZoomFit),
                {
                    scale = 1f
                    offset = Offset.Zero
                },
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.kitZoomIn),
                {
                    scale = (scale * 1.5f).coerceAtMost(MAX_ZOOM)
                },
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

private const val MAX_ZOOM = 5f

public const val PICTURE_VIEW_TAG: String = "kit-picture-view"
