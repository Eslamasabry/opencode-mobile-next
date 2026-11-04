package io.github.eslamasabry.oc.feature.onboarding.presentation

import io.github.eslamasabry.oc.core.common.Failure
import kotlinx.coroutines.flow.StateFlow

/** Reasons have fixed native copy; technical text stays in the redacted failure. */
public enum class ManagedMoveProblem {
    SeparateHistory,
    NotReady,
    NotConfirmed,
    Stopped,
}

/** The first-launch source's reasons; each maps onto the one [ManagedMoveProblem] the page shows. */
public enum class ManagedMoveReason {
    PhoneNotReady,
    SeparateHistory,
    MoveStopped,
    NotConfirmed;

    public val problem: ManagedMoveProblem
        get() =
            when (this) {
                PhoneNotReady -> ManagedMoveProblem.NotReady
                SeparateHistory -> ManagedMoveProblem.SeparateHistory
                MoveStopped -> ManagedMoveProblem.Stopped
                NotConfirmed -> ManagedMoveProblem.NotConfirmed
            }
}

public sealed interface ManagedMoveResult {
    public data object Done : ManagedMoveResult

    public data class Failed(val reason: ManagedMoveReason, val failure: Failure? = null) :
        ManagedMoveResult
}

/**
 * The host owns migration admission, setup and durable verification, independently of the screen.
 * [needed] decides the first-launch root.
 */
public interface ManagedMoveSource {
    public suspend fun needed(): Boolean

    public suspend fun move(): ManagedMoveResult
}

/**
 * [retry] counts quiet retries out of [retries]; [failure] holds redacted technical details shown
 * only in the details fold.
 */
public data class ManagedMoveUi(
    val running: Boolean = true,
    val problem: ManagedMoveProblem? = null,
    val retry: Int = 0,
    val retries: Int = 3,
    val failure: Failure? = null,
)

public interface ManagedMoveActions {
    public fun retry()

    public fun changeServer()
}

/**
 * The exact saved phone owner's actual migration, never a status inferred from an OC2 health reply.
 * A separate history is always terminal and both databases remain intact.
 */
public interface ManagedMoveWorld : ManagedMoveActions {
    public val state: StateFlow<ManagedMoveUi>
}
