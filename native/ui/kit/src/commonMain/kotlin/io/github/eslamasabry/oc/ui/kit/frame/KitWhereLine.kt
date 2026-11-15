package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/**
 * Where the page's work happens, as the one line under a centred title: "‹folder› docsdemo · This phone ⌄". The whole
 * line is one 48 dp target that opens the chooser; the words wrap at their spaces (two lines) and are never cut.
 *
 * States: rest, pressed Text: [label], frame, up to two lines Cap: 360 dp Motion: none
 */
@Composable
public fun KitWhereLine(label: String, spoken: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KitTappable(
        onClick = onClick,
        onClickLabel = spoken,
        shape = KitShapeId.Button,
        modifier = modifier.widthIn(max = 360.dp).semantics { contentDescription = spoken; testTag = "kit-where-line" },
    ) {
        Row(
            Modifier.padding(horizontal = Space.S2.dp, vertical = Space.S1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp, Alignment.CenterHorizontally),
        ) {
            KitIcon(KitIcons.files, size = KitIconSize.Small, tone = TextTone.Secondary)
            KitText(
                label,
                Modifier.weight(1f, fill = false),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
                kind = KitTextKind.Frame,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            KitIcon(KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
        }
    }
}
