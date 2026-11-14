package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitStopButton
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.request.KitHeld
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.kit.request.KitRequestAnswers
import io.github.eslamasabry.oc.ui.kit.request.KitRequestWords
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import kotlinx.coroutines.delay

/**
 * Which tree the data is (LAMP-CRAFT §4a): sub-agents that run at the same time are [Parallel] (a quiet trunk, a
 * branch to each child in its own state, never an order); the steps of one agent are a [Sequence] (one trunk coloured
 * as far as the work got, never past a waiting step).
 */
public enum class KitStepTreeKind { Parallel, Sequence }

/** Where one step or sub-agent stands. */
public enum class KitTreeStepState { NotStarted, Working, NeedsYou, Done, Failed }

/**
 * One step or sub-agent, plain values. [fact] is the quiet fact at the row's end ("4 of 6", "CHANGELOG.md"); [verb]
 * the working step's verb ("Testing"; null = the words' Working); [seconds] the time so far while working (the tree
 * counts on once a second while it is shown) or the time it took once done. [progress] is the working mark's share
 * done (null = the still dashed ring). [request] is what a child that needs the person asks; [held] an answer or a
 * stop held for 3 s; [denied] a refused request. [children] in their real order (as they started).
 */
@Immutable
public data class KitTreeStep(
    val key: Any,
    val name: String,
    val state: KitTreeStepState,
    val fact: String? = null,
    val verb: String? = null,
    val seconds: Long? = null,
    val progress: Float? = null,
    val request: KitRequest? = null,
    val held: KitHeld? = null,
    val denied: Boolean = false,
    val stoppable: Boolean = false,
    val children: List<KitTreeStep> = emptyList(),
)

/** The tree's words (the caller's; L1 owns them). */
public class KitStepTreeWords(
    public val working: String,
    public val notStarted: String,
    public val needsYou: String,
    /** "Paused until you answer". */
    public val paused: String,
    public val denied: String,
    public val failed: String,
    /** "+2 more inside". */
    public val moreInside: (Int) -> String,
    /** "+3 more". */
    public val more: (Int) -> String,
    /** "34s", "1:13", in the phone's digits. */
    public val duration: (Long) -> String,
    /** "Stop “‹name›”". */
    public val stop: (String) -> String,
    /** What TalkBack says for a step: "‹child›, sub-agent of ‹parent›, ‹state›, ‹fact›", [said] being its state word. */
    public val spoken: (step: KitTreeStep, said: String) -> String,
    /** The fold chevron's names: "Show sub-agents" / "Hide sub-agents". */
    public val unfold: String,
    public val fold: String,
    public val request: KitRequestWords,
)

/** What a tap on the tree does; the step is the one tapped. */
public class KitStepTreeActions(
    public val onOpen: (KitTreeStep) -> Unit = {},
    public val onStop: (KitTreeStep) -> Unit = {},
    public val onAllow: (KitTreeStep) -> Unit = {},
    public val onDeny: (KitTreeStep) -> Unit = {},
    public val onAnswer: (KitTreeStep) -> Unit = {},
    public val onUndo: (KitTreeStep) -> Unit = {},
    public val onHoldEnd: (KitTreeStep) -> Unit = {},
)

/** The tree's measures (LAMP-CRAFT §4b), in dp. */
public object StepTreeDims {
    /** Line weight in Solid, Glass and Soft. */
    public const val LINE: Float = 1.5f // NV: the spec's 1.5 dp hairline (StepTreeDims.LINE is the token)

    /** Brutal's ink line. */
    public const val LINE_BRUTAL: Float = 2f // NV: Brutal's 2 dp ink (LAMP-CRAFT §4b)

    /** Paper's rule. */
    public const val LINE_PAPER: Float = 1f // NV: Paper's 1 dp rule (LAMP-CRAFT §4b)

    /** The guide's ink share (ink at 22 %). */
    public const val GUIDE_ALPHA: Float = 0.22f // NV: --guide, ink at 22 % (LAMP-CRAFT §4a)

    /** A parallel branch's rounded corner off the trunk (`Space.S2`). */
    public const val BRANCH_R: Int = Space.S2

    /** A sequence's S-bend between columns (`Space.S4`). */
    public const val BEND: Int = Space.S4

    /**
     * A nested row: 12 dp above and below a 24 dp line, the kit's 48 dp touch target (owner 2026-11-14: the rows keep
     * the kit's touch rule; the craft spec's 44 dp would be cut by `kitTappable`'s minimum anyway).
     */
    public const val ROW: Int = Space.S3 * 2 + Space.S6

    /** The conversation's (the header's) mark. */
    public const val ROOT_MARK: Int = Space.S6

    /** A nested mark (`MarkSize.Mini`). */
    public const val MARK: Int = TaskMarkRules.MINI_DP

    /** The gutter to the conversation's mark. */
    public const val GUTTER: Int = Space.S4

    /** Mark → text (`Space.S3`). */
    public const val GAP: Int = Space.S3

    /** A child's mark under the start of the parent's title: 16 + 24 + 12. */
    public const val CHILD_INDENT: Int = GUTTER + ROOT_MARK + GAP

    /** A grandchild's under the child's name: 52 + 11 + 12. */
    public const val GRAND_INDENT: Int = CHILD_INDENT + MARK + GAP

    /** Nested levels shown; deeper ones fold into "+N more inside". */
    public const val MAX_DEPTH: Int = 2

    /** Children shown before "+N more". */
    public const val MAX_SHOWN: Int = 5
}

/** A line's tone: the quiet guide, the accent (working / progress), the attention (waiting for the person). */
public enum class StepTone { Guide, Accent, Attention }

/** One mark as the tree's lines see it: its centre and drawn radius in the tree's own pixels. */
@Immutable
public data class StepMark(val key: Any, val parent: Any?, val depth: Int, val state: KitTreeStepState, val center: Offset, val radius: Float) {
    val top: Float get() = center.y - radius
    val bottom: Float get() = center.y + radius
}

/** One piece of a stroke, ending at [to]. */
public sealed interface StepSeg {
    public val to: Offset

    public data class Line(override val to: Offset) : StepSeg

    public data class Quad(val control: Offset, override val to: Offset) : StepSeg

    public data class Cubic(val c1: Offset, val c2: Offset, override val to: Offset) : StepSeg
}

/** One stroke of the tree: from [from] through [segs], in one [tone]. [toKey] is the mark it ends on, if any. */
@Immutable
public data class StepStroke(val from: Offset, val segs: List<StepSeg>, val tone: StepTone, val toKey: Any? = null) {
    val end: Offset get() = segs.lastOrNull()?.to ?: from
}

/** The tree's pure rules. */
public object StepTreeRules {
    /** Root key the header's mark registers under. */
    public val ROOT: Any = RootKey

    private object RootKey

    /**
     * The parallel tree: for every mark with children, a quiet trunk from its bottom down to [branchR] above its last
     * child's centre, and for each child a branch curving off the trunk ([branchR] corner) and running straight to the
     * child mark's near edge, in the child's tone. Mirrors by itself (a child to the trunk's left gets a branch to the
     * left).
     */
    public fun parallel(marks: List<StepMark>, branchR: Float): List<StepStroke> {
        val out = mutableListOf<StepStroke>()
        for (p in marks) {
            val kids = marks.filter { it.parent == p.key }
            if (kids.isEmpty()) continue
            val x = p.center.x
            val last = kids.last()
            out += StepStroke(Offset(x, p.bottom), listOf(StepSeg.Line(Offset(x, last.center.y - branchR))), StepTone.Guide)
            for (k in kids) {
                val dir = if (k.center.x >= x) 1f else -1f
                val edge = k.center.x - dir * k.radius
                out +=
                    StepStroke(
                        Offset(x, k.center.y - branchR),
                        listOf(StepSeg.Quad(Offset(x, k.center.y), Offset(x + dir * branchR, k.center.y)), StepSeg.Line(Offset(edge, k.center.y))),
                        branchTone(k.state),
                        k.key,
                    )
            }
        }
        return out
    }

    /** A branch is accent while its child works, amber while it waits for the person, else quiet. */
    public fun branchTone(state: KitTreeStepState): StepTone =
        when (state) {
            KitTreeStepState.Working -> StepTone.Accent
            KitTreeStepState.NeedsYou -> StepTone.Attention
            else -> StepTone.Guide
        }

    /**
     * The sequence: one trunk through every mark in the order shown, from each mark's bottom to the next one's top; into
     * a deeper column it runs down and S-bends ([bend] tall, vertical tangents) into the column, back out it S-bends
     * first. The first [accentSegments] pieces are accent; the rest quiet. A last piece bends back toward the first
     * mark's column under the last mark.
     */
    public fun sequence(marks: List<StepMark>, bend: Float): List<StepStroke> {
        if (marks.size < 2) return emptyList()
        val n = accentSegments(marks.map { it.state })
        val out = mutableListOf<StepStroke>()
        for (i in 1 until marks.size) {
            val p = marks[i - 1]
            val m = marks[i]
            val tone = if (i - 1 < n) StepTone.Accent else StepTone.Guide
            out +=
                when {
                    kotlin.math.abs(p.center.x - m.center.x) < HALF_PX ->
                        StepStroke(Offset(p.center.x, p.bottom), listOf(StepSeg.Line(Offset(m.center.x, m.top))), tone, m.key)
                    m.depth > p.depth -> {
                        val ya = maxOf(p.bottom, m.top - bend)
                        val mid = (ya + m.top) / 2f
                        StepStroke(
                            Offset(p.center.x, p.bottom),
                            listOf(StepSeg.Line(Offset(p.center.x, ya)), StepSeg.Cubic(Offset(p.center.x, mid), Offset(m.center.x, mid), Offset(m.center.x, m.top))),
                            tone,
                            m.key,
                        )
                    }
                    else -> {
                        val yb = minOf(m.top, p.bottom + bend)
                        val mid = (p.bottom + yb) / 2f
                        StepStroke(
                            Offset(p.center.x, p.bottom),
                            listOf(StepSeg.Cubic(Offset(p.center.x, mid), Offset(m.center.x, mid), Offset(m.center.x, yb)), StepSeg.Line(Offset(m.center.x, m.top))),
                            tone,
                            m.key,
                        )
                    }
                }
        }
        val last = marks.last()
        val first = marks.first()
        if (kotlin.math.abs(last.center.x - first.center.x) >= HALF_PX) {
            val mid = last.bottom + bend / 2f
            out += StepStroke(Offset(last.center.x, last.bottom), listOf(StepSeg.Cubic(Offset(last.center.x, mid), Offset(first.center.x, mid), Offset(first.center.x, last.bottom + bend))), StepTone.Guide)
        }
        return out
    }

    /**
     * How many trunk pieces (after the first mark) are accent: through every done step, into the step working or waiting
     * now, and no further; a step not started (or failed) stops it before.
     */
    public fun accentSegments(states: List<KitTreeStepState>): Int {
        var n = 0
        for (s in states.drop(1)) {
            when (s) {
                KitTreeStepState.Done -> n++
                KitTreeStepState.Working, KitTreeStepState.NeedsYou -> return n + 1
                else -> return n
            }
        }
        return n
    }

    /** The five most urgent children (or all with [all]), kept in their real order. */
    public fun shown(children: List<KitTreeStep>, all: Boolean = false): List<KitTreeStep> {
        if (all || children.size <= StepTreeDims.MAX_SHOWN) return children
        val keep = children.withIndex().sortedWith(compareBy<IndexedValue<KitTreeStep>>({ urgency(it.value.state) }, { -(it.value.progress ?: -1f) }, { it.index }))
            .take(StepTreeDims.MAX_SHOWN).map { it.index }.toSet()
        return children.filterIndexed { i, _ -> i in keep }
    }

    private fun urgency(s: KitTreeStepState): Int =
        when (s) {
            KitTreeStepState.NeedsYou -> 0
            KitTreeStepState.Failed -> 1
            KitTreeStepState.Working -> 2
            KitTreeStepState.Done -> 3
            KitTreeStepState.NotStarted -> 4
        }

    /** Open while any step under it works, needs the person or failed; folded once all of them are done. */
    public fun open(children: List<KitTreeStep>): Boolean =
        children.any { it.state == KitTreeStepState.Working || it.state == KitTreeStepState.NeedsYou || it.state == KitTreeStepState.Failed || open(it.children) }

    /** Every step under [step] (the "+N more inside" count past the depth cap). */
    public fun descendants(step: KitTreeStep): Int = step.children.sumOf { 1 + descendants(it) }

    /** The time a step shows: its [KitTreeStep.seconds] plus the whole seconds counted since it was given. */
    public fun seconds(step: KitTreeStep, counted: Int): Long? =
        step.seconds?.let { if (step.state == KitTreeStepState.Working) it + counted else it }

    private const val HALF_PX: Float = 0.5f // NV: two marks within half a pixel share a column
}

/** What the header (the conversation's own row) gets from its tree. */
@Stable
public interface KitStepTreeScope {
    /** The block is folded to its header ("N sub-agents"). */
    public val folded: Boolean

    /** Direct children. */
    public val count: Int

    /** Put on the header's 24 dp mark: the trunk starts under it. */
    public fun Modifier.stepTreeRoot(): Modifier

    /** The fold chevron at the end of the header's title line. */
    @Composable
    public fun FoldChevron()
}

/** The tree's own choices, kept while the panel lives: a fold the person chose, "+N more" shown, a request closed. */
@Stable
public class KitStepTreeState {
    internal val fold: SnapshotStateMap<Any, Boolean> = mutableStateMapOf()
    internal val all: SnapshotStateMap<Any, Boolean> = mutableStateMapOf()
    internal val closed: SnapshotStateMap<Any, Boolean> = mutableStateMapOf()
}

@Composable
public fun rememberKitStepTreeState(): KitStepTreeState = remember { KitStepTreeState() }

/**
 * A conversation and its sub-agents (or one agent's steps) as one block drawn as a step tree (LAMP-CRAFT §4a, §4b).
 * [header] is the conversation's own row: it puts [KitStepTreeScope.stepTreeRoot] on its mark and may show the
 * [KitStepTreeScope.FoldChevron]. Under it, each step is one 44 dp line: its 11 dp mark (`KitTaskMark` Mini, the step
 * look), the name, a quiet fact, and at the end the working verb in accent with a live timer, the time it took, "Not
 * started", or "Needs you". A child keeps its real order; one that needs the person says "Paused until you answer",
 * its request's words and Allow once / Deny in place. At most two levels ("+N more inside"), at most five children
 * shown ("+N more"). The lines meet each mark's ring exactly and mirror in Arabic.
 *
 * States: open, folded (by itself when all are done, or by the chevron), a child waiting, held answers and stops
 * Text: frame (name Label in ink, fact and state Caption, tabular timer)
 * Cap: none of its own (it fills the panel row; each request is Reading-capped)
 * Motion: fold and unfold the height only on `spatial`; marks cross-fade QUICK; nothing else; the timer's text changes
 *   once a second only while shown; Off = instant
 * Replaces: the rings' indented child rows (DockPanel's ChildRows) for the edge light
 */
@Composable
public fun KitStepTree(
    kind: KitStepTreeKind,
    steps: List<KitTreeStep>,
    words: KitStepTreeWords,
    modifier: Modifier = Modifier,
    actions: KitStepTreeActions = KitStepTreeActions(),
    state: KitStepTreeState = rememberKitStepTreeState(),
    header: @Composable KitStepTreeScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val style = LocalKitStyle.current.style
    val specs = KitMotion.specs
    val marks = remember { mutableStateMapOf<Any, Rect>() }
    val root = remember { CoordsHolder() }
    val autoOpen = StepTreeRules.open(steps)
    val open = state.fold[StepTreeRules.ROOT] ?: autoOpen
    val shownFraction = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        val target = if (open) 1f else 0f
        if (shownFraction.targetValue != target) shownFraction.animateTo(target, specs.spatial())
    }
    // The live timer: one count a second while any shown step is working with a time (no frames in between).
    val ticking = steps.any { it.ticks() || it.children.any { c -> c.ticks() } }
    var counted by remember { mutableIntStateOf(0) }
    LaunchedEffect(steps, ticking) {
        counted = 0
        if (ticking) while (true) {
            delay(SECOND_MS)
            counted++
        }
    }
    val scope =
        remember(state, open, steps.size, words) {
            TreeScope(!open, steps.size, { c -> register(marks, root, StepTreeRules.ROOT, c) }) {
                state.fold[StepTreeRules.ROOT] = !open
            }.also { it.words = words }
        }
    val lineW = when (style) {
        Style.Brutal -> StepTreeDims.LINE_BRUTAL
        Style.Paper -> StepTreeDims.LINE_PAPER
        else -> StepTreeDims.LINE
    }
    val guide = if (style == Style.Brutal || style == Style.Paper) colors.text1 else colors.text1.copy(alpha = StepTreeDims.GUIDE_ALPHA)
    // Paper's progress is ink, not accent (LAMP-CRAFT §4a).
    val accent = if (style == Style.Paper) colors.text1 else colors.accent
    val attention = colors.attention
    // Read in composition: the person's "+N more" choice recomposes the lines.
    val flat = flatten(steps, open || shownFraction.value > 0f, state)
    Column(
        modifier
            .testTag(STEP_TREE_TAG)
            .onPlaced { root.coords = it }
            .drawBehind {
                val fraction = shownFraction.value
                if (fraction <= 0f) return@drawBehind
                val known = buildList {
                    marks[StepTreeRules.ROOT]?.let { add(StepMark(StepTreeRules.ROOT, null, 0, KitTreeStepState.Working, it.center, it.width * TaskMarkRules.MINI_OUTER_SHARE)) }
                    for (f in flat) marks[f.step.key]?.let { add(StepMark(f.step.key, f.parent ?: StepTreeRules.ROOT, f.depth, f.step.state, it.center, it.width * TaskMarkRules.MINI_OUTER_SHARE)) }
                }
                if (known.size < 2) return@drawBehind
                val strokes =
                    if (kind == KitStepTreeKind.Parallel) StepTreeRules.parallel(known, StepTreeDims.BRANCH_R.dp.toPx())
                    else StepTreeRules.sequence(known, StepTreeDims.BEND.dp.toPx())
                val stroke = Stroke(lineW.dp.toPx(), cap = StrokeCap.Round)
                val bottom = size.height
                for (s in strokes) {
                    if (s.from.y > bottom) continue
                    val color = when (s.tone) {
                        StepTone.Guide -> guide
                        StepTone.Accent -> accent
                        StepTone.Attention -> attention
                    }
                    drawPath(s.toPath(), color.copy(alpha = color.alpha * fraction), style = stroke)
                }
            },
    ) {
        scope.header()
        // The children: height only, on spatial; not composed at all while folded and still.
        if (open || shownFraction.value > 0f || shownFraction.isRunning) {
            Box(
                Modifier
                    .clipToBounds()
                    .layout { m, c ->
                        val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        val h = (p.height * shownFraction.value).toInt()
                        layout(p.width, h) { p.place(0, 0) }
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    for (f in flat) {
                        key(f.step.key) {
                            StepLine(f, words, actions, counted, state, register = { c -> register(marks, root, f.step.key, c) })
                        }
                        if (f.moreInside > 0) MoreLine(words.moreInside(f.moreInside), StepTreeDims.GRAND_INDENT + StepTreeDims.MARK + StepTreeDims.GAP) { actions.onOpen(f.step) }
                        if (f.moreAfter > 0) {
                            val parentKey = f.parent ?: StepTreeRules.ROOT
                            MoreLine(words.more(f.moreAfter), indentOf(f.depth) + StepTreeDims.MARK + StepTreeDims.GAP) { state.all[parentKey] = true }
                        }
                    }
                }
            }
        }
    }
}

private fun KitTreeStep.ticks(): Boolean = state == KitTreeStepState.Working && seconds != null

/** The registry's coordinates of the tree's root, set when it is placed. */
private class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

private fun register(marks: SnapshotStateMap<Any, Rect>, root: CoordsHolder, key: Any, c: LayoutCoordinates) {
    val r = root.coords ?: return
    if (!r.isAttached || !c.isAttached) return
    val rect = r.localBoundingBoxOf(c, clipBounds = false)
    if (marks[key] != rect) marks[key] = rect
}

private class TreeScope(
    override val folded: Boolean,
    override val count: Int,
    private val onRoot: (LayoutCoordinates) -> Unit,
    private val toggle: () -> Unit,
) : KitStepTreeScope {
    var words: KitStepTreeWords? = null

    override fun Modifier.stepTreeRoot(): Modifier = this.onGloballyPositioned { onRoot(it) }

    @Composable
    override fun FoldChevron() {
        val label = words?.let { if (folded) it.unfold else it.fold }.orEmpty()
        Box(
            Modifier
                .size(StepTreeDims.ROW.dp)
                .testTag(STEP_TREE_FOLD_TAG)
                .kitTappable(onClick = toggle, onClickLabel = label)
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    onClick { toggle(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            KitIcon(if (folded) KitIcons.chevronDown else KitIcons.chevronUp, size = KitIconSize.Small, tone = TextTone.Secondary)
        }
    }
}

/** One shown line of the tree, with where it hangs and what follows it. */
private class FlatStep(val step: KitTreeStep, val parent: Any?, val depth: Int, val parentName: String?) {
    /** "+N more inside" after this line (a step at the depth cap with steps of its own). */
    var moreInside: Int = 0

    /** "+N more" after this line (the last shown child of a parent with more than five). */
    var moreAfter: Int = 0
}

private fun flatten(steps: List<KitTreeStep>, open: Boolean, state: KitStepTreeState): List<FlatStep> {
    if (!open) return emptyList()
    val out = mutableListOf<FlatStep>()
    fun level(list: List<KitTreeStep>, parent: Any?, parentName: String?, depth: Int) {
        val parentKey = parent ?: StepTreeRules.ROOT
        val shown = StepTreeRules.shown(list, state.all[parentKey] == true)
        for (s in shown) {
            val f = FlatStep(s, parent, depth, parentName)
            out += f
            if (depth >= StepTreeDims.MAX_DEPTH) f.moreInside = StepTreeRules.descendants(s)
            else level(s.children, s.key, s.name, depth + 1)
        }
        if (shown.size < list.size) out.lastOrNull { it.depth == depth && it.parent == parent }?.moreAfter = list.size - shown.size
    }
    level(steps, null, null, 1)
    return out
}

private fun indentOf(depth: Int): Int = if (depth <= 1) StepTreeDims.CHILD_INDENT else StepTreeDims.GRAND_INDENT

/** One 44 dp line: mark, name, fact, state, stop; under a waiting child its pause words and its answers. */
@Composable
private fun StepLine(
    f: FlatStep,
    words: KitStepTreeWords,
    actions: KitStepTreeActions,
    counted: Int,
    state: KitStepTreeState,
    register: (LayoutCoordinates) -> Unit,
) {
    val step = f.step
    val waiting = step.state == KitTreeStepState.NeedsYou && step.request != null
    val requestOpen = waiting && state.closed[step.key] != true
    val said = when {
        step.denied -> words.denied
        step.state == KitTreeStepState.Working -> step.verb ?: words.working
        step.state == KitTreeStepState.NeedsYou -> words.needsYou
        step.state == KitTreeStepState.Failed -> words.failed
        step.state == KitTreeStepState.NotStarted -> words.notStarted
        else -> step.seconds?.let(words.duration) ?: ""
    }
    val indent = indentOf(f.depth)
    Column(Modifier.fillMaxWidth().testTag(STEP_ROW_TAG)) {
        Row(Modifier.fillMaxWidth().heightIn(min = StepTreeDims.ROW.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = StepTreeDims.ROW.dp)
                    .kitTappable(onClick = { actions.onOpen(step) }, role = io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole.Row)
                    // TalkBack: "‹child›, sub-agent of ‹parent›, ‹state›, ‹fact›" (the caller's words).
                    .semantics { contentDescription = words.spoken(step, said) }
                    .padding(start = indent.dp, end = Space.S2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.testTag(STEP_MARK_TAG).onGloballyPositioned(register).clearAndSetSemantics {}) {
                    KitTaskMark(step.state.taskState(), label = said, size = KitMarkSize.Mini, progress = step.progress, stepLook = true)
                }
                Box(Modifier.width(StepTreeDims.GAP.dp))
                StepWords(step, said, counted, words)
            }
            when {
                waiting && step.held == null -> NeedsYouToggle(words.needsYou, requestOpen) { state.closed[step.key] = requestOpen }
                step.stoppable && step.state == KitTreeStepState.Working && step.held == null ->
                    KitStopButton(words.stop(step.name), { actions.onStop(step) }, Modifier.padding(end = Space.S2.dp), compact = true)
                else -> Box(Modifier.width(Space.S2.dp))
            }
        }
        val under = (indent + StepTreeDims.MARK + StepTreeDims.GAP).dp
        if (requestOpen || (waiting && step.held != null)) {
            val request = step.request!!
            Column(Modifier.fillMaxWidth().padding(start = under, end = Space.S4.dp, bottom = Space.S3.dp), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                KitText(words.paused, role = TypeRole.Caption, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 2)
                KitText(request.words, role = TypeRole.Secondary, tone = TextTone.Primary, kind = KitTextKind.Frame, maxLines = 3)
                KitRequestAnswers(
                    request,
                    step.name,
                    words.request,
                    onAllow = { actions.onAllow(step) },
                    onDeny = { actions.onDeny(step) },
                    onAnswer = { actions.onAnswer(step) },
                    held = step.held,
                    onUndo = { actions.onUndo(step) },
                    onHoldEnd = { actions.onHoldEnd(step) },
                    showWords = false,
                    modifier = Modifier.padding(top = Space.S1.dp),
                )
            }
        } else if (step.held != null) {
            // A stop held for 3 s: its receipt under the line, draining.
            KitReceipt(
                KitReceiptState.Confirmed,
                Modifier.fillMaxWidth().padding(start = under, end = Space.S4.dp, bottom = Space.S3.dp),
                label = step.held.label,
                onUndo = { actions.onUndo(step) },
                countdownMs = MotionTokens.HELD_ANSWER,
                onHoldEnd = { actions.onHoldEnd(step) },
            )
        }
    }
}

/**
 * The line's words: the name first and kept; the fact, quiet, dropped first when the row is narrow; the state at the
 * end (verb + timer in accent, the time it took, "Not started", or the state word).
 */
@Composable
private fun StepWords(step: KitTreeStep, said: String, counted: Int, words: KitStepTreeWords) {
    val quiet = step.state == KitTreeStepState.NotStarted
    val stateText =
        when {
            step.state == KitTreeStepState.Working -> {
                val t = StepTreeRules.seconds(step, counted)
                if (t == null) said else "$said ${words.duration(t)}"
            }
            step.state == KitTreeStepState.NeedsYou && step.request != null -> null // the toggle at the end says it
            else -> said.ifBlank { null }
        }
    val stateTone = when {
        step.state == KitTreeStepState.Working && !step.denied -> TextTone.Accent
        step.state == KitTreeStepState.NeedsYou -> TextTone.Attention
        else -> TextTone.Secondary
    }
    Layout(
        content = {
            KitText(step.name, role = TypeRole.Label, tone = if (quiet) TextTone.Secondary else TextTone.Primary, kind = KitTextKind.Frame, maxLines = 1)
            if (step.fact != null) KitText(step.fact, role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1, tabular = true) else Box {}
            if (stateText != null) KitText(stateText, role = TypeRole.Caption, tone = stateTone, kind = KitTextKind.Frame, maxLines = 1, tabular = true) else Box {}
        },
        modifier = Modifier.fillMaxWidth(),
    ) { ms, c ->
        val gap = Space.S2.dp.roundToPx()
        val max = c.maxWidth
        val loose = c.copy(minWidth = 0)
        val st = ms[2].measure(loose)
        val stW = if (st.width > 0) st.width + gap else 0
        val name = ms[0].measure(loose.copy(maxWidth = (max - stW).coerceAtLeast(0)))
        val left = max - stW - name.width
        val factFull = ms[1].measure(loose)
        // The fact goes first, never the name: it shows only where it fits whole.
        val fact = if (factFull.width > 0 && factFull.width + gap <= left) factFull else null
        val h = maxOf(name.height, st.height, fact?.height ?: 0)
        layout(max, h) {
            name.placeRelative(0, (h - name.height) / 2)
            val stX = max - st.width
            st.placeRelative(stX, (h - st.height) / 2)
            fact?.placeRelative(stX - (if (st.width > 0) gap else 0) - fact.width, (h - fact.height) / 2)
        }
    }
}

/** "Needs you ⌄" at a waiting child's end: opens or closes its request in place. */
@Composable
private fun NeedsYouToggle(label: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = StepTreeDims.ROW.dp)
            .kitTappable(onClick = onToggle, ink = TextTone.Attention)
            .padding(horizontal = Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        KitText(label, role = TypeRole.Caption, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 1)
        KitIcon(if (open) KitIcons.chevronUp else KitIcons.chevronDown, size = KitIconSize.Tiny, tone = TextTone.Attention)
    }
}

/** "+N more inside" / "+N more": a quiet line that opens the rest. */
@Composable
private fun MoreLine(text: String, startDp: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = StepTreeDims.ROW.dp)
            .kitTappable(onClick = onClick, role = io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole.Row)
            .padding(start = startDp.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        KitText(text, role = TypeRole.Label, tone = TextTone.Secondary, kind = KitTextKind.Frame, maxLines = 1)
    }
}

private fun KitTreeStepState.taskState(): KitTaskState =
    when (this) {
        KitTreeStepState.NotStarted -> KitTaskState.Waiting
        KitTreeStepState.Working -> KitTaskState.Working
        KitTreeStepState.NeedsYou -> KitTaskState.NeedsYou
        KitTreeStepState.Done -> KitTaskState.Done
        KitTreeStepState.Failed -> KitTaskState.Failed
    }

private fun StepStroke.toPath(): Path =
    Path().apply {
        moveTo(from.x, from.y)
        for (s in segs) {
            when (s) {
                is StepSeg.Line -> lineTo(s.to.x, s.to.y)
                is StepSeg.Quad -> quadraticTo(s.control.x, s.control.y, s.to.x, s.to.y)
                is StepSeg.Cubic -> cubicTo(s.c1.x, s.c1.y, s.c2.x, s.c2.y, s.to.x, s.to.y)
            }
        }
    }

private const val SECOND_MS: Long = 1_000L // NV: the timer changes its text once a second

/** The tree, for tests. */
public const val STEP_TREE_TAG: String = "kit-step-tree"

/** One step's line. */
public const val STEP_ROW_TAG: String = "kit-step-row"

/** One step's 11 dp mark. */
public const val STEP_MARK_TAG: String = "kit-step-mark"

/** The fold chevron. */
public const val STEP_TREE_FOLD_TAG: String = "kit-step-tree-fold"
