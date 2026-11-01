package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.migration.FaultPoints

/**
 * The crash-safety seam (plan 05 §7.10, §10.5 J2, `StepInterrupter`). The runner calls [reached] after each step's
 * journal marker (`S0`, `M1`, `M2:1`, `M2:all`, `M3`, `M4`, `M5`, `M6`) and between S0's filesystem operations
 * (`S0:partialCreated`, `S0:copied:<file>`, …). Release and debug builds use [None]; only the `qa` build type compiles
 * a real one (`QaStepInterrupter`, which kills the process at `files/native/qa-kill-after`). JVM tests throw a
 * `SimulatedCrash` instead, then start a fresh runner over the same files.
 */
public interface StepInterrupter : FaultPoints {
    /** Extra wait before S0 so the Updating screen can be filmed (`qa-delay-ms`). */
    public fun delayMs(): Long = 0L

    /** Replaces `StatFs.availableBytes` so J8 can run without filling the disk (`qa-free-bytes`). */
    public fun freeBytesOverride(): Long? = null

    public companion object {
        public val None: StepInterrupter = object : StepInterrupter {
            override fun reached(point: String) = Unit
        }
    }
}
