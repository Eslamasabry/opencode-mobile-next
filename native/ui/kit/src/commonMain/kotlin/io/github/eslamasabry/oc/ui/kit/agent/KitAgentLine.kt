package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** The words of the line: "Claude Code · on this phone" (Dart `'$agent · $place'`). */
internal fun agentLineText(agent: String, place: String): String = "$agent · $place"

/**
 * The one quiet line under a question that says who will do the work and where, and opens the choices behind it:
 * "Claude Code · on this phone ⌄" (a port of Dart `KitAgentLine`, lib/ui/kit/kit_agent_line.dart). Plain muted words and
 * a small chevron, no fill and no border. The whole line is the touch target (48 dp tall at least) and a screen reader
 * hears [semanticsLabel].
 */
@Composable
public fun KitAgentLine(
    agent: String,
    place: String,
    onClick: () -> Unit,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        KitTappable(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = semanticsLabel },
            shape = KitShapeId.Button,
        ) {
            Row(
                Modifier.padding(Space.S2.dp).clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitText(
                    agentLineText(agent, place),
                    Modifier.weight(1f, fill = false),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.S1.dp))
                KitIcon(KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
            }
        }
    }
}
