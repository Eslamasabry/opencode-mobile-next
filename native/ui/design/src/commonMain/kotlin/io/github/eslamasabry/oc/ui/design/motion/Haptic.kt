package io.github.eslamasabry.oc.ui.design.motion

/**
 * The haptic events (plan 06 §4.9, plan 08 §6.2). Meaning, not mechanism: `AndroidKitPlatform` maps each to a
 * `HapticFeedbackConstants` value. Haptics never fire on scroll, plain taps, failures, sheets or dialogs (MOT-11).
 */
public enum class KitHaptic {
    /** The person's words, or a typed answer, leave. Not a copy (that is a [Select] tick). */
    Send,
    /** A waited-for finish, once; skipped under Off. */
    Done,
    /**
     * The one consequential confirmation: approve, deny, always allow, stop a run, a destructive confirm. Felt as
     * different from, and stronger than, [Send]; never the lightest.
     */
    Commit,
    /** A long-press opens a menu or selection. */
    LongPress,
    /** The lightest tick: a segment, tab, chip, choice, slider step, wheel value, or a copy acknowledgement. */
    Select,
    ToggleOn,
    ToggleOff,
    /**
     * Another conversation needs the person while they read this one: the edge light's one light tick, once per burst
     * (LAMP-CRAFT §2). The only haptic for something that did not come from the person's own touch.
     */
    Attention,
}

/** Flutter KitHaptics: the global and contextual settings both win; only done obeys reduced motion. */
public fun KitHaptic.allowed(enabled: Boolean = true, contextEnabled: Boolean = true, reduced: Boolean = false): Boolean =
    enabled && contextEnabled && (this != KitHaptic.Done || !reduced)

/**
 * The strength order of the roles, in common code so the platform mapping can be checked without a phone: a stronger
 * role never maps to a weaker platform effect than a lighter one. [KitHaptic.Select] < [KitHaptic.Send] <
 * [KitHaptic.Commit] strictly.
 */
public val KitHaptic.weight: Int
    get() =
        when (this) {
            KitHaptic.Attention -> 0
            KitHaptic.Select, KitHaptic.ToggleOn, KitHaptic.ToggleOff -> 1
            KitHaptic.Send, KitHaptic.Done -> 2
            KitHaptic.LongPress -> 3
            KitHaptic.Commit -> 4
        }
