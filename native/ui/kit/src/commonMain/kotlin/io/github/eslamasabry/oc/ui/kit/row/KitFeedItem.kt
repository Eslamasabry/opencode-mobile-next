package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitPinned
import org.jetbrains.compose.resources.stringResource

/** The agent a conversation belongs to: [id] is the bundled mark slug, [name] the agent's plain name. */
public data class KitFeedAgent(val id: String, val name: String)

/**
 * Flutter kit_feed_item.dart: title, then one inline state/project/server/time line. With an [agent] the row leads with
 * that agent's monochrome mark; [live] adds a small dot on the mark while the agent is working or needs the person
 * (the status word stays in the line, so colour is never the only signal). Long-press opens the row's actions.
 */
@Composable
public fun KitFeedItem(
    title: String,
    project: String,
    time: String?,
    state: KitRowState?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    growKey: Any? = null,
    agent: KitFeedAgent? = null,
    server: String? = null,
    live: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /** A quiet pin after the title (and "Pinned" in what TalkBack says) for a conversation pinned to the top. */
    pinned: Boolean = false,
) {
    if (LocalKitStyle.current.style == Style.Signal) {
        KitRow(
            title, modifier, supporting = project.ifBlank { null }, state = state, meta = time, onClick = onClick, growKey = growKey,
            server = server, leading = agent?.let { KitRowLeading.Logo(it.id, it.name, agent = true) },
            onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
        )
        return
    }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val large = LocalDensity.current.fontScale > 1.3f // F: lib/ui/kit/kit_feed_item.dart:365
    val word = state?.word
    val statusColor = when (state?.tone) {
        KitRowStateTone.NeedsYou -> colors.attention
        KitRowStateTone.Running -> colors.accent
        else -> colors.text2
    }
    val meta = buildAnnotatedString {
        if (word != null) {
            if (done) append("✓ ")
            withStyle(SpanStyle(color = statusColor, fontWeight = if (state?.tone == KitRowStateTone.NeedsYou || state?.tone == KitRowStateTone.Running) FontWeight.SemiBold else null)) { append(word) }
        }
        if (project.isNotBlank()) { if (length > 0) append("\u00a0· "); append("\u2068$project\u2069") }
        if (!server.isNullOrBlank()) { if (length > 0) append("\u00a0· "); append("\u2068$server\u2069") }
        if (time != null) { if (length > 0) append("\u00a0· "); append(time.replace(' ', '\u00a0')) }
    }
    val pinnedWord = if (pinned) stringResource(Res.string.kitPinned) else null
    val words = listOfNotNull(title, pinnedWord, agent?.name, word, project.takeIf { it.isNotBlank() }, server?.takeIf { it.isNotBlank() }, time).joinToString(", ")
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S3.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (agent != null) {
                Box {
                    // N-54: the agent's mark in the rows' own tile, so it lines up with the tiles under the list and
                    // reads as a mark, not a stray shape.
                    RowLeadingTile(KitRowLeading.Logo(agent.id, agent.name, agent = true), dim = false)
                    if (live) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(10.dp)
                                .background(colors.ground, CircleShape).padding(2.dp)
                                .background(if (state?.tone == KitRowStateTone.NeedsYou) colors.attention else colors.accent, CircleShape),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(title, Modifier.weight(1f, fill = false), role = TypeRole.RowTitle, maxLines = if (large) 2 else 1) // F: lib/ui/kit/kit_feed_item.dart:490
                    // Quiet: the secondary tone at the size of the line under it, never a badge.
                    if (pinned) KitGlyph(KitIcons.pin, colors.text2, 14.dp)
                }
                if (meta.isNotEmpty()) BasicText(meta, style = LocalKitTypography.current.style(TypeRole.Secondary).copy(color = colors.text2), maxLines = if (large) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val m = modifier.kitMaxWidth(CapRole.List).kitGrowSource(growKey).semantics(mergeDescendants = true) { contentDescription = words }
    if (onClick == null) Box(m) { body() }
    else KitTappable(onClick, m, role = KitTappableRole.Row, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel) { body() }
}
