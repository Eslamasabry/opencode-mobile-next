package io.github.eslamasabry.oc.ui.gate.flicker

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Keeps the frames of one test and reads them with [FlickerRules]. Frames arrive from the test clock ([add]); the
 * test's own steps (setting content, finding a node, waiting) arrive as [mark]s: the frame before the first mark
 * after the content showed is the settled page, and the recompositions between two marks are one step's.
 *
 * Only a short run of frames is kept: when it grows past [CAP] the rules read it and all but the last [keep] frames are
 * dropped (every pattern fits in that overlap), so a test that runs the clock for minutes stays small.
 */
public class FrameRecorder(private val limits: FlickerLimits = FlickerLimits()) {
    private val rules = FlickerRules(limits)
    private val frames = ArrayList<RecordedFrame>()
    private val found = LinkedHashMap<String, Finding>()
    private val changeLog = ArrayList<Long>()
    private val marks = ArrayList<Int>()
    private val keep = limits.returnFrames + limits.blinkFrames + KEEP_SPARE
    private var picture: Picture? = null

    /** The first frame that showed a labelled node. */
    public var first: RecordedFrame? = null
        private set

    /** The page once the first step after it began; null until then. */
    public var settled: RecordedFrame? = null
        private set

    /** Frames recorded so far. */
    public val count: Int
        get() = changeLog.size

    /** One frame: its clock time, the recomposer's change count and its picture (null: the last frame's again). */
    @Synchronized
    public fun add(timeMs: Long, changes: Long, fresh: Picture?) {
        val now = fresh ?: picture ?: Picture(emptyList(), emptyMap())
        picture = now
        val frame = RecordedFrame(count, timeMs, now)
        changeLog += changes
        frames += frame
        if (first == null && now.nodes.isNotEmpty()) first = frame
        if (frames.size > CAP) {
            keepAll(rules.run(frames, final = false))
            frames.subList(0, frames.size - keep).clear()
        }
    }

    /** The test took a step (set content, looked for a node, waited). */
    @Synchronized
    public fun mark() {
        marks += count
        if (settled == null && first != null) settled = frames.lastOrNull()
    }

    /** Everything found, the last frames read as the end of the run; [ceiling] bounds each step's recompositions. */
    @Synchronized
    public fun finish(ceiling: Int?): List<Finding> {
        keepAll(rules.run(frames, final = true))
        val start = first
        val end = settled ?: frames.lastOrNull()
        if (start != null && end != null) rules.firstVsSettled(start, end)?.let { keepAll(listOf(it)) }
        if (ceiling != null) keepAll(steps(ceiling))
        return found.values.toList()
    }

    private fun keepAll(findings: List<Finding>) {
        findings.forEach { found.putIfAbsent("${it.rule}@${it.frame}", it) }
    }

    /** The steps between marks that recomposed more than [ceiling] times (not counting the first composition). */
    private fun steps(ceiling: Int): List<Finding> {
        val points = (listOf(0) + marks + count).distinct().sorted()
        return points.zipWithNext().filter { (a, b) -> b > a }.mapIndexedNotNull { i, (a, b) ->
            val n = changeLog[b - 1] - changeLog[maxOf(a - 1, 0)]
            if (n > ceiling) {
                val what = "step ${i + 1} (frames $a to ${b - 1}) recomposed $n times, the ceiling is $ceiling"
                Finding(FlickerRule.RECOMPOSE, "step ${i + 1}", b - 1, what)
            } else {
                null
            }
        }
    }

    public companion object {
        private const val CAP = 600
        private const val KEEP_SPARE = 8
        private const val NO_TEXT = ""

        /**
         * The picture of the window as the semantics trees show it: every placed node with words, a description or
         * a test tag, in tree order, with its box in dp (at the node's own density) and the scroll containers around
         * it. Call it on the UI thread.
         */
        public fun read(roots: List<SemanticsNode>): Picture {
            val nodes = ArrayList<FrameNode>()
            val scrolls = HashMap<Int, Float>()
            roots.forEach { walk(it, emptyList(), null, nodes, scrolls) }
            return Picture(nodes, scrolls)
        }

        private fun walk(
            node: SemanticsNode,
            scrollers: List<Int>,
            control: Int?,
            nodes: MutableList<FrameNode>,
            scrolls: MutableMap<Int, Float>,
        ) {
            if (!node.layoutInfo.isPlaced) return
            val config = node.config
            val inControl = if (SemanticsActions.OnClick in config) node.id else control
            val range =
                config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    ?: config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
            nodeOf(node, config, scrollers, inControl)?.let { nodes += it }
            val inside = if (range != null) listOf(node.id) + scrollers else scrollers
            if (range != null) scrolls[node.id] = range.value()
            node.children.forEach { walk(it, inside, inControl, nodes, scrolls) }
        }

        private fun nodeOf(
            node: SemanticsNode,
            config: SemanticsConfiguration,
            scrollers: List<Int>,
            control: Int?,
        ): FrameNode? {
            val text =
                (config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: NO_TEXT)
                    .ifBlank { config.getOrNull(SemanticsProperties.EditableText)?.text ?: NO_TEXT }
            val described = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: NO_TEXT
            val tag = config.getOrNull(SemanticsProperties.TestTag)
            val label = text.ifBlank { described.ifBlank { tag?.let { "#$it" } ?: NO_TEXT } }
            val r = node.boundsInWindow
            val d = node.layoutInfo.density.density
            return if (label.isBlank() || r.width <= 0f || r.height <= 0f) {
                null
            } else {
                val box = DpBox(r.left / d, r.top / d, r.right / d, r.bottom / d)
                FrameNode(node.id, label, tag, text.isNotBlank(), box, scrollers, control)
            }
        }
    }
}
