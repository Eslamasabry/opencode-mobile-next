package io.github.eslamasabry.oc.ui.design.motion

/**
 * The haptic events (plan 06 §4.9, plan 08 §6.2). Meaning, not mechanism: `AndroidKitPlatform` maps each to a
 * `HapticFeedbackConstants` value. Haptics never fire on scroll, plain taps, failures, sheets or dialogs (MOT-11).
 */
public enum class KitHaptic {
    /** The person's words leave; also a copy. */
    Send,
    /** A waited-for finish, once; skipped under Off. */
    Done,
    /** A confirmed stop, destructive action or discard. */
    Commit,
    /** A long-press opens a menu or selection. */
    LongPress,
    /** A segment, slider step or wheel value. */
    Select,
    ToggleOn,
    ToggleOff,
}

/** Flutter KitHaptics: the global and contextual settings both win; only done obeys reduced motion. */
public fun KitHaptic.allowed(enabled: Boolean = true, contextEnabled: Boolean = true, reduced: Boolean = false): Boolean =
    enabled && contextEnabled && (this != KitHaptic.Done || !reduced)
