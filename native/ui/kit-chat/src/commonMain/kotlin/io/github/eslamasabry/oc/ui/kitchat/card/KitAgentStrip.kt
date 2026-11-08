package io.github.eslamasabry.oc.ui.kitchat.card

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitMayTruncate
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskMark
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.status.TaskMarkRules
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitAgentOpen
import io.github.eslamasabry.oc.ui.words.res.kitAgentStripLabel
import org.jetbrains.compose.resources.stringResource

/** One agent on a team task (Dart `KitAgent`): the lead, a worker or a reviewer. */
@Immutable
public data class KitAgent(
    val id: String,
    /** The short name ("furiosa"), or the role word when it has none. */
    val name: String,
    val state: KitTaskState,
    /** "Lead", "Worker", "Reviewer". */
    val role: String? = null,
    /** A heat pause or a force stop (waiting or working only). */
    val paused: Boolean = false,
    /** Opens its conversation; null: not tappable (the lead). */
    val onOpen: (() -> Unit)? = null,
)

/**
 * Who is working on a team task (Dart `KitAgentStrip`, lib/ui/kit/chat/kit_agent_strip.dart), in order: the lead, then
 * workers and reviewers as they joined. Each agent is a `surface3` pill at the touch target's height (start space2,
 * end space3): its state mark (the needs-you mark for needs you), space2, the name in the label role and primary ink,
 * one line. A phone scrolls the chips sideways from the gutter, space2 apart; a wide window wraps them. Capped at the
 * conversation width. Empty draws nothing.
 *
 * Text: frame. Cap: conversation (700).
 */
@Composable
public fun KitAgentStrip(agents: List<KitAgent>, modifier: Modifier = Modifier, semanticsLabel: String? = null) {
    if (agents.isEmpty()) return
    val tokens = LocalKitTokens.current
    val window = LocalKitWindow.current
    val label = semanticsLabel ?: stringResource(Res.string.kitAgentStripLabel)
    // A chip never grows past the strip's width, so a long name truncates instead of pushing it out of reach (A11Y-8).
    val maxChip = maxOf(tokens.minTarget.toFloat(), window.widthDp - tokens.gutter * 2).dp
    Box(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth().semantics { isTraversalGroup = true; contentDescription = label }) {
        if (window.cls.isWide) {
            Box(Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp), contentAlignment = Alignment.CenterStart) {
                KitChipWrap { agents.forEach { AgentChip(it, maxChip) } }
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = tokens.gutter.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { agents.forEach { AgentChip(it, maxChip) } }
        }
    }
}

@Composable
private fun AgentChip(agent: KitAgent, maxWidth: androidx.compose.ui.unit.Dp) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val word = stringResource(TaskMarkRules.word(agent.state, agent.paused))
    val spoken = listOfNotNull(agent.name, agent.role, word).joinToString(", ")
    val content: @Composable () -> Unit = {
        Row(
            Modifier.heightIn(min = tokens.minTarget.dp).widthIn(max = maxWidth).padding(start = Space.S2.dp, end = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (agent.state == KitTaskState.NeedsYou) KitNeedsYouMark() else KitTaskMark(agent.state, paused = agent.paused)
            Spacer(Modifier.width(Space.S2.dp))
            KitText(
                agent.name,
                Modifier.weight(1f, fill = false).kitMayTruncate("agent-name"), // A11Y-8: a long name ends in "…"
                role = TypeRole.Label,
                tone = TextTone.Primary,
                maxLines = 1,
            )
        }
    }
    val pill = Modifier.background(colors.surface3, CircleShape)
    val onOpen = agent.onOpen
    if (onOpen == null) {
        // The lead: plain, not a button.
        Box(pill.clearAndSetSemantics { contentDescription = spoken }) { content() }
    } else {
        KitTappable(
            onOpen,
            pill.semantics { contentDescription = spoken },
            shape = KitShapeId.Pill,
            onClickLabel = stringResource(Res.string.kitAgentOpen, agent.name),
        ) { content() }
    }
}
