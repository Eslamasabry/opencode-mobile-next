package io.github.eslamasabry.oc.android.migration

/** Not a `qa` build: no kill points, no delay, no free-space override (plan 05 §7.4 S0 step 3). */
public object StepInterrupters {
    public fun forBuild(@Suppress("UNUSED_PARAMETER") paths: MigrationPaths): StepInterrupter = StepInterrupter.None
}
