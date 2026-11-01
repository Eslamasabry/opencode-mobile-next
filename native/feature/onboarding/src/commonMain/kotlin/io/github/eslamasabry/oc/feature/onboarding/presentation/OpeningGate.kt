package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
 * The gate's port onto `:engine:entry` (`AppBootstrap.phase` + `GateSignal.open`) and the start-fresh reset. Bound by
 * the shell hookup (S2-CHATS report §7); nothing here reads an engine directly.
 */
public interface OpeningWorld {
    public val phase: StateFlow<OpeningPhase>

    /** Runs the failed step again. */
    public suspend fun retry()

    /** Closes the phone agents first (ee2c53940), then removes saved sign-ins and the selected server. */
    public suspend fun startFresh(): Outcome<Unit>

    /** Opens Report a problem with this start's category-only record. */
    public fun reportProblem()
}

/** What the gate draws. [since] lets the kit escalate a slow wait at 8 s with Try again (B-KIT-034). */
public sealed interface OpeningGateUi {
    public data class Waiting(val updating: Boolean, val since: TimeMark) : OpeningGateUi

    /** "Can't read saved servers" with Try again, Report a problem and Start fresh; [confirming] = the question shows. */
    public data class Failed(val confirming: Boolean = false) : OpeningGateUi

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

    public fun confirmStartFresh()

    public fun cancelStartFresh()
}

private enum class Reset { None, Confirming, Running, Failed }

/** The shell's `BootstrapViewModel` (S): the shell hookup constructs it with the bound [OpeningWorld]. */
public class OpeningGateViewModel(
    private val world: OpeningWorld,
    private val time: TimeSource = TimeSource.Monotonic,
) : ViewModel(), OpeningGateActions {
    private val reset = MutableStateFlow(Reset.None)
    private var waitingSince: Pair<OpeningPhase, TimeMark>? = null

    public val state: StateFlow<OpeningGateUi> =
        combine(world.phase, reset, ::build).stateIn(viewModelScope, SharingStarted.Eagerly, build(world.phase.value, reset.value))

    private fun build(phase: OpeningPhase, reset: Reset): OpeningGateUi =
        when {
            reset == Reset.Running -> OpeningGateUi.Resetting
            reset == Reset.Failed -> OpeningGateUi.ResetFailed
            phase == OpeningPhase.Opening || phase == OpeningPhase.Updating -> {
                // One clock per waiting phase: a recomposition or a reset question never restarts the 8 s.
                val mark = waitingSince?.takeIf { it.first == phase }?.second ?: time.markNow()
                waitingSince = phase to mark
                OpeningGateUi.Waiting(updating = phase == OpeningPhase.Updating, since = mark)
            }
            phase == OpeningPhase.Failed -> OpeningGateUi.Failed(confirming = reset == Reset.Confirming)
            else -> OpeningGateUi.Open
        }

    override fun retry() {
        reset.value = Reset.None
        viewModelScope.launch { world.retry() }
    }

    override fun reportProblem(): Unit = world.reportProblem()

    override fun askStartFresh(): Unit = reset.update { if (it == Reset.None) Reset.Confirming else it }

    override fun cancelStartFresh(): Unit = reset.update { if (it == Reset.Confirming) Reset.None else it }

    override fun confirmStartFresh() {
        if (reset.value != Reset.Confirming) return
        reset.value = Reset.Running
        viewModelScope.launch {
            reset.value = if (world.startFresh() is Outcome.Ok) Reset.None else Reset.Failed
        }
    }
}
