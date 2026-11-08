package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.runtime.Immutable
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style

/** The beat table of a theme switch (theme-switch-premium, Choreography), in ms after the tap. */
public object ThemeSwitchTimings {
    /** The theme commits once the motion is visibly under way, so the recomposition frame does not land on its start. */
    public const val COMMIT_MS: Long = 120

    /** The rim's run round the edge, then its glow settling. */
    public const val RIM_TRAVEL_MS: Int = 450
    public const val RIM_MS: Int = 600

    /** Idle: every draw loop has stopped. */
    public const val IDLE_MS: Int = 700

    /**
     * Two theme commits are at least this far apart, so the full-screen light-dark flip happens at most three times a
     * second however fast the taps come (WCAG 2.3.1).
     */
    public const val MIN_COMMIT_GAP_MS: Long = 334
}

/** What a tap on the mode control plays at a motion level (theme-switch-premium §5). */
@Immutable
public data class ThemeSwitchPlan(
    /** The chosen block slides to the new segment (the release spring). */
    val slide: Boolean,
    /** The rim light runs round the new segment's edge. */
    val rim: Boolean,
    /** The icon's dots travel and re-form (otherwise, with [crossfade], the icons cross-fade). */
    val dust: Boolean,
    val crossfade: Boolean,
    /** When the theme commits after the tap (before any debounce wait). */
    val commitDelayMs: Long,
)

/** The look of the rim per style (theme-switch-premium §3). */
public enum class ThemeRimKind {
    /** A head of accent light with a fading tail and a soft halo (Solid, Signal, Glass). */
    Glow,

    /** A soft inner glow with no hard edge (Soft). */
    InnerGlow,

    /** No glow: a hard outline sweep, and the block shifts 2 px along its offset shadow (Brutal). */
    HardSweep,

    /** No glow: an ink outline sweep (Paper). */
    InkSweep,
}

public object ThemeSwitchRules {
    /** Brutal's block shift during the sweep, in px, along its offset shadow (mirrored in RTL). */
    public const val BRUTAL_SHIFT_PX: Float = 2f

    /**
     * Full: everything. Calm: the slide and a plain icon cross-fade, no rim, no dust. Off (reduced motion, system
     * animations off, Saver: `resolveMotion` already folds them into Off): an instant change; the theme cover's own
     * fade is the only motion.
     */
    public fun plan(level: MotionLevel): ThemeSwitchPlan =
        when (level) {
            MotionLevel.Full -> ThemeSwitchPlan(slide = true, rim = true, dust = true, crossfade = false, ThemeSwitchTimings.COMMIT_MS)
            MotionLevel.Calm -> ThemeSwitchPlan(slide = true, rim = false, dust = false, crossfade = true, ThemeSwitchTimings.COMMIT_MS)
            MotionLevel.Off -> ThemeSwitchPlan(slide = false, rim = false, dust = false, crossfade = false, 0)
        }

    public fun rimFor(style: Style): ThemeRimKind =
        when (style) {
            Style.Solid, Style.Signal, Style.Glass -> ThemeRimKind.Glow
            Style.Soft -> ThemeRimKind.InnerGlow
            Style.Brutal -> ThemeRimKind.HardSweep
            Style.Paper -> ThemeRimKind.InkSweep
        }

    /**
     * When a tap at [nowMs] commits the theme: after the plan's delay, and never sooner than
     * [ThemeSwitchTimings.MIN_COMMIT_GAP_MS] after the last commit ([lastCommitMs], null before the first).
     */
    public fun commitAt(nowMs: Long, lastCommitMs: Long?, plan: ThemeSwitchPlan): Long {
        val wanted = nowMs + plan.commitDelayMs
        return if (lastCommitMs == null) wanted else maxOf(wanted, lastCommitMs + ThemeSwitchTimings.MIN_COMMIT_GAP_MS)
    }
}
