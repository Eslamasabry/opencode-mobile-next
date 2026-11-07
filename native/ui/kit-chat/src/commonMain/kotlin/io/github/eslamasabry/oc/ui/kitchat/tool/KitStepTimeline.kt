package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.motion.KitSpin
import io.github.eslamasabry.oc.ui.kit.motion.pace
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt

/**
 * A turn's work as one quiet line that opens in place to a timeline (Dart `KitStepTimeline`,
 * lib/ui/kit/chat/kit_step_timeline.dart; docs/ux-system/kit-api/KitStepTimeline.md).
 *
 * The line is [icon] (in `text2`), [label] in the secondary role and tone, and the kit's ONE chevron, which turns
 * 180° when [expanded]; [mark] takes the icon's place (the failed mark), and [live] puts the step light on the icon
 * while a step runs and the line is shut. Opened, [head] ("Show earlier steps") and [steps] hang off a thin rail under
 * the icon: each step draws its own node on the rail ([KitToolRow] in its timeline form, [KitStepSentence]); the rail
 * runs from under the line's icon to the last node. Steps fold in and out with the kit's one layout motion
 * ([KitReveal]: size and fade, STANDARD in, QUICK out; instant under reduced motion). The rail mirrors under Arabic.
 *
 * States: shut, open; live (a step running while shut); a step arriving while open fades (and at Full rises 6 dp) in.
 */
@Composable
public fun KitStepTimeline(
    label: String,
    icon: ImageVector,
    expanded: Boolean,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    spoken: String? = null,
    mark: (@Composable () -> Unit)? = null,
    live: Boolean = false,
    head: (@Composable () -> Unit)? = null,
    lineModifier: Modifier = Modifier,
    steps: @Composable ColumnScope.() -> Unit = {},
) {
    val m = railMeasures()
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rail = remember { StepRail() }
    // F: lib/ui/kit/chat/kit_step_timeline.dart:962 the glyph sits in the middle of the 48 dp target
    val pad = maxOf(Space.S1.dp, (tokens.minTarget.dp - m.first) / 2) // F: lib/ui/kit/chat/kit_step_timeline.dart:962
    // F: lib/ui/kit/chat/kit_step_timeline.dart:1061 where the rail's stub starts under the line's glyph
    val startY = pad + m.first / 2 + m.glyph / 2 + Space.S1.dp // F: lib/ui/kit/chat/kit_step_timeline.dart:1061
    var stepsTop by remember { mutableFloatStateOf(0f) }
    var stepsBottom by remember { mutableFloatStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = 700.dp) // F: lib/ui/kit/kit_layout.dart:48 paneDetailMaxWidth
            .onPlaced { rail.root = it }
            .drawBehind {
                if (!expanded || stepsBottom <= stepsTop) return@drawBehind
                // The last node's centre, or the end of the steps when none draws a node.
                val end = rail.centres.values.maxOrNull() ?: stepsBottom
                val top = startY.toPx()
                if (end <= top) return@drawBehind
                val stroke = 2f // F: lib/ui/kit/chat/kit_step_timeline.dart:742 two physical pixels
                val centre = m.glyph.toPx() / 2f // F: lib/ui/kit/chat/kit_step_timeline.dart:739
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - centre else centre
                val left = (x - stroke / 2f).roundToInt().toFloat() // F: lib/ui/kit/chat/kit_step_timeline.dart:802
                // F: lib/ui/kit/chat/kit_step_timeline.dart:746 text3 at half strength
                drawRect(colors.text3.copy(alpha = colors.text3.alpha * .5f), Offset(left, top), Size(stroke, end - top)) // F: lib/ui/kit/chat/kit_step_timeline.dart:746
            }
    ) {
        Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start)) {
            KitTappable(
                onClick = onPressed,
                role = KitTappableRole.Button,
                shape = KitShapeId.Tile, // F: lib/ui/kit/chat/kit_step_timeline.dart:1052
                modifier =
                    lineModifier.semantics {
                        if (spoken != null) stateDescription = spoken
                        if (expanded) collapse { onPressed(); true } else expand { onPressed(); true }
                    },
            ) {
                Row(Modifier.padding(vertical = pad), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(m.glyph).height(m.first).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        when {
                            mark != null -> mark()
                            // F: lib/ui/kit/chat/kit_step_timeline.dart:976 the glyph carries the step light
                            live -> KitStepTile(icon, KitStepNodeLook.Running, m.glyph)
                            // F: lib/ui/kit/chat/kit_step_timeline.dart:982 the glyph at the rail's size in text2 (drawn, so it
                            // carries no text of its own)
                            else -> KitToolGlyph(icon, colors.text2, m.glyph)
                        }
                    }
                    Spacer(Modifier.width(m.column - m.glyph)) // F: lib/ui/kit/chat/kit_step_timeline.dart:994
                    // Two lines cut at a word; from 1.3x text one line with an ellipsis (Flutter's Text with an ellipsis
                    // and no maxLines stops at its first line, as kit_work_line_default_text2 shows).
                    Box(Modifier.weight(1f, fill = false)) {
                        KitText(label, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = if (m.wide) 1 else 2) // F: lib/ui/kit/chat/kit_step_timeline.dart:1008-1019
                    }
                    Spacer(Modifier.width(Space.S1.dp)) // F: lib/ui/kit/chat/kit_step_timeline.dart:1022
                    // F: lib/ui/kit/chat/kit_step_timeline.dart:1023 decoration: the line carries the expanded state
                    Box(Modifier.height(m.first).testTag("kit-step-timeline-chevron").clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        // F: lib/ui/kit/motion/kit_motion_parts.dart:272-302 KitSpin.chevron: 20 dp text2, half a turn on STANDARD
                        KitSpin(if (expanded) .5f else 0f, pace = KitPace.Standard) { // F: lib/ui/kit/motion/kit_motion_parts.dart:295
                            KitIcon(KitIcons.chevronDown, size = KitIconSize.Small, tone = TextTone.Secondary)
                        }
                    }
                }
            }
        }
        KitReveal(visible = expanded) {
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { c ->
                        val root = rail.root ?: return@onGloballyPositioned
                        if (!root.isAttached || !c.isAttached) return@onGloballyPositioned
                        stepsTop = root.localPositionOf(c, Offset.Zero).y
                        stepsBottom = stepsTop + c.size.height
                    }
                    // F: lib/ui/kit/chat/kit_step_timeline.dart:916 the node column kept free at the start
                    .padding(start = m.column)
            ) {
                CompositionLocalProvider(LocalKitStepRail provides rail) {
                    if (head != null) head()
                    steps()
                }
                SideEffect { rail.settled = true }
            }
        }
        if (!expanded) SideEffect { rail.settled = false }
    }
}

/**
 * The glyph to lead a work line with, by what the work mostly did (Dart `KitStepTimeline.iconFor`): the largest count
 * wins, ties go to the earlier kind; no count at all is the tools glyph.
 */
public fun kitWorkIcon(
    read: Int = 0,
    searched: Int = 0,
    listed: Int = 0,
    edited: Int = 0,
    ran: Int = 0,
    fetched: Int = 0,
    delegated: Int = 0,
    other: Int = 0,
): ImageVector {
    // F: lib/ui/kit/chat/kit_step_timeline.dart:397-406 the order breaks ties
    val counts =
        listOf(
            edited to KitIcons.editNote,
            ran to KitIcons.terminal,
            fetched to KitIcons.globe,
            delegated to KitIcons.agent,
            searched to KitIcons.search,
            read to KitIcons.fileText,
            listed to KitIcons.folderOpen,
            other to KitIcons.tools,
        )
    var best = counts.first()
    for (entry in counts) if (entry.first > best.first) best = entry
    return if (best.first == 0) KitIcons.tools else best.second
}

/**
 * A sentence between steps (prose the agent said while it worked) with a quiet dot on the rail (Dart
 * `KitStepTimeline.sentence`). Outside a timeline it is [content] unchanged.
 */
@Composable
public fun KitStepSentence(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!kitOnStepRail()) {
        Box(modifier) { content() }
        return
    }
    val m = railMeasures()
    val colors = LocalKitColors.current
    // F: lib/ui/kit/chat/kit_step_timeline.dart:365-370 a 24 dp first line, one step down, padded one step
    RailNode(
        box = 24.dp * m.scale, // F: lib/ui/kit/chat/kit_step_timeline.dart:365
        top = Space.S1.dp,
        modifier = modifier,
        node = { Box(Modifier.size(6.dp).background(colors.text3, CircleShape)) }, // F: lib/ui/kit/chat/kit_step_node.dart:38,66
    ) {
        Box(Modifier.padding(vertical = Space.S1.dp)) { content() }
    }
}

/** True inside an opened [KitStepTimeline]'s steps (Dart `KitStepTimeline.inside`): a step there draws its node. */
@Composable
public fun kitOnStepRail(): Boolean = LocalKitStepRail.current != null

/** The rail a step sits on, and where each node's centre is (the rail ends on the last one). */
@Stable
internal class StepRail {
    var root: LayoutCoordinates? = null
    val centres = mutableStateMapOf<Any, Float>()

    /** The open steps have been shown once: a step composed after this arrived while the timeline was open. */
    var settled by mutableStateOf(false)
}

internal val LocalKitStepRail = compositionLocalOf<StepRail?> { null }

/** The rail's measures, all from the kit's tokens and the person's text size (Dart `_Rail`). */
internal class RailMeasures(
    val glyph: Dp,
    val first: Dp,
    val stepLine: Dp,
    val scale: Float,
) {
    /** Where the words start. */
    val column: Dp
        get() = glyph + Space.S3.dp // F: lib/ui/kit/chat/kit_step_timeline.dart:736

    /** From 1.3x text the words wrap (A11Y-8). */
    val wide: Boolean
        get() = scale >= 1.3f // F: lib/ui/kit/chat/kit_tool_row.dart:122 _kWrapTextScale
}

@Composable
internal fun railMeasures(): RailMeasures {
    val tokens = LocalKitTokens.current
    val scale = kitTextScale()
    // F: lib/ui/kit/chat/kit_step_timeline.dart:723 iconSize(smallIconSize).roundToDouble()
    val glyph = (tokens.smallIcon * minOf(scale, tokens.maxIconScale)).roundToInt().dp
    val first = 20.dp * scale // F: lib/ui/kit/chat/kit_step_timeline.dart:958 rail.line(20)
    val stepLine = maxOf(32.dp, 20.dp * scale + Space.S1.dp * 2) // F: lib/ui/kit/chat/kit_step_timeline.dart:355
    return RailMeasures(glyph, first, stepLine, scale)
}

/**
 * Draws [node] in the free column at the start of the step, centred in a [box]-high band [top] down (Dart
 * `_StepNode`); the node is decoration (no semantics, no taps) and sits outside [content]'s own box. It reports its
 * centre so the rail can end on the last node. A step composed while its timeline is already open arrives: it fades
 * in over STANDARD and, at Full motion, rises 6 dp; under reduced motion it is simply there.
 */
@Composable
internal fun RailNode(
    box: Dp,
    top: Dp,
    modifier: Modifier = Modifier,
    node: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val rail = LocalKitStepRail.current
    if (rail == null) {
        Box(modifier) { content() }
        return
    }
    val m = railMeasures()
    val key = remember { Any() }
    DisposableEffect(rail, key) { onDispose { rail.centres.remove(key) } }
    val motion = KitMotion.state
    val arrives = remember { rail.settled && !motion.reduced }
    val shown = remember { Animatable(if (arrives) 0f else 1f) }
    val standard = KitMotion.specs.pace<Float>(KitPace.Standard)
    LaunchedEffect(Unit) { if (shown.value < 1f) shown.animateTo(1f, standard) }
    val rise = if (motion.travels) 6.dp else 0.dp // F: lib/ui/kit/chat/kit_step_timeline.dart:531 _StepArrival.rise
    Box(
        modifier.fillMaxWidth().graphicsLayer {
            alpha = shown.value
            translationY = rise.toPx() * (1f - shown.value)
        }
    ) {
        content()
        Box(
            Modifier.offset(x = -m.column, y = top)
                .width(m.glyph)
                .height(box)
                .clearAndSetSemantics {}
                .onGloballyPositioned { c ->
                    val root = rail.root ?: return@onGloballyPositioned
                    if (root.isAttached && c.isAttached)
                        rail.centres[key] = root.localPositionOf(c, Offset(0f, c.size.height / 2f)).y // F: lib/ui/kit/chat/kit_step_node.dart:72
                },
            contentAlignment = Alignment.Center,
        ) {
            node()
        }
    }
}
