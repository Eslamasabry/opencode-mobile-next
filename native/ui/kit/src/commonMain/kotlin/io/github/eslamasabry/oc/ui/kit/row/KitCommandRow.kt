package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Flutter KitRow's below slot and custom trailing rail, used by saved/found development commands.
 */
@Composable
public fun KitCommandRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val scale = kitTextScale()
    KitTappable(
        onClick,
        modifier.kitMaxWidth(CapRole.List),
        role = KitTappableRole.Row,
        shape = KitShapeId.Square,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = tokens.rowHeightTwo.dp)
                .padding(
                    start = Space.S4.dp,
                    end = if (trailing == null) Space.S4.dp else Space.S1.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Box(
                Modifier.size((tokens.iconTile * minOf(scale, TextScalePolicy.ICON_CAP)).dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                KitGlyph(
                    icon,
                    if (current) colors.accent else colors.text2,
                    (24f * minOf(scale, TextScalePolicy.ICON_CAP)).dp,
                ) // F: lib/ui/kit/kit_row.dart:1085
            }
            Column(
                Modifier.weight(1f).padding(vertical = Space.S2.dp),
                verticalArrangement = Arrangement.spacedBy(FlutterKitTokens.ROW_LINE_GAP.dp),
            ) {
                KitText(title, role = TypeRole.RowTitle, maxLines = kitRowTitleLines(scale))
                KitText(
                    supporting,
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    maxLines = kitRowSupportingLines(scale, 2),
                ) // F: lib/ui/screens/development_services_screen.dart:1009
                below?.invoke()
            }
            trailing?.invoke()
        }
    }
}
