package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app's side of the AI Team (Dart `TeamProjectController` + `TeamPage`'s choice of what to show). The wiring reads
 * the saved profile, the Keystore token and the engine's loopback HTTP; the engine never stores the workspace, so the
 * phone engine stays the one truth (a project Flutter's team started keeps going there and shows here).
 */
public interface TeamHost {
    /** The profile's saved team config (Flutter `ServerProfile.orchestration`); null while the team is off. */
    public suspend fun config(profileId: String): TeamConfig?

    /**
     * One read of the phone engine for [profileId]: `GET /v1/health` (the profile and schema it answers for), then
     * `GET /v1/workspace`. Fails when the engine is not running, answers for another profile or breaks the contract.
     */
    public suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamWorkspace>

    /**
     * Sends one command (Dart `PhoneEngineGateway.executeProject`): `GET /v1/health` first, refusing an action the
     * engine does not list (`unsupportedCommand`) or one that runs work while it cannot execute
     * (`boundaryUnverified`, `protocolUnverified`, `engineUnavailable`), then one `POST /v1/commands` with [body].
     * Never retried.
     */
    public suspend fun command(
        profileId: String,
        config: TeamConfig,
        command: TeamCommand,
        body: Map<String, Any?>,
    ): Outcome<TeamCommandResult>
}

/** What the team page shows (Dart `TeamPage`'s branches). */
public enum class TeamPhase {
    /** The first read is under way. */
    Loading,

    /** No team on this server: the intro and turn-on. */
    Off,

    /** A computer's team (Gas City) or the demo: not shown by the native team yet. */
    Computer,

    /** The phone's team is on but not answering, and nothing was read before (Dart `PhoneTeamOffPage`). */
    PhoneOff,

    /** The projects, possibly last-known ([TeamState.stale]). */
    Ready,
}

public data class TeamState(
    val phase: TeamPhase = TeamPhase.Loading,
    val config: TeamConfig? = null,
    val workspace: TeamWorkspace? = null,
    /** A read is under way. */
    val loading: Boolean = false,
    /** Dart `errorCode == 'unavailable'`: the last read failed and [workspace] is the last known one. */
    val stale: Boolean = false,
    /** A command is on its way (Dart `busy`): one at a time. */
    val busy: Boolean = false,
    /** The last command's refusal code (Dart `errorCode` after `execute`); null after a success. */
    val refusal: String? = null,
)

/** One serial lane for every profile's reads; each profile has its own state. */
public class TeamEngine(
    private val host: TeamHost,
    private val newRequestId: () -> String = Ids()::next,
) {
    private val lane = Mutex()
    private val states = AtomicReference<PersistentMap<String, MutableStateFlow<TeamState>>>(persistentHashMapOf())
    private val log = AppLog.tagged("Team")

    public fun state(profileId: String): StateFlow<TeamState> = flow(profileId).asStateFlow()

    private fun flow(profileId: String): MutableStateFlow<TeamState> {
        while (true) {
            val current = states.load()
            current[profileId]?.let { return it }
            val created = MutableStateFlow(TeamState())
            if (states.compareAndSet(current, current.put(profileId, created))) return created
        }
    }

    /**
     * Reads the config and, for the phone's team, the workspace. A failed read keeps the last workspace and marks it
     * stale (Dart `errorCode = 'unavailable'`); an older revision never replaces a newer one.
     */
    public suspend fun refresh(profileId: String): Outcome<Unit> =
        lane.withLock {
            val state = flow(profileId)
            state.update { it.copy(loading = true) }
            val config = host.config(profileId)
            if (config == null) {
                state.value = TeamState(TeamPhase.Off)
                return@withLock Outcome.Ok(Unit)
            }
            if (config.provider != TeamProvider.PhoneEngine) {
                state.value = TeamState(TeamPhase.Computer, config)
                return@withLock Outcome.Ok(Unit)
            }
            val previous = state.value.workspace?.takeIf { state.value.config == config }
            when (val read = host.workspace(profileId, config)) {
                is Outcome.Ok -> {
                    val next = read.value.takeIf { previous == null || it.revision >= previous.revision } ?: previous
                    state.update { it.copy(phase = TeamPhase.Ready, config = config, workspace = next, loading = false, stale = false) }
                    Outcome.Ok(Unit)
                }
                is Outcome.Failed -> {
                    log.i { "workspace read failed: ${read.failure::class.simpleName}" }
                    state.update {
                        if (previous == null) TeamState(TeamPhase.PhoneOff, config)
                        else it.copy(phase = TeamPhase.Ready, config = config, workspace = previous, loading = false, stale = true)
                    }
                    read
                }
            }
        }

    /**
     * Sends [command] for [profileId] and reads the team again (Dart `TeamProjectController.execute`). While one is on
     * its way another is refused with `busy`; a refusal's code stays in [TeamState.refusal] for the page to word.
     */
    public suspend fun execute(profileId: String, command: TeamCommand): Outcome<TeamCommandResult> {
        val state = flow(profileId)
        val config = state.value.config?.takeIf { it.provider == TeamProvider.PhoneEngine }
        var admitted = false
        state.update { current ->
            admitted = !current.busy && config != null
            if (admitted) current.copy(busy = true, refusal = null) else current
        }
        if (!admitted) return Outcome.Ok(TeamCommandResult(accepted = false, code = "busy", projectId = command.projectId))
        try {
            val result = lane.withLock { host.command(profileId, config!!, command, command.body(newRequestId())) }
            val refusal =
                when (result) {
                    is Outcome.Ok -> result.value.code.takeIf { !result.value.accepted }
                    is Outcome.Failed -> "saveFailed"
                }
            state.update { it.copy(refusal = refusal) }
            refresh(profileId)
            return result
        } finally {
            state.update { it.copy(busy = false) }
        }
    }

    /** Clears a shown refusal (the page's notice was dismissed). */
    public fun clearRefusal(profileId: String) {
        flow(profileId).update { it.copy(refusal = null) }
    }

    /** Forgets [profileId]'s state (the profile was deleted or the team turned off). */
    public fun forget(profileId: String) {
        flow(profileId).value = TeamState()
    }

    /** Dart `newRequestId`: unique per command, so a replay of the same request is recognised by the engine. */
    private class Ids {
        private val prefix = kotlin.random.Random.nextLong().toULong().toString(36)
        private val serial = kotlin.concurrent.atomics.AtomicInt(0)

        fun next(): String = "team-$prefix-${serial.fetchAndAdd(1)}"
    }

    public companion object {
        /** Dart `PhoneEngineGateway.pollInterval`: how often an open team page reads again. */
        public const val POLL_MS: Long = 3_000
    }
}
