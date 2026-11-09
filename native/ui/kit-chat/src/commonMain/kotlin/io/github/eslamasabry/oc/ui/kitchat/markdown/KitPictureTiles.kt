package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.image.rememberKitImage

/**
 * P12-CHAT-FEEL: a line of pictures in a reply (B-CHAT-059, Flutter `KitMarkdownImage` tiles). Each
 * picture is a row naming it, one tap from its viewer (a server file) or the link policy (a web
 * address, never fetched by the app). A picture the app cannot open is named and inert.
 */
@Composable
public fun KitPictureTiles(
    block: KitMdBlock.Pictures,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKitColors.current
    DisableSelection {
        val pictures = block.pictures.distinctBy { it.path ?: it.url ?: it.label }
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            pictures.forEach { picture ->
                val open =
                    actions.onPicture?.takeIf {
                        picture.url != null || (picture.path != null && actions.openFilePictures)
                    }
                val read = actions.readPicture
                val path = picture.path
                val confirmed = path != null && path in actions.confirmedPaths
                val drawable =
                    confirmed && path != null && read != null && DRAWABLE.containsMatchIn(path)
                // The kit picture pipeline: downsampled to the tile, memory and disk cached, off the main thread.
                val bitmap by
                    rememberKitImage(
                        key = if (drawable) "pic:$path" else null,
                        maxEdge = TILE_EDGE,
                        scope = actions.pictureScope,
                    ) {
                        read?.invoke(path!!)
                    }
                val tile =
                    @Composable {
                        val image = bitmap
                        if (image != null) {
                            val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
                            val height =
                                if (pictures.size > 1) 148.dp else minOf(360f, 320f / aspect).dp
                            val width =
                                if (pictures.size > 1) (148f * aspect).coerceIn(48f, 360f).dp
                                else (height.value * aspect).coerceAtMost(320f).dp
                            Column(Modifier.width(width)) {
                                Image(
                                    image,
                                    actions.pictureLabel(picture),
                                    Modifier.width(width).height(height),
                                    contentScale = ContentScale.Fit,
                                )
                                if (picture.label != null)
                                    KitText(
                                        picture.label,
                                        role = TypeRole.Caption,
                                        tone = TextTone.Secondary,
                                        maxLines = 1,
                                    )
                            }
                        } else
                            Row(
                                Modifier.width(240.dp)
                                    .background(colors.surface2, RoundedCornerShape(8.dp))
                                    .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                            ) {
                                KitText("▣", role = TypeRole.Body, tone = TextTone.Secondary)
                                KitText(
                                    actions.pictureLabel(picture),
                                    Modifier.weight(1f),
                                    role = TypeRole.Body,
                                    tone =
                                        if (open != null) TextTone.Accent else TextTone.Secondary,
                                )
                            }
                    }
                if (open != null) {
                    KitTappable(
                        onClick = { open(picture) },
                        role = KitTappableRole.Row,
                        modifier = Modifier.testTag(PICTURE_TILE_TAG),
                    ) {
                        tile()
                    }
                } else {
                    tile()
                }
            }
        }
    }
}

public const val PICTURE_TILE_TAG: String = "kit-picture-tile"

/** The longest side a tile draws (a single picture is at most 360 dp tall, a row of them 148 dp). */
private val TILE_EDGE = 360.dp

private val DRAWABLE = Regex("""\.(png|jpe?g|gif|webp|bmp|wbmp)$""", RegexOption.IGNORE_CASE)
