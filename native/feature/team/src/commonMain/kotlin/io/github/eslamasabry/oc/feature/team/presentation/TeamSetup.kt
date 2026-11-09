package io.github.eslamasabry.oc.feature.team.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.SetupPhase
import io.github.eslamasabry.oc.engine.team.SetupProblem
import io.github.eslamasabry.oc.engine.team.SetupServerState
import io.github.eslamasabry.oc.engine.team.SetupState
import io.github.eslamasabry.oc.engine.team.SetupStep
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The mark of one step (Dart `KitMarkState`); every mark comes from the engine's own signals. */
public enum class SetupMark { Waiting, Working, Done, Failed }

/** The words a step carries under its title, besides its time. */
public enum class SetupNote { None, NotNeeded, ReplyWaiting, StopWaiting, NotPackaged }

/** One row of the checklist. [tookMs] once it ended, [soFarMs] while it works. */
public data class SetupStepUi(
    val step: SetupStep,
    val mark: SetupMark,
    val note: SetupNote = SetupNote.None,
    val tookMs: Long? = null,
    val soFarMs: Long? = null,
)

/** "Turn on AI Team on this phone" as the page draws it (Dart `TeamPhoneSetupScreen`). */
public data class TeamSetupUi(
    val phase: SetupPhase = SetupPhase.Idle,
    val steps: ImmutableList<SetupStepUi> = persistentListOf(),
    val problem: SetupProblem? = null,
    val serverState: SetupServerState = SetupServerState.Untouched,
    val terminalsClosed: Boolean = false,
    /** The safe technical code, redacted; Details on a failure. */
    val details: Redacted? = null,
    /** The plain kind of place a failed safety check stopped at, when it did. */
    val proof: SetupProof? = null,
    /** `proot` or `landlock` once the run is done and the engine named it. */
    val protection: String? = null,
) {
    /** The run stopped for a reason that is the person's choice or a missing server: calm, not a failure. */
    val calm: Boolean
        get() = problem == SetupProblem.Declined || problem == SetupProblem.NoServer

    val finished: Int
        get() = steps.count { it.mark == SetupMark.Done }
}

public interface TeamSetupActions {
    public fun back() {}

    /** Start, and Start again: runs the set-up from its first step. */
    public fun start() {}

    /** The person's answer to "Stop OpenCode briefly?". */
    public fun answer(yes: Boolean) {}

    /** Done: back to the team page, which is now the team. */
    public fun openTeam() {}
}

/** The kinds of place a phone safety check stops at (Dart `PhoneProofStepKind`). */
public enum class SetupProofKind { Signal, CheckSignal, NotBlocked, Sandbox, Ubuntu, Fixture, NoAnswer, Proot, Other }

/**
 * The step a boundary proof stopped at (Dart `PhoneProofStep`, issue #111). The native side reports a failed proof as
 * `boundary_proof_failed:<step>`, where `<step>` is a short static code such as `native.signal-31`; it never holds a
 * path or a secret.
 */
public data class SetupProof(val code: String, val kind: SetupProofKind, val check: String? = null, val signal: Int? = null) {
    public companion object {
        private const val PREFIX = "boundary_proof_failed:"
        private val SAFE = Regex("^[A-Za-z0-9._-]{1,120}$")
        private val SIGNAL = Regex("^signal-(\\d{1,3})$")

        private fun signalOf(part: String?): Int? = part?.let { SIGNAL.matchEntire(it)?.groupValues?.get(1)?.toInt() }

        /** Reads the step out of a failure's details, or null when it has none. */
        public fun parse(details: String?): SetupProof? {
            if (details == null) return null
            val at = details.indexOf(PREFIX)
            if (at < 0) return null
            var code = details.substring(at + PREFIX.length)
            val end = code.indexOfFirst { it.isWhitespace() || it == ';' }
            if (end >= 0) code = code.substring(0, end)
            if (!SAFE.matches(code)) return null
            val parts = code.split('.')
            val leg = parts.first()
            val second = parts.getOrNull(1)
            val last = if (parts.size > 1) parts.last() else null
            if (code == "fixture-changed" || code == "proot-tier.fixture-changed" || code == "setup.proof_fixture_unavailable") {
                return SetupProof(code, SetupProofKind.Fixture)
            }
            if (last == "no-answer") return SetupProof(code, SetupProofKind.NoAnswer)
            when (leg) {
                "native" ->
                    if (second != null) {
                        signalOf(second)?.let { return SetupProof(code, SetupProofKind.Signal, signal = it) }
                        if (second == "sandbox") return SetupProof(code, SetupProofKind.Sandbox)
                        if (!second.startsWith("exit-")) {
                            signalOf(parts.getOrNull(2))?.let {
                                return SetupProof(code, SetupProofKind.CheckSignal, check = second, signal = it)
                            }
                            val other = second == "worker-write" || second == "worker-rename"
                            return SetupProof(code, if (other) SetupProofKind.Other else SetupProofKind.NotBlocked, check = second)
                        }
                    }
                "ubuntu" -> if (second != null) return SetupProof(code, SetupProofKind.Ubuntu, check = second)
                "proot-tier" -> if (second != null) return SetupProof(code, SetupProofKind.Proot, check = second)
                "setup" -> if (second != null) return SetupProof(code, SetupProofKind.Other, check = second)
            }
            return SetupProof(code, SetupProofKind.Other, check = code)
        }
    }
}

/** Pure mapping from the engine's run to the page (tested without Compose). */
public object TeamSetupPresent {
    public fun present(state: SetupState, nowMs: Long, redact: (String) -> Redacted): TeamSetupUi {
        val steps =
            SetupStep.entries.map { step ->
                val took = state.tookMs[step]
                val began = state.beganAtMs[step]
                val failed = state.phase == SetupPhase.Failed && state.failedStep == step
                when {
                    failed ->
                        SetupStepUi(
                            step,
                            SetupMark.Failed,
                            note =
                                if (step == SetupStep.Check && state.problem == SetupProblem.Unsafe && state.details.orEmpty().contains("not_packaged")) {
                                    SetupNote.NotPackaged
                                } else {
                                    SetupNote.None
                                },
                        )
                    took != null ->
                        if (step in state.skipped) SetupStepUi(step, SetupMark.Done, SetupNote.NotNeeded)
                        else SetupStepUi(step, SetupMark.Done, tookMs = took)
                    began != null ->
                        SetupStepUi(
                            step,
                            SetupMark.Working,
                            note =
                                when {
                                    step == SetupStep.Reply -> SetupNote.ReplyWaiting
                                    step == SetupStep.Stop && state.phase == SetupPhase.Confirming -> SetupNote.StopWaiting
                                    else -> SetupNote.None
                                },
                            soFarMs = (nowMs - began).coerceAtLeast(0),
                        )
                    else -> SetupStepUi(step, SetupMark.Waiting)
                }
            }
        return TeamSetupUi(
            phase = state.phase,
            steps = steps.toImmutableList(),
            problem = state.problem,
            serverState = state.serverState,
            terminalsClosed = state.terminalsClosed,
            details = state.details?.takeIf { state.phase == SetupPhase.Failed && state.problem !in CALM }?.let(redact),
            proof = state.details?.takeIf { state.phase == SetupPhase.Failed }?.let { SetupProof.parse(it) },
            protection = state.boundaryTier?.takeIf { state.phase == SetupPhase.Done && (it == "proot" || it == "landlock") },
        )
    }

    private val CALM = setOf(SetupProblem.Declined, SetupProblem.NoServer)
}

/**
 * "Turn on AI Team on this phone" (Dart `TeamPhoneSetupScreen`): the person taps once and watches four real steps, each
 * with its own time and a mark. The run belongs to the engine, so leaving this page does not cut it short and opening
 * it again joins it. A run that is not going and not done starts when the page opens (the tap that brought the person
 * here was the start).
 */
@AssistedInject
public class TeamSetupViewModel(
    @Assisted private val route: TeamRoute.TurnOn,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
    private val redactor: Redactor,
) : ViewModel(), TeamSetupActions {
    private val profile = route.profileId.raw

    public val state: StateFlow<TeamSetupUi> =
        combine(engine.setupState(profile), ticks()) { run, now -> TeamSetupPresent.present(run, now, redactor::details) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamSetupUi())

    init {
        val phase = engine.setupState(profile).value.phase
        if (phase != SetupPhase.Done && !engine.setupState(profile).value.running) start()
    }

    /** One tick a second while a run is going: the "so far" times. */
    private fun ticks(): Flow<Long> =
        flow {
            while (true) {
                emit(clock.now().toEpochMilliseconds())
                delay(1_000)
            }
        }

    override fun start() {
        viewModelScope.launch { engine.runSetup(profile) }
    }

    override fun answer(yes: Boolean) {
        engine.answerSetup(profile, yes)
    }

    override fun openTeam() {
        engine.dismissSetup(profile)
        nav.back()
    }

    override fun back(): Unit = nav.back()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.TurnOn): TeamSetupViewModel
    }
}
