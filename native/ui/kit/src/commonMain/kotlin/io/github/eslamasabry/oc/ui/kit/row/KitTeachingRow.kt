package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Help's title-only row: three title lines, with a replay bubble anchored to Show me, not the row.
 */
@Composable
public fun KitTeachingRow(
    title: String,
    icon: ImageVector,
    showLabel: String,
    onShow: () -> Unit,
    message: String,
    replaying: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKitTokens.current
    val scale = minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    val colors = LocalKitColors.current
    Row(
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .heightIn(min = tokens.rowHeightOne.dp)
            .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Box(
            Modifier.size((tokens.iconTile * scale).dp).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            KitGlyph(icon, colors.text2, (24f * scale).dp) // F: lib/ui/kit/kit_row.dart:1085
        }
        KitText(
            title,
            Modifier.weight(1f),
            role = TypeRole.RowTitle,
            maxLines = 3,
        ) // F: lib/ui/screens/settings/tips_screen.dart:74
        KitTip(message, replaying, onDismissed) {
            KitButton(showLabel, onShow, role = KitButtonRole.Tertiary)
        }
    }
}
