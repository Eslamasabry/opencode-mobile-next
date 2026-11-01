package io.github.eslamasabry.oc.ui.kit.content.foundation

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * A quiet labelled action in content frames (zoom buttons, Close, Try again). Internal until the kit's `KitButton`
 * secondary/quiet styles land in `:ui:kit` (WP K1); then this file is deleted and callers use those.
 */
@Composable
internal fun ContentTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    val tokens = LocalKitTokens.current
    KitTappable(
        onClick = onClick,
        modifier = modifier.heightIn(min = tokens.minTarget.dp),
        enabled = enabled,
        disabledReason = disabledReason,
        shape = KitShapeId.Pill,
        ink = TextTone.Accent,
    ) {
        KitText(
            label,
            Modifier.padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
            role = TypeRole.Button,
            tone = if (enabled) TextTone.Accent else TextTone.Tertiary,
            kind = KitTextKind.Frame,
            maxLines = 2,
        )
    }
}
