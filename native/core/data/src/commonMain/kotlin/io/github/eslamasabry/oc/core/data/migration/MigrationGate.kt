package io.github.eslamasabry.oc.core.data.migration

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Plan 05 §7.3. The gate the day-1 migrator (`:android:migration`) drives. */
public sealed interface GateState {
    public data object Closed : GateState

    public data class Running(val step: String) : GateState

    public data class Open(val firstNativeLaunch: Boolean, val notices: MigrationNotices) : GateState

    public data class Blocked(val reason: BlockReason, val neededBytes: Long? = null) : GateState
}

public enum class BlockReason { LowSpace, PrefsUnreadable, WriteRefused, AuthorityChanged, StepCrashed }

/** Drives the one-time "enter the password again" sheet (plan 05 §8.2). Ids only, never secrets. */
public data class MigrationNotices(val reenter: List<ProfileId> = emptyList())

/**
 * The store gates WRITES, not reads (plan 05 §2.2): the gate screen, the crash-loop guard and the migrator must read.
 * Every `KeyValueStore` write suspends in [awaitWritable]; the migrator writes through its own `MigrationWriter`.
 *
 * `MigrationActions` (retry, restore, start fresh) are deliberately NOT here: the gate screen in `:ui:shell` may not
 * depend on `:core:data` (audit codex-04+05 BLOCKER 3); that surface belongs to an engine-level API.
 */
public interface MigrationGate {
    public val state: StateFlow<GateState>

    /** Returns when the state is [GateState.Open]; never returns while Closed, Running or Blocked. */
    public suspend fun awaitWritable()
}

@RequiresOptIn(
    "Only the day-1 migrator (:android:migration) and tests drive the gate or write past it.",
    RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
public annotation class MigrationOnly

/** A gate backed by a state flow. The migrator publishes states; everyone else only waits. */
public class StateMigrationGate @MigrationOnly constructor(initial: GateState = GateState.Closed) : MigrationGate {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<GateState> = mutableState

    override suspend fun awaitWritable() {
        mutableState.first { it is GateState.Open }
    }

    @MigrationOnly
    public fun publish(next: GateState) {
        mutableState.value = next
    }

    public companion object {
        /** No Flutter data to adopt (desktop, fresh test stores): open from the start. */
        @OptIn(MigrationOnly::class)
        public fun alwaysOpen(): MigrationGate =
            StateMigrationGate(GateState.Open(firstNativeLaunch = false, notices = MigrationNotices()))
    }
}
