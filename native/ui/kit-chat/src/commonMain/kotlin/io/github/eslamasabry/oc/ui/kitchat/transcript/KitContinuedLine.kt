package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.continuedGone
import org.jetbrains.compose.resources.stringResource

/**
 * "Continued in Claude Code" / "Continued from <title>": one quiet line that opens the other conversation.
 * [gone] words a conversation that no longer exists and turns the line off (kit_continued_line.dart).
 */
@Composable
public fun KitContinuedLine(text: String, onOpen: () -> Unit, modifier: Modifier = Modifier, gone: Boolean = false) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val goneWords = stringResource(Res.string.continuedGone)
    val words = if (gone) goneWords else text
    Box(modifier.widthIn(max = 700.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
    KitTappable(
        onClick = onOpen,
        modifier = Modifier.testTag("continued-line").semantics { contentDescription = words },
        enabled = !gone,
        disabledReason = if (gone) goneWords else null,
    ) {
        Row(
            Modifier.padding(horizontal = Space.S2.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitIcon(KitIcons.link, size = KitIconSize.Small)
            Spacer(Modifier.width(Space.S2.dp))
            KitText(
                words,
                Modifier.weight(1f, fill = false),
                role = TypeRole.Secondary,
                tone = if (gone) TextTone.Tertiary else TextTone.Accent,
                maxLines = 1,
            )
            if (!gone) {
                Spacer(Modifier.width(Space.S1.dp))
                KitIcon(if (rtl) KitIcons.chevronLeft else KitIcons.chevronRight, size = KitIconSize.Small)
            }
        }
    }
    }
}
