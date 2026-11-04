package io.github.eslamasabry.oc.engine.runtime.setup

import kotlin.math.floor

/**
 * Port of the pure rules of lib/ui/widgets/setup_progress_view.dart used by the progress screen
 * (lib/ui/screens/phone_setup/phone_setup_progress_screen.dart): when the ownership recovery (Force stop) replaces
 * Continue setup, and how a byte pair is written.
 */
public object SetupProgressRules {
    /**
     * A failed job whose own error or a failed row's error is the ownership failure. A running job never asks for it,
     * and the log is not trusted (a script can print anything), so only the engine's error values count.
     */
    public fun needsOwnershipRecovery(progress: SetupProgress): Boolean =
        progress.state == SetupState.Failed && (
            progress.error == SetupError.OwnershipUnknown ||
                progress.components.any { it.state == ComponentState.Failed && it.error == SetupError.OwnershipUnknown }
            )

    private val units = listOf("B", "KB", "MB", "GB")

    /**
     * "18 of 30 MB" parts (`setupProgressViewBytes(done, total)`): the unit is chosen by [total]; one decimal below
     * ten units, none in bytes. Done rounds down (never shows more than arrived) and is clamped to the total; the
     * total rounds to nearest and keeps its unit.
     */
    public fun formatBytePair(done: Long, total: Long): Pair<String, String> {
        var unit = 0
        var scale = 1.0
        while (unit < units.size - 1 && total >= scale * 1000) {
            scale *= 1000
            unit++
        }
        val decimals = if (unit == 0 || total / scale >= 10) 0 else 1
        val factor = if (decimals == 0) 1.0 else 10.0
        fun fixed(value: Double, down: Boolean): String {
            val scaled = if (down) floor(value * factor) else floor(value * factor + 0.5)
            val whole = scaled.toLong()
            return if (decimals == 0) whole.toString() else "${whole / 10}.${whole % 10}"
        }
        return fixed(done.coerceIn(0L, maxOf(total, 0L)) / scale, down = true) to
            "${fixed(total / scale, down = false)} ${units[unit]}"
    }
}
