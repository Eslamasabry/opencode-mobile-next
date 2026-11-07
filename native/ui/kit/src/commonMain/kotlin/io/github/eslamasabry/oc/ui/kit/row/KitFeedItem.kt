package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.kitGrowSource
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitPinned
import org.jetbrains.compose.resources.stringResource

/**
 * The agent a conversation belongs to: [id] is the bundled mark slug (blank: no mark, Dart `agentMark: null`), [name]
 * the agent's plain name.
 */
public data class KitFeedAgent(val id: String, val name: String)

/** The feed row's rules, apart from the drawing (Dart `KitFeedItem`). */
public object KitFeedItemRules {
    /** The space before the dot does not break, so a wrapped line never starts with a lone "·". */
    public const val DOT: String = " · "

    /** "2 min ago" is one piece: it never breaks between the number and its unit. */
    public fun time(time: String): String = time.replace(' ', ' ')

    /** From 1.3x text the title and the meta line each take a second line instead of being cut. */
    public fun large(fontScale: Float): Boolean = fontScale > 1.3f // F: lib/ui/kit/kit_feed_item.dart:365

    /** What a screen reader says for the row, in order: title, agent, state, project, time. */
    public fun spoken(title: String, agent: String?, state: String?, project: String, time: String?, extra: List<String?> = emptyList()): String =
        (listOf(title) + extra + listOf(agent, state, project.takeIf { it.isNotEmpty() }, time)).filterNotNull().joinToString(", ")

    /** Where the sweep's light is at [t] (0..1 of a lap): it starts left of the word and leaves right of it. */
    public fun sweepCentre(t: Float, rtl: Boolean): Float = -0.3f + 1.6f * (if (rtl) 1 - t else t) // F: lib/ui/kit/kit_feed_item.dart:574

    /** The sweep's half width, as a share of the word. */
    public const val SWEEP_HALF: Float = 0.18f // F: lib/ui/kit/kit_feed_item.dart:581
}

/**
 * One conversation in a feed (Chats home; Dart `KitFeedItem`), two lines at most: the title on one line, then one meta
 * line in the small size holding, in order, the agent's name (with its mark, only when it is not the default), the
 * state word (only when it matters), the project and the time: "Needs you · shopfront · 2 h ago". "Needs you" is amber
 * and firm, "Working" the accent with a soft light sweeping over it while motion runs, Done a quiet check, every other
 * state plain secondary text (never red). No preview, no Git mark, no pills; the whole row is one target and reads as
 * one sentence. At large text the title and the meta line each take a second line instead of being cut. Read-only
 * ([onClick] null) is the same row, untappable, at the same size. Long-press opens the row's actions.
 *
 * [server] (native) joins the meta line after the project; [pinned] (native) adds a quiet pin after the title. [live]
 * is kept for callers and draws nothing: Flutter's row has no dot, the state word says it.
 *
 * States: disabled (read-only)
 * Text: reading
 * Cap: List
 * Motion: the Working word's sweep, one lap per 2.2 s; reduced motion and Off = a still word
 * Hidden: long-press (the row's actions; the same actions are on the conversation's own menu)
 * Replaces: KitFeedItem, KitFeedStateTone (lib/ui/kit/kit_feed_item.dart)
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
        // Signal draws the conversation on its track (kit_feed_item.dart _buildSignal); the kit row carries it here.
        KitRow(
            title, modifier, supporting = project.ifBlank { null }, state = state, meta = time, onClick = onClick, growKey = growKey,
            server = server, leading = agent?.let { KitRowLeading.Logo(it.id, it.name, agent = true) },
            onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
        )
        return
    }
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val large = KitFeedItemRules.large(LocalDensity.current.fontScale)
    val word = state?.word
    val tone = state?.tone
    val stateColor =
        when (tone) {
            KitRowStateTone.NeedsYou -> colors.attention
            KitRowStateTone.Running -> colors.accent
            // Done is finished, not live: muted, told from Failed by its check; Failed is never red here.
            else -> colors.text2
        }
    val base = typography.style(TypeRole.Secondary).copy(color = colors.text2)
    val running = tone == KitRowStateTone.Running && word != null
    val sweep = running && !KitMotion.state.reduced && KitMotion.state.ambientLoops
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sweepBrush = if (sweep) sweepBrush(stateColor, colors.text1, rtl) else null
    val meta = buildAnnotatedString {
        if (agent != null) {
            if (agent.id.isNotBlank()) appendInlineContent(MARK, agent.name)
            append(agent.name + KitFeedItemRules.DOT)
        }
        if (word != null) {
            if (done) appendInlineContent(CHECK, "✓")
            val weight = if (tone == KitRowStateTone.NeedsYou || running) FontWeight.SemiBold else null // F: lib/ui/kit/kit_feed_item.dart:442,453
            val style = if (sweepBrush != null) SpanStyle(brush = sweepBrush, fontWeight = weight) else SpanStyle(color = stateColor, fontWeight = weight)
            withStyle(style) { append(word) }
            append(KitFeedItemRules.DOT)
        }
        if (project.isNotEmpty()) append("⁨$project⁩")
        if (!server.isNullOrBlank()) append(KitFeedItemRules.DOT + "⁨$server⁩")
        if (time != null) append((if (project.isEmpty() && server.isNullOrBlank()) "" else KitFeedItemRules.DOT) + KitFeedItemRules.time(time))
    }
    val size = base.fontSize.value
    val inline = mapOf(
        // The agent's mark scaled down to the meta line's text size, then space1 to its name.
        MARK to InlineTextContent(Placeholder((size + 2 + Space.S1).sp, (size + 2).sp, PlaceholderVerticalAlign.Center)) { // F: lib/ui/kit/kit_feed_item.dart:400
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (agent != null) KitProviderLogo(agent.id, agent.name, agent = true, markSize = (size + 2).dp) // F: lib/ui/kit/kit_feed_item.dart:400
            }
        },
        // The shape that tells Done from Working in a still image, then space1 to the word.
        CHECK to InlineTextContent(Placeholder((size + 1 + Space.S1).sp, (size + 1).sp, PlaceholderVerticalAlign.Center)) { // F: lib/ui/kit/kit_feed_item.dart:420
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                KitGlyph(KitIcons.check, stateColor, with(LocalDensity.current) { (size + 1).sp.toDp() }) // F: lib/ui/kit/kit_feed_item.dart:420
            }
        },
    )
    val pinnedWord = if (pinned) stringResource(Res.string.kitPinned) else null
    val words = KitFeedItemRules.spoken(title, agent?.name, word, project, time, listOf(pinnedWord, server?.takeIf { it.isNotBlank() }))
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = kitRowGutter(), vertical = Space.S3.dp) // F: lib/ui/kit/kit_feed_item.dart:476-477
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) { // F: lib/ui/kit/kit_feed_item.dart:485
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    KitText(title, Modifier.weight(1f, fill = false), role = TypeRole.RowTitle, maxLines = if (large) 2 else 1) // F: lib/ui/kit/kit_feed_item.dart:490
                    // Quiet: the secondary tone at the size of the line under it, never a badge.
                    if (pinned) KitGlyph(KitIcons.pin, colors.text2, with(LocalDensity.current) { size.sp.toDp() })
                }
                BasicText(
                    meta,
                    style = base,
                    maxLines = if (large) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = inline,
                )
            }
        }
    }
    val m = modifier.kitMaxWidth(CapRole.List).kitGrowSource(growKey).semantics(mergeDescendants = true) { contentDescription = words }
    if (onClick == null) Box(m) { body() }
    else KitTappable(onClick, m, role = KitTappableRole.Row, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel) { body() }
}

private const val MARK = "kit-feed-mark"
private const val CHECK = "kit-feed-check"

/** The live word's light: a soft highlight sweeping over "Working", one lap per [MotionTokens.LIVE_SWEEP_LAP]. */
@Composable
private fun sweepBrush(color: Color, light: Color, rtl: Boolean): Brush {
    val lap = rememberInfiniteTransition(label = "kit-feed-sweep")
    val t by lap.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(MotionTokens.LIVE_SWEEP_LAP, easing = LinearEasing), RepeatMode.Restart),
        label = "kit-feed-sweep-t",
    )
    val centre = KitFeedItemRules.sweepCentre(t, rtl)
    val half = KitFeedItemRules.SWEEP_HALF
    return Brush.horizontalGradient(
        (centre - half).coerceIn(0f, 1f) to color,
        centre.coerceIn(0f, 1f) to light,
        (centre + half).coerceIn(0f, 1f) to color,
    )
}
