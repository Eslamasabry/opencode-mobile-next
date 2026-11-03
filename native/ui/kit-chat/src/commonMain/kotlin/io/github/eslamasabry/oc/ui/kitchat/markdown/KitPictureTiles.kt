package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * P12-CHAT-FEEL: a line of pictures in a reply (B-CHAT-059, Flutter `KitMarkdownImage` tiles). Each picture is a row
 * naming it, one tap from its viewer (a server file) or the link policy (a web address, never fetched by the app). A
 * picture the app cannot open is named and inert.
 */
@Composable
public fun KitPictureTiles(block: KitMdBlock.Pictures, actions: KitMarkdownActions, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    DisableSelection {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            block.pictures.forEach { picture ->
                val open = actions.onPicture?.takeIf { picture.url != null || (picture.path != null && actions.openFilePictures) }
                val tile = @Composable {
                    Row(
                        Modifier.fillMaxWidth()
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
                            tone = if (open != null) TextTone.Accent else TextTone.Secondary,
                        )
                    }
                }
                if (open != null) {
                    KitTappable(
                        onClick = { open(picture) },
                        role = KitTappableRole.Row,
                        modifier = Modifier.testTag(PICTURE_TILE_TAG),
                    ) { tile() }
                } else {
                    tile()
                }
            }
        }
    }
}

public const val PICTURE_TILE_TAG: String = "kit-picture-tile"
