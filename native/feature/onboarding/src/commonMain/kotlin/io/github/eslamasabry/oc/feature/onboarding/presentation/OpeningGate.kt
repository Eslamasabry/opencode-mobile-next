package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ADR §9.7 start-up as the opening gate shows it (07 §5 A01). */
public enum class OpeningPhase {
    /** Bootstrap steps 5–6 (the StartupFence, prefs) are running. */
    Opening,

    /** The MigrationGate is closed while the migration runs: "Updating your app…". */
    Updating,

    /** Saved servers could not be read (keystore locked after a restart, refused storage). */
    Failed,

    /** Bootstrap Ready and the gate open: the shell draws the first stack. */
    Ready,

    /** The crash-loop guard tripped: the shell shows safe mode (A02), not this gate. */
    SafeMode,
}

/**
 * An atomic bootstrap observation. The source advances [generation] for every transition or new
 * failed attempt, including a replacement failure; StateFlow may conflate the intermediate phase
 * but must retain this revision.
 */
public data class OpeningSnapshot(val phase: OpeningPhase, val generation: Long)

/**
 * The gate's port onto `:engine:entry` (`AppBootstrap.phase` + `GateSignal.open`) and the
 * start-fresh reset. Bound by the shell hookup (S2-CHATS report §7); nothing here reads an engine
 * directly.
 */
public interface OpeningWorld {
    public val phase: StateFlow<OpeningSnapshot>

    /** Runs the failed step again. */
    public suspend fun retry()

    /**
     * Closes the phone agents first (ee2c53940), then removes saved sign-ins and the selected
     * server.
     */
    public suspend fun startFresh(): Outcome<Unit>

    /** Opens Report a problem with this start's category-only record. */
    public fun reportProblem()
}

/**
 * What the gate draws. [since] lets the kit escalate a slow wait at 8 s with Try again (B-KIT-034).
 */
public sealed interface OpeningGateUi {
    public data class Waiting(val updating: Boolean, val since: TimeMark) : OpeningGateUi

    /**
     * "Can't read saved servers" with Try again, Report a problem and Start fresh; [confirming] =
     * the question shows.
     */
    public data class Failed(val confirming: Boolean = false, val confirmationId: Long? = null) :
        OpeningGateUi

    public data object Resetting : OpeningGateUi

    public data object ResetFailed : OpeningGateUi

    /** Nothing to draw: the gate is open (or safe mode owns the screen). */
    public data object Open : OpeningGateUi
}

public interface OpeningGateActions {
    public fun retry()

    public fun reportProblem()

    /** Asks "Remove saved sign-ins?" first; nothing is removed on this tap. */
    public fun askStartFresh()

    public fun confirmStartFresh(confirmationId: Long?)

    public fun cancelStartFresh()
}

private enum class Reset {
    None,
    Confirming,
    Running,
    Failed,
}

/**
 * The shell's `BootstrapViewModel` (S): the shell hookup constructs it with the bound
 * [OpeningWorld].
 */
public class OpeningGateViewModel(
    private val world: OpeningWorld,
    private val time: TimeSource = TimeSource.Monotonic,
) : ViewModel(), OpeningGateActions {
    private val reset = MutableStateFlow(Reset.None)
    private var confirmationId: Long? = null
    private var nextConfirmationId = 0L
    private var observedPhase = world.phase.value
    private val phases =
        world.phase.onEach { phase ->
            if (phase != observedPhase) {
                observedPhase = phase
                confirmationId = null
                if (reset.value == Reset.Confirming) reset.value = Reset.None
            }
        }
    private var waitingSince: Pair<OpeningPhase, TimeMark>? = null

    public val state: StateFlow<OpeningGateUi> =
        combine(phases, reset) { snapshot, reset -> build(snapshot.phase, reset) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(world.phase.value.phase, reset.value),
            )

    private fun build(phase: OpeningPhase, reset: Reset): OpeningGateUi =
        when {
            reset == Reset.Running -> OpeningGateUi.Resetting
            reset == Reset.Failed -> OpeningGateUi.ResetFailed
            phase == OpeningPhase.Opening || phase == OpeningPhase.Updating -> {
                // One clock per waiting phase: a recomposition or a reset question never restarts
                // the 8 s.
                val mark = waitingSince?.takeIf { it.first == phase }?.second ?: time.markNow()
                waitingSince = phase to mark
                OpeningGateUi.Waiting(updating = phase == OpeningPhase.Updating, since = mark)
            }
            phase == OpeningPhase.Failed ->
                OpeningGateUi.Failed(
                    confirming = reset == Reset.Confirming && confirmationId != null,
                    confirmationId = confirmationId,
                )
            else -> OpeningGateUi.Open
        }

    override fun retry() {
        reset.value = Reset.None
        viewModelScope.launch { world.retry() }
    }

    override fun reportProblem(): Unit = world.reportProblem()

    override fun askStartFresh() {
        if (
            world.phase.value.phase != OpeningPhase.Failed ||
                observedPhase != world.phase.value ||
                reset.value != Reset.None
        )
            return
        confirmationId = ++nextConfirmationId
        reset.value = Reset.Confirming
    }

    override fun cancelStartFresh() {
        confirmationId = null
        reset.update { if (it == Reset.Confirming) Reset.None else it }
    }

    override fun confirmStartFresh(confirmationId: Long?) {
        if (observedPhase != world.phase.value) {
            cancelStartFresh()
            return
        }
        if (
            confirmationId == null ||
                confirmationId != this.confirmationId ||
                world.phase.value.phase != OpeningPhase.Failed ||
                reset.value != Reset.Confirming
        )
            return
        this.confirmationId = null
        val generation = world.phase.value.generation
        reset.value = Reset.Running
        viewModelScope.launch {
            // The launch may run after bootstrap recovered; consent belongs only to that failed
            // phase.
            if (
                world.phase.value.phase != OpeningPhase.Failed ||
                    generation != world.phase.value.generation
            ) {
                reset.value = Reset.None
                return@launch
            }
            reset.value = if (world.startFresh() is Outcome.Ok) Reset.None else Reset.Failed
        }
    }
}
