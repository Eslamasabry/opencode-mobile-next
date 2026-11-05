package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Flutter KitRow(titleAccent: true): an action in place, with the same rails as its sibling rows.
 */
@Composable
public fun KitActionRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    KitTappable(onClick, modifier, role = KitTappableRole.Row, shape = KitShapeId.Square) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = tokens.rowHeightTwo.dp)
                .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Box(
                Modifier.size(tokens.iconTile.dp).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                val size = 24.dp // F: lib/ui/kit/kit_row.dart:1085
                KitGlyph(icon, colors.text2, size)
            }
            Column(Modifier.weight(1f)) {
                KitText(title, role = TypeRole.RowTitle, tone = TextTone.Accent)
                KitText(supporting, role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        }
    }
}
