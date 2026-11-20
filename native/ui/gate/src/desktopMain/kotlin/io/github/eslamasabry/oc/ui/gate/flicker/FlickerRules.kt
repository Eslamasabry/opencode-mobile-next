package io.github.eslamasabry.oc.ui.gate.flicker

import kotlin.math.max
import kotlin.math.min

/**
 * The rules over a run of frames. Pure: frames in, findings out, so the planted cases can feed it pictures directly.
 * Motion is told apart from a fault by what surrounds it: a node that moves on the frames before or after a big step is
 * in motion (an animation), a node whose scroll container scrolled is being scrolled; neither is a jump, a blink or a
 * return. Findings of one rule that start on the same frame are one finding (a block of lines that leaves together).
 */
public class FlickerRules(private val limits: FlickerLimits = FlickerLimits()) {
    /**
     * Every finding in [frames]. When the run is not [final] (more frames will follow) a pattern that needs a frame
     * after the last one is left for the next read.
     */
    public fun run(frames: List<RecordedFrame>, final: Boolean = true): List<Finding> {
        val hits = blinks(frames) + doubles(frames) + returns(frames) + moveReturns(frames) + jumps(frames, final)
        return grouped(frames, hits)
    }

    /**
     * The first frame drawn against the settled page: nodes found once in both (by tag, else by words) whose box
     * differs by more than the jump distance.
     */
    public fun firstVsSettled(first: RecordedFrame, settled: RecordedFrame): Finding? {
        val before = unique(first)
        val moved =
            if (settled.index <= first.index) {
                emptyList()
            } else {
                unique(settled).mapNotNull { (key, now) ->
                    before[key]?.takeIf { it.box.edgeMove(now.box) > limits.jumpDp }?.let { it to now }
                }
            }
        return moved.firstOrNull()?.let { (a, b) ->
            val more = if (moved.size > 1) " (+${moved.size - 1} more)" else ""
            val what = "at ${a.box} on the first frame drawn ($first), at ${b.box} once settled ($settled)$more"
            Finding(FlickerRule.FIRST, a.label, first.index, "\"${a.label.take(SHOWN)}\" $what")
        }
    }

    private fun blinks(frames: List<RecordedFrame>): List<Hit> =
        (1 until frames.size).flatMap { k ->
            val before = frames[k - 1].picture.byId
            frames[k].nodes.filter { it.id !in before }.mapNotNull { node -> blinkAt(frames, k, node) }
        }

    private fun blinkAt(frames: List<RecordedFrame>, k: Int, node: FrameNode): Hit? {
        val gone = after(frames, k, limits.blinkFrames).firstOrNull { node.id !in frames[it].picture.byId }
        return gone?.takeIf { !scrolled(frames, k - 1, it, node.scrollers) }?.let {
            val shown = "shown for ${it - k} frame(s) (${frames[k]} to ${frames[it - 1]}), then gone"
            Hit(FlickerRule.BLINK, k, node, shown)
        }
    }

    private fun doubles(frames: List<RecordedFrame>): List<Hit> =
        (1 until frames.size).flatMap { k ->
            val before = frames[k - 1].picture.texts
            frames[k].picture.texts
                .filter { (label, n) -> n >= 2 && n > (before[label] ?: 0) && label.length >= MIN_DOUBLE }
                .mapNotNull { (label, n) -> doubleAt(frames, k, label, n) }
        }

    private fun doubleAt(frames: List<RecordedFrame>, k: Int, label: String, n: Int): Hit? {
        val copies = frames[k].nodes.filter { it.isText && it.label == label }
        val end =
            after(frames, k, limits.returnFrames)
                .firstOrNull { (frames[it].picture.texts[label] ?: 0) < n }
                ?.takeIf { (frames[it].picture.texts[label] ?: 0) >= 1 }
                ?.takeIf { e -> copies.none { scrolled(frames, k - 1, e, it.scrollers) } }
        return end?.let {
            val where = copies.map { c -> c.box }
            Hit(FlickerRule.DOUBLE, k, copies.first(), "on $n nodes from ${frames[k]} until ${frames[it]} ($where)")
        }
    }

    private fun returns(frames: List<RecordedFrame>): List<Hit> =
        (1 until frames.size).flatMap { k ->
            val now = frames[k].picture.keys
            frames[k - 1].picture.keys
                .filter { (key, n) -> (now[key] ?: 0) < n }
                .mapNotNull { (key, n) -> returnAt(frames, k, key, n) }
        }

    private fun returnAt(frames: List<RecordedFrame>, k: Int, key: String, n: Int): Hit? {
        val node = frames[k - 1].nodes.first { flickerKey(it.label) == key }
        val back =
            after(frames, k, limits.returnFrames)
                .firstOrNull { (frames[it].picture.keys[key] ?: 0) >= n }
                ?.takeIf { !scrolled(frames, k - 1, it, node.scrollers) }
        return back?.let {
            Hit(FlickerRule.ABA, k, node, "gone at ${frames[k]} and back at ${frames[it]} (was at ${node.box})")
        }
    }

    private fun moveReturns(frames: List<RecordedFrame>): List<Hit> =
        (1 until frames.size).flatMap { k ->
            val before = frames[k - 1].picture.byId
            frames[k].nodes.mapNotNull { away ->
                before[away.id]
                    ?.takeIf { it.box.edgeMove(away.box) > limits.abaDp }
                    ?.let { was -> moveBack(frames, k, was, away, limits) }
            }
        }

    private fun jumps(frames: List<RecordedFrame>, final: Boolean): List<Hit> =
        (1 until frames.size).filter { final || it < frames.lastIndex }.flatMap { k ->
            val before = frames[k - 1].picture.byId
            frames[k].nodes.mapNotNull { now ->
                before[now.id]?.takeIf { isJump(frames, k, it, now, limits) }?.let { was ->
                    Hit(FlickerRule.JUMP, k, now, "moved from ${was.box} to ${now.box} in one frame (${frames[k]})")
                }
            }
        }
}

/** One rule's hit before grouping: the list position it starts at, the node, and how it reads. */
private data class Hit(val rule: FlickerRule, val at: Int, val node: FrameNode, val what: String)

private const val SHOWN = 60
private const val MORE = 3
private const val MIN_DOUBLE = 2

private fun unique(frame: RecordedFrame): Map<String, FrameNode> =
    frame.nodes
        .groupBy { n -> n.tag?.let { "#$it" } ?: n.label }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single() }

/** True when one of [scrollers] changed its scroll position anywhere in frames [from]..[to]. */
private fun scrolled(frames: List<RecordedFrame>, from: Int, to: Int, scrollers: List<Int>): Boolean {
    val range = max(from, 0)..min(to, frames.lastIndex)
    return scrollers.any { s -> range.mapNotNull { frames[it].picture.scrolls[s] }.distinct().size > 1 }
}

/** The positions after [k] a pattern starting at [k] may take to end, at most [span] frames on. */
private fun after(frames: List<RecordedFrame>, k: Int, span: Int): IntRange = k + 1..min(k + span, frames.lastIndex)

private fun moveBack(
    frames: List<RecordedFrame>,
    k: Int,
    was: FrameNode,
    away: FrameNode,
    limits: FlickerLimits,
): Hit? {
    // The first frame on which the node is back where it was, or gone (then it did not come back).
    val end =
        after(frames, k, limits.returnFrames).firstOrNull { i ->
            frames[i].picture.byId[was.id]?.let { it.box.edgeMove(was.box) <= limits.stillDp } ?: true
        }
    val back =
        end?.takeIf { frames[it].picture.byId[was.id] != null && !scrolled(frames, k - 1, it, was.scrollers) }
    return back?.let {
        val path = "moved from ${was.box} to ${away.box} at ${frames[k]} and back at ${frames[it]}"
        Hit(FlickerRule.ABA, k, was, path)
    }
}

/** A step over the jump distance between k-1 and k, the node still on the frames around it, nothing scrolled. */
private fun isJump(
    frames: List<RecordedFrame>,
    k: Int,
    was: FrameNode,
    now: FrameNode,
    limits: FlickerLimits,
): Boolean =
    now.box.placeMove(was.box) > limits.jumpDp &&
        still(frames, k - 2, k - 1, now.id, limits) &&
        still(frames, k, k + 1, now.id, limits) &&
        !scrolled(frames, k - 2, k + 1, now.scrollers)

private fun still(frames: List<RecordedFrame>, a: Int, b: Int, id: Int, limits: FlickerLimits): Boolean {
    val x = frames.getOrNull(a)?.picture?.byId?.get(id)
    val y = frames.getOrNull(b)?.picture?.byId?.get(id)
    return x == null || y == null || y.box.placeMove(x.box) <= limits.stillDp
}

private fun grouped(frames: List<RecordedFrame>, hits: List<Hit>): List<Finding> =
    hits.groupBy { it.rule to it.at }.map { (_, group) ->
        val hit = group.first()
        val others = group.drop(1).take(MORE).joinToString { "\"${it.node.label.take(SHOWN)}\"" }
        val more = if (group.size > 1) " (+${group.size - 1} more: $others)" else ""
        val detail = "\"${hit.node.label.take(SHOWN)}\" ${hit.what}$more"
        Finding(hit.rule, hit.node.label, frames[hit.at].index, detail)
    }
