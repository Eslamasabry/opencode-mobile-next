package io.github.eslamasabry.oc.ui.gate.flicker

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * The rules the frame recorder reads a test's frames with (docs: the flicker-rule design, owner 2026-11-20 "No release
 * until we make sure no flickering systematically"). Each names what a person would see in a frame-by-frame read.
 */
public enum class FlickerRule {
    /** A node appears and is gone again within [FlickerLimits.blinkFrames] frames. */
    BLINK,

    /** The same words on two nodes for a moment, then on one again (F2: the first message shown twice). */
    DOUBLE,

    /** Words leave and come back, or a node moves away and comes back, within [FlickerLimits.returnFrames] (F3, F8). */
    ABA,

    /** A node's place moves more than [FlickerLimits.jumpDp] in one frame, still before and after (F1b, F5). */
    JUMP,

    /** The first frame drawn lays a node out more than [FlickerLimits.jumpDp] away from the settled page (F1b). */
    FIRST,

    /** A step of the test recomposed more often than its ceiling (the keyboard probe, generalised). */
    RECOMPOSE,
}

/** How the rules measure. The defaults are the design's: N = 3 frames (about 50 ms), X = 24 dp. */
public data class FlickerLimits(
    val blinkFrames: Int = BLINK_FRAMES,
    val returnFrames: Int = RETURN_FRAMES,
    val jumpDp: Float = JUMP_DP,
    val abaDp: Float = ABA_DP,
    val stillDp: Float = STILL_DP,
) {
    private companion object {
        const val BLINK_FRAMES = 3
        const val RETURN_FRAMES = 60
        const val JUMP_DP = 24f
        const val ABA_DP = 8f
        const val STILL_DP = 1f
    }
}

/** How a mode judges what the recorder found. */
public enum class FlickerMode {
    /** Nothing is recorded. */
    Off,

    /** Everything found is written down (build/flicker, and on CI native/reports/flicker-*.json); nothing fails. */
    Report,

    /** A finding fails the test unless the module's flicker-baseline.tsv holds it or the test allows it by name. */
    FailNew,

    /** Any finding the test does not allow by name fails it (the planted tests, and modules with an empty baseline). */
    Fail,
}

/**
 * How one [kitUiTest] runs: the recorder's [mode] (null: the module's, from `-Poc.flicker` or its baseline file), its
 * [limits], a ceiling of recompositions per test step (null: none), and the test's [timeout].
 */
public data class KitUiOptions(
    val mode: FlickerMode? = null,
    val limits: FlickerLimits = FlickerLimits(),
    val recompositionsPerStep: Int? = null,
    val timeout: Duration = Duration.INFINITE,
)

/** One thing the recorder saw: the rule, the node's words (or `#tag`), the frame it started at and how it read. */
public data class Finding(val rule: FlickerRule, val label: String, val frame: Int, val detail: String) {
    /** The words with digits and spacing evened out: the same fault keeps the same line across runs and clocks. */
    public val key: String
        get() = flickerKey(label)

    override fun toString(): String = "$rule $detail"
}

/**
 * Intended motion, declared by name in the test that shows it: a sheet sliding in, a pager swipe. Every entry
 * carries the reason a reviewer reads; [match] is found in the finding's words or tag.
 */
public class Allow private constructor(
    public val rule: FlickerRule,
    public val match: String,
    public val reason: String,
) {
    init {
        require(match.isNotBlank()) { "an allow-list entry names the node it allows" }
        require(reason.isNotBlank()) { "an allow-list entry for \"$match\" says why the motion is intended" }
    }

    internal fun covers(finding: Finding): Boolean =
        finding.rule == rule && (match in finding.label || match in finding.key)

    override fun toString(): String = "$rule \"$match\": $reason"

    public companion object {
        public fun blink(match: String, reason: String): Allow = Allow(FlickerRule.BLINK, match, reason)

        public fun double(match: String, reason: String): Allow = Allow(FlickerRule.DOUBLE, match, reason)

        public fun aba(match: String, reason: String): Allow = Allow(FlickerRule.ABA, match, reason)

        public fun jump(match: String, reason: String): Allow = Allow(FlickerRule.JUMP, match, reason)

        public fun first(match: String, reason: String): Allow = Allow(FlickerRule.FIRST, match, reason)

        public fun recompose(match: String, reason: String): Allow = Allow(FlickerRule.RECOMPOSE, match, reason)
    }
}

/** A node's box in the window, in dp. */
public data class DpBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** The largest distance any edge moved from [other]. */
    public fun edgeMove(other: DpBox): Float =
        maxOf(abs(left - other.left), abs(top - other.top), abs(right - other.right), abs(bottom - other.bottom))

    /** How far the box's place (its top-left corner) moved from [other]. */
    public fun placeMove(other: DpBox): Float = max(abs(left - other.left), abs(top - other.top))

    override fun toString(): String =
        "[${left.roundToInt()},${top.roundToInt()},${right.roundToInt()},${bottom.roundToInt()}]dp"
}

/**
 * What one frame showed of one semantics node: its [label] (its words, else its description, else `#tag`), whether the
 * words are text, its [box], the scroll containers around it (innermost first), and [control], the semantics id of the
 * nearest node at or above it that answers a click (null: none).
 */
public data class FrameNode(
    val id: Int,
    val label: String,
    val tag: String?,
    val isText: Boolean,
    val box: DpBox,
    val scrollers: List<Int>,
    val control: Int? = null,
)

/** The nodes of one picture, in tree order, and the scroll position of every scroll container in it. */
public class Picture(public val nodes: List<FrameNode>, public val scrolls: Map<Int, Float>) {
    internal val byId: Map<Int, FrameNode> by lazy { nodes.associateBy { it.id } }

    /** How many text nodes carry exactly these words. */
    internal val texts: Map<String, Int> by lazy { nodes.filter { it.isText }.groupingBy { it.label }.eachCount() }

    /** How many nodes carry these words, digits evened out. */
    internal val keys: Map<String, Int> by lazy { nodes.groupingBy { flickerKey(it.label) }.eachCount() }
}

/** One frame the test clock produced: its number in the test, its clock time and its picture (shared if unchanged). */
public class RecordedFrame(public val index: Int, public val timeMs: Long, public val picture: Picture) {
    public val nodes: List<FrameNode>
        get() = picture.nodes

    override fun toString(): String = "frame $index ($timeMs ms)"
}

private val DIGITS = Regex("""\d+""")
private val SPACE = Regex("""\s+""")
private const val KEY_CHARS = 60

/** The words a baseline line keeps for [label]: digits as `#`, spacing as one space, at most 60 characters. */
public fun flickerKey(label: String): String = label.replace(DIGITS, "#").replace(SPACE, " ").trim().take(KEY_CHARS)
