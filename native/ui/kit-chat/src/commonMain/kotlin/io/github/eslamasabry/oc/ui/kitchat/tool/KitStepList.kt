package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
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
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.status.KitStepTree
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeActions
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeKind
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeState
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeWords
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStep
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState
import io.github.eslamasabry.oc.ui.kit.status.StepMark
import io.github.eslamasabry.oc.ui.kit.status.StepStroke
import io.github.eslamasabry.oc.ui.kit.status.StepTone
import io.github.eslamasabry.oc.ui.kit.status.StepTreeDims
import io.github.eslamasabry.oc.ui.kit.status.StepTreeRules
import io.github.eslamasabry.oc.ui.kit.status.drawStepStrokes
import io.github.eslamasabry.oc.ui.kit.status.rememberKitStepTreeState
import io.github.eslamasabry.oc.ui.kit.status.rememberStepTreeInk
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The first row of a turn's steps while older ones are folded (D1 "steps only", owner 2026-11-14): [label] ("Show 5
 * earlier steps" / "Hide earlier steps") and, quiet after it, [note]: what lies inside that matters ("1 declined · 1
 * failed"), so a problem is never folded away silently. [open] shows the earlier steps; a tap asks [onToggle].
 */
@Immutable
public class KitStepFoldLine(
    public val label: String,
    public val note: String?,
    public val open: Boolean,
    public val onToggle: () -> Unit,
)

/**
 * A turn's work as its steps (D1 "steps only", owner 2026-11-14; replaces the summary line of [KitStepTimeline] in the
 * transcript): no summary line, no frame, no fill. Every step is one row of the timeline it always was ([KitToolRow]
 * on the rail: the kind's glyph, the step's plain words, "+4 −2", the time when the data has one, the failed and the
 * declined in words; [KitStepFold] for a thought, with its own glyph; [KitStepGroup] for sub-agents at work together),
 * and one thin rail runs from each glyph's bottom to the next one's top (the step tree's sequence, quiet, in the
 * tree's own ink: 1.5 dp, Brutal 2 dp of ink, Paper a 1 dp rule; mirrored in Arabic).
 *
 * [fold] is the first row while older steps are folded; [earlier] are those steps, shown under it while it is open
 * (they unfold with the kit's one layout motion: STANDARD in, QUICK out, instant under reduced motion). A step that
 * arrives while the list is shown fades in and rises into place over STANDARD. Nothing here moves at rest: the only
 * moving mark is a running step's light, which runs on frames (so it stops with the window) and ends with the step.
 *
 * States: with or without the fold row, folded or open; a step arriving
 * Text: frame (the steps' own roles; the fold row Secondary, its note Caption)
 * Cap: 700 dp, as the reply column
 * Motion: KitReveal for the earlier steps; RailNode's arrival for a new step; nothing at rest
 * Replaces: [KitWorkLine]'s summary line and its separate "Thought" line in the transcript
 */
@Composable
public fun KitStepList(
    modifier: Modifier = Modifier,
    fold: KitStepFoldLine? = null,
    earlier: (@Composable ColumnScope.() -> Unit)? = null,
    steps: @Composable ColumnScope.() -> Unit,
) {
    val m = railMeasures()
    val rail = remember { StepRail() }
    val ink = rememberStepTreeInk()
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = 700.dp) // F: lib/ui/kit/kit_layout.dart:48 paneDetailMaxWidth
            .testTag(STEP_LIST_TAG)
            .onPlaced { rail.root = it }
            .drawBehind {
                val strokes =
                    stepListRail(
                        rail.spans.values.toList(),
                        x = m.glyph.toPx() / 2f,
                        width = size.width,
                        rtl = layoutDirection == LayoutDirection.Rtl,
                        bend = StepTreeDims.BEND.dp.toPx(),
                    )
                drawStepStrokes(strokes, ink)
            },
    ) {
        // The words keep one column in from the rail; each step's node is drawn back in that free column.
        Column(Modifier.fillMaxWidth().padding(start = m.column)) {
            CompositionLocalProvider(LocalKitStepRail provides rail) {
                if (fold != null) StepFoldRow(fold)
                if (earlier != null)
                    KitReveal(visible = fold?.open == true) {
                        Column(Modifier.fillMaxWidth()) { earlier() }
                    }
                steps()
            }
            SideEffect { rail.settled = true }
        }
    }
}

/**
 * The rail of a [KitStepList]: one quiet line from each node's bottom to the next node's top, in the order shown, on
 * the glyph column [x] from the start edge (from the end of [width] in Arabic). [spans] are the nodes' top and bottom.
 * The step tree's own sequence rule draws it, so it meets every glyph exactly, as the tree meets its marks.
 */
internal fun stepListRail(
    spans: List<ClosedFloatingPointRange<Float>>,
    x: Float,
    width: Float,
    rtl: Boolean,
    bend: Float,
): List<StepStroke> {
    if (spans.size < 2) return emptyList()
    val cx = if (rtl) width - x else x
    val marks =
        spans.sortedBy { it.start }.mapIndexed { i, s ->
            StepMark(i, null, 1, KitTreeStepState.Done, Offset(cx, (s.start + s.endInclusive) / 2f), (s.endInclusive - s.start) / 2f)
        }
    // A turn's steps are done or under way: the rail is never progress, so every piece is the quiet guide.
    return StepTreeRules.sequence(marks, bend).map { it.copy(tone = StepTone.Guide) }
}

/** The fold row: the unfold glyph on the rail, the label, the quiet note; one 32 dp line in a 48 dp target. */
@Composable
private fun StepFoldRow(fold: KitStepFoldLine) {
    val m = railMeasures()
    val tokens = LocalKitTokens.current
    val pad = (m.stepLine - m.first) / 2
    val toggle = fold.onToggle
    val note = fold.note
    val open = fold.open
    RailNode(
        box = m.stepLine,
        top = 0.dp,
        modifier = Modifier.testTag(STEP_FOLD_ROW_TAG),
        node = { KitStepTile(if (open) KitIcons.unfoldLess else KitIcons.unfoldMore, KitStepNodeLook.Queued, m.glyph) },
    ) {
        val line: @Composable BoxScope.() -> Unit = {
            Box(Modifier.fillMaxWidth().heightIn(min = m.stepLine).align(Alignment.CenterStart), contentAlignment = Alignment.CenterStart) {
                if (m.wide)
                    Column(Modifier.padding(vertical = pad)) {
                        KitText(fold.label, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2)
                        if (note != null) KitText(note, role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 2)
                    }
                else
                    Row(Modifier.padding(vertical = pad), verticalAlignment = Alignment.CenterVertically) {
                        KitText(fold.label, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 1)
                        if (note != null) {
                            Spacer(Modifier.width(Space.S2.dp))
                            KitText(note, Modifier.weight(1f, fill = false), role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 1)
                        }
                    }
            }
        }
        val tap: @Composable (Modifier) -> Unit = { target ->
            KitTappable(
                onClick = toggle,
                modifier =
                    target.semantics {
                        if (note != null) stateDescription = note
                        if (open) collapse { toggle(); true } else expand { toggle(); true }
                    },
                role = KitTappableRole.Row,
                shape = KitShapeId.Tile,
                content = line,
            )
        }
        if (m.wide) tap(Modifier.fillMaxWidth())
        else
            Box(Modifier.fillMaxWidth().height(m.stepLine), contentAlignment = Alignment.Center) {
                tap(Modifier.fillMaxWidth().requiredHeight(tokens.minTarget.dp))
            }
    }
}

/**
 * Sub-agents the agent runs at the same time, as ONE row of a [KitStepList] (D1): the sub-agent glyph on the rail (the
 * step light while one works), [name] ("2 sub-agents"), quiet [note] ("1 done · 1 working") and the fold chevron at the
 * end. The whole row opens or folds its [children], which hang from the glyph on the shared [KitStepTree] (Parallel: a
 * quiet trunk, a rounded branch to each child in its own state, never an order); a child opens its own conversation
 * through [actions]. It opens by itself only when [opensItself] (a child needs the person); otherwise the person opens
 * it. Sub-agents carry no start time, so a child shows its state, never a timer.
 */
@Composable
public fun KitStepGroup(
    name: String,
    note: String?,
    children: List<KitTreeStep>,
    words: KitStepTreeWords,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    opensItself: Boolean = false,
    actions: KitStepTreeActions = KitStepTreeActions(),
    state: KitStepTreeState = rememberKitStepTreeState(),
) {
    val m = railMeasures()
    val tokens = LocalKitTokens.current
    val pad = (m.stepLine - m.first) / 2
    KitStepTree(
        KitStepTreeKind.Parallel,
        children,
        words,
        modifier.fillMaxWidth().testTag(STEP_GROUP_TAG),
        actions = actions,
        state = state,
        indent = STEP_GROUP_CHILD_INDENT,
        opensItself = opensItself,
    ) {
        val shut = folded
        RailNode(
            box = m.stepLine,
            top = 0.dp,
            node = {
                KitStepTile(KitIcons.agent, if (live) KitStepNodeLook.Running else KitStepNodeLook.Plain, m.glyph, Modifier.stepTreeRoot())
            },
        ) {
            val line: @Composable BoxScope.() -> Unit = {
                Box(Modifier.fillMaxWidth().heightIn(min = m.stepLine).align(Alignment.CenterStart), contentAlignment = Alignment.CenterStart) {
                    Row(Modifier.fillMaxWidth().padding(vertical = pad), verticalAlignment = if (m.wide) Alignment.Top else Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (m.wide)
                                Column {
                                    KitText(name, role = TypeRole.Secondary, tone = TextTone.Primary, maxLines = 2)
                                    if (note != null) KitText(note, role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 2)
                                }
                            else
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    KitText(name, role = TypeRole.Secondary, tone = TextTone.Primary, maxLines = 1)
                                    if (note != null) {
                                        Spacer(Modifier.width(Space.S2.dp))
                                        KitText(note, Modifier.weight(1f, fill = false), role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 1)
                                    }
                                }
                        }
                        Spacer(Modifier.width(Space.S1.dp))
                        KitIcon(
                            if (shut) KitIcons.chevronDown else KitIcons.chevronUp,
                            Modifier.testTag(STEP_GROUP_CHEVRON_TAG),
                            size = KitIconSize.Small,
                            tone = TextTone.Secondary,
                        )
                    }
                }
            }
            val tap: @Composable (Modifier) -> Unit = { target ->
                KitTappable(
                    onClick = { toggle() },
                    modifier =
                        target.semantics {
                            if (note != null) stateDescription = note
                            if (shut) expand { toggle(); true } else collapse { toggle(); true }
                        },
                    role = KitTappableRole.Row,
                    shape = KitShapeId.Tile,
                    content = line,
                )
            }
            if (m.wide) tap(Modifier.fillMaxWidth())
            else
                Box(Modifier.fillMaxWidth().height(m.stepLine), contentAlignment = Alignment.Center) {
                    tap(Modifier.fillMaxWidth().requiredHeight(tokens.minTarget.dp))
                }
        }
    }
}

/**
 * Where a group's children hang, dp from the steps' text column: just past it, so each branch runs from the rail under
 * the group's glyph to the child's mark (D1: the children sit under the group's words, a step in).
 */
public const val STEP_GROUP_CHILD_INDENT: Int = Space.S1

/** A [KitStepList], for tests. */
public const val STEP_LIST_TAG: String = "kit-step-list"

/** Its fold row. */
public const val STEP_FOLD_ROW_TAG: String = "kit-step-fold-row"

/** A [KitStepGroup]. */
public const val STEP_GROUP_TAG: String = "kit-step-group"

/** A group's fold chevron. */
public const val STEP_GROUP_CHEVRON_TAG: String = "kit-step-group-chevron"
