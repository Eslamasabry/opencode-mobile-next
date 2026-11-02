package io.github.eslamasabry.oc.engine.migration

import kotlinx.coroutines.flow.StateFlow

/**
 * The day-1 runner as [MigrationEngine] drives it. Bound by `:androidApp` over `:android:migration`'s
 * `MigrationRunner` (graph/MigrationWiring.kt); tests use a fake. Every call is blocking-free (suspends on the runner's
 * own IO dispatcher) and single-flight in the runner.
 */
public interface MigrationRunnerPort {
    public val progress: StateFlow<MigrationProgress>

    /** Runs or resumes `native.migration.v1`, or opens at once when it already completed. */
    public suspend fun run()

    /** "Try moving your data again": while a run is in flight waits on it; from Blocked resets unfinished steps. */
    public suspend fun retry()

    /** The re-enter sheet and the deferred-features notice were seen: never again (plan 05 §8.2). */
    public suspend fun acknowledgeNotices()

    /**
     * Start fresh (plan 05 §9.4): keeps a copy of the old data and opens an empty app. Only called while
     * [MigrationProgress.Blocked.startFreshAvailable]; returns false when it could not start.
     */
    public suspend fun startFresh(): Boolean

    /** R2 (plan 05 §9.4). Only called while [MigrationProgress.Blocked.restoreAvailable]. */
    public suspend fun restorePreUpdateData(): Boolean

    public companion object {
        /** No Flutter data to adopt (the side-by-side preview package, desktop): open from the start. */
        public val Nothing: MigrationRunnerPort = object : MigrationRunnerPort {
            override val progress: StateFlow<MigrationProgress> =
                kotlinx.coroutines.flow.MutableStateFlow(MigrationProgress.Open(firstNativeLaunch = false))

            override suspend fun run() = Unit

            override suspend fun retry() = Unit

            override suspend fun acknowledgeNotices() = Unit

            override suspend fun startFresh(): Boolean = false

            override suspend fun restorePreUpdateData(): Boolean = false
        }
    }
}
