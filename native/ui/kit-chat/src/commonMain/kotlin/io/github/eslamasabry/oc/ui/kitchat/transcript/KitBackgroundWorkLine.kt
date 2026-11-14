package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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

/**
 * The quiet line under the newest turn while work the agent started goes on in the background (kit_background_work_line
 * .dart): "Still running in the background · build.sh · 2 min", or "2 tasks still running in the background". It sits
 * in the transcript's own column, no bar or card (no-new-UI-levels rule). With [onOpen] the whole line is one tap
 * target that opens the conversation's running work ([openLabel] is what a screen reader says it does); without it the
 * line is the plain quiet text.
 */
@Composable
public fun KitBackgroundWorkLine(
    text: String,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    openLabel: String? = null,
) {
    if (onOpen == null) {
        KitQuietLine(text, modifier)
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.then(column()), contentAlignment = Alignment.CenterStart) {
        KitTappable(
            onClick = onOpen,
            modifier = Modifier.testTag("kit-background-work-line"),
            onClickLabel = openLabel,
        ) {
            Row(
                Modifier.padding(vertical = Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitIcon(KitIcons.terminal, size = KitIconSize.Small)
                Spacer(Modifier.width(Space.S2.dp))
                KitText(
                    text,
                    Modifier.weight(1f, fill = false),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    maxLines = 2,
                )
                Spacer(Modifier.width(Space.S1.dp))
                KitIcon(if (rtl) KitIcons.chevronLeft else KitIcons.chevronRight, size = KitIconSize.Small)
            }
        }
    }
}
