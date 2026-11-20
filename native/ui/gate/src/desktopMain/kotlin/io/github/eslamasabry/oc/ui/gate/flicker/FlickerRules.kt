package io.github.eslamasabry.oc.ui.gate.flicker

/**
 * The rules over a run of frames. Pure: frames in, findings out, so the planted cases can feed it pictures directly.
 * Motion is told apart from a fault by what surrounds it: a node that moves on the frames before or after a big step is
 * in motion (an animation), a node whose scroll container scrolled is being scrolled; neither is a jump, a blink or a
 * return. Findings of one rule that start on the same frame are one finding (a block of lines that leaves together).
 */
public class FlickerRules(private val limits: FlickerLimits = FlickerLimits()) {
    /** Every finding in [frames] (not yet: the rules land in the next commit, so the planted cases are red). */
    public fun run(frames: List<RecordedFrame>, final: Boolean = true): List<Finding> = emptyList()

    /** The first frame drawn against the settled page (not yet). */
    public fun firstVsSettled(first: RecordedFrame, settled: RecordedFrame): Finding? = null
}
