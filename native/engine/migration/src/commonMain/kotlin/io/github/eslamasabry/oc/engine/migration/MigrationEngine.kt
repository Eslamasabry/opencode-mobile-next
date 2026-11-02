package io.github.eslamasabry.oc.engine.migration

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.migration.BlockReason
import io.github.eslamasabry.oc.core.data.migration.GateState
import io.github.eslamasabry.oc.core.data.migration.MigrationGate
import io.github.eslamasabry.oc.core.data.migration.MigrationNotices
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Resolves a stored profile id to the server's plain name for the re-enter sheet. Called only after the gate opens. */
public fun interface ReentryNames {
    public suspend fun nameOf(profileId: String): String?
}

/**
 * What the update gate is fed: the runner's [progress], the plain [names] of the servers in the re-enter list, and
 * whether the deferred-features notice is still to be shown.
 */
public data class MigrationView(
    val progress: MigrationProgress,
    val names: Map<String, String> = emptyMap(),
    /** True while a way out the person chose (Try again, Start fresh, Restore) is running. */
    val acting: Boolean = false,
)

/**
 * The day-1 migration as the app sees it (plan 05 §7.3, 01 §9.7). One per process.
 *
 * - [start] runs the runner once, off the caller's thread; [gate] stays closed (every `KeyValueStore` write and every
 *   `GatedSecretStore` call waits) until the runner reports Open, and never closes again after that.
 * - A runner that throws is never a crash loop or an endless "Updating": it becomes `Blocked(StepCrashed)` with
 *   Try again (B-MIGR-010). Nothing is deleted by the engine.
 * - Every way out is single-flight: a second tap while one runs does nothing.
 * - The re-enter sheet and the deferred-features notice are acknowledged once and never shown again.
 */
@OptIn(MigrationOnly::class)
public class MigrationEngine(
    private val runner: MigrationRunnerPort,
    private val names: ReentryNames,
    private val scope: CoroutineScope,
) {
    private val stateGate = StateMigrationGate(GateState.Closed)

    /** Bound for every store as the app's `MigrationGate`. */
    public val gate: MigrationGate get() = stateGate

    private val mutableView = MutableStateFlow(MigrationView(MigrationProgress.Waiting))
    public val view: StateFlow<MigrationView> = mutableView.asStateFlow()

    private val actions = Mutex()
    private var started = false
    private var opened = false
    private var deferredSeen = false

    /** Starts the runner and follows it. A second call is ignored. */
    public fun start() {
        if (started) return
        started = true
        scope.launch { runner.progress.collect { onProgress(it) } }
        scope.launch { guarded { runner.run() } }
    }

    /** "Try moving your data again" (also Try again on the slow waiting screen). */
    public suspend fun retry() {
        act { guarded { runner.retry() } }
    }

    /** Start fresh (keeps a copy). Refused unless the current block offers it. */
    public suspend fun startFresh() {
        val blocked = mutableView.value.progress as? MigrationProgress.Blocked ?: return
        if (!blocked.startFreshAvailable) return
        act { guarded { if (!runner.startFresh()) log.w { "start fresh refused" } } }
    }

    /** R2. Refused unless the current block offers it. */
    public suspend fun restorePreUpdateData() {
        val blocked = mutableView.value.progress as? MigrationProgress.Blocked ?: return
        if (!blocked.restoreAvailable) return
        act { guarded { if (!runner.restorePreUpdateData()) log.w { "restore refused" } } }
    }

    /** The re-enter sheet was closed, answered or put off: it is never asked again (plan 05 §8.2). */
    public suspend fun acknowledgeNotices() {
        guarded { runner.acknowledgeNotices() }
    }

    /**
     * The deferred-features notice was shown (02 §3.7 item 4). The runner records both notices together, so while the
     * re-enter sheet still waits for the person this only remembers it; the sheet's acknowledgement records both.
     */
    public suspend fun acknowledgeDeferredNotice() {
        deferredSeen = true
        val open = mutableView.value.progress as? MigrationProgress.Open ?: return
        if (open.reenter.isEmpty()) guarded { runner.acknowledgeNotices() }
    }

    private suspend fun act(block: suspend () -> Unit) {
        if (!actions.tryLock()) return
        try {
            mutableView.update { it.copy(acting = true) }
            block()
        } finally {
            mutableView.update { it.copy(acting = false) }
            actions.unlock()
        }
    }

    /** Runs a runner call; an unexpected throw becomes a safe block, never a crash (plan 05 §7.11). */
    private suspend fun guarded(block: suspend () -> Unit) {
        val result = attempt({ Failure.Unexpected() }) { block() }
        if (result is Outcome.Failed && !opened) {
            log.w { "runner stopped unexpectedly" }
            onProgress(MigrationProgress.Blocked(MigrationBlock.StepCrashed, step = "runner"))
        }
    }

    private suspend fun onProgress(progress: MigrationProgress) {
        if (opened && progress !is MigrationProgress.Open) return // the gate never closes again once engines run
        stateGate.publish(gateStateOf(progress))
        val current = mutableView.value
        mutableView.value = current.copy(progress = progress)
        if (progress is MigrationProgress.Open) {
            if (!opened) log.i { "gate open (first native launch: ${progress.firstNativeLaunch})" }
            opened = true
            val ids = progress.reenter.mapNotNull { it.profileId }.distinct()
            val resolved = ids.filter { it !in current.names }.associateWith { id ->
                val name = attempt({ Failure.Unexpected() }) { names.nameOf(id) }
                (name as? Outcome.Ok)?.value
            }
            val found = resolved.mapNotNull { (id, name) -> name?.let { id to it } }.toMap()
            if (found.isNotEmpty()) mutableView.update { it.copy(names = it.names + found) }
            if (deferredSeen && progress.reenter.isEmpty() && progress.deferred.isNotEmpty()) {
                guarded { runner.acknowledgeNotices() }
            }
        }
    }

    public companion object {
        private val log = AppLog.tagged("migration")

        /** The one mapping onto the `:core:data` gate (plan 05 §7.3). `BackupInvalid` is a crashed step there. */
        public fun gateStateOf(progress: MigrationProgress): GateState =
            when (progress) {
                MigrationProgress.Waiting -> GateState.Closed
                is MigrationProgress.Running -> GateState.Running(progress.step)
                is MigrationProgress.Open ->
                    GateState.Open(
                        firstNativeLaunch = progress.firstNativeLaunch,
                        notices = MigrationNotices(progress.reenter.mapNotNull { it.profileId }.distinct().map(::ProfileId)),
                    )
                is MigrationProgress.Blocked ->
                    GateState.Blocked(
                        reason =
                            when (progress.reason) {
                                MigrationBlock.LowSpace -> BlockReason.LowSpace
                                MigrationBlock.PrefsUnreadable -> BlockReason.PrefsUnreadable
                                MigrationBlock.WriteRefused -> BlockReason.WriteRefused
                                MigrationBlock.AuthorityChanged -> BlockReason.AuthorityChanged
                                MigrationBlock.StepCrashed, MigrationBlock.BackupInvalid -> BlockReason.StepCrashed
                            },
                        neededBytes = progress.neededBytes,
                    )
            }
    }
}
