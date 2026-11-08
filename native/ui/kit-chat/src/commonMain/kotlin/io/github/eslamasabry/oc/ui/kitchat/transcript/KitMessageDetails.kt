package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource

/**
 * A visible reasoning fold (Flutter `KitMessage.thought`, kit_message.dart `_Thought` in `_Fold`): one 48 dp line,
 * the idea glyph (small, text2), space2, the title in the secondary role and ink, the kit's chevron at the end; open,
 * the body under it (space1 above, space2 below) as Markdown in the secondary role. The collapsed line never truncates
 * the expanded body.
 */
@Composable
public fun KitThoughtBlock(
    key: String,
    title: String,
    text: String,
    live: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: KitMarkdownActions,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(key, text, live) { KitMdSource.blocks(key, text, live) }
    val tokens = LocalKitTokens.current
    Column(modifier.then(column())) {
        KitTappable(
            { onExpandedChange(!expanded) },
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                if (expanded) collapse { onExpandedChange(false); true } else expand { onExpandedChange(true); true }
            },
            shape = KitShapeId.Tile,
            role = KitTappableRole.Row, // the fold line is a row: full width, the 48 dp line (kit_message.dart `_Fold`)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = tokens.minTarget.dp).padding(vertical = Space.S1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitIcon(KitIcons.idea, size = KitIconSize.Small, tone = TextTone.Secondary)
                Spacer(Modifier.width(Space.S2.dp))
                KitText(title, Modifier.weight(1f), role = TypeRole.Secondary, tone = TextTone.Secondary)
                Spacer(Modifier.width(Space.S1.dp))
                KitChevron(expanded)
            }
        }
        KitReveal(visible = expanded) {
            KitMarkdown(
                blocks,
                actions,
                Modifier.padding(top = Space.S1.dp, bottom = Space.S2.dp),
                live = live,
                role = TypeRole.Secondary,
            )
        }
    }
}

/** The platform formats actual message time with its local date and 12/24-hour preferences. */
@Composable
public fun KitMessageTimestamp(createdMs: Long, nowMs: Long, modifier: Modifier = Modifier) {
    val text = LocalKitPlatform.current.messageTime(createdMs, nowMs) ?: return
    KitQuietLine(text, modifier)
}
