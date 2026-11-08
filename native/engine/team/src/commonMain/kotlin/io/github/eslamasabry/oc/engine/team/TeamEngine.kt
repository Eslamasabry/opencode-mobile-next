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
)

/** One serial lane for every profile's reads; each profile has its own state. */
public class TeamEngine(private val host: TeamHost) {
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
                    state.value = TeamState(TeamPhase.Ready, config, next)
                    Outcome.Ok(Unit)
                }
                is Outcome.Failed -> {
                    log.i { "workspace read failed: ${read.failure::class.simpleName}" }
                    state.value =
                        if (previous == null) TeamState(TeamPhase.PhoneOff, config)
                        else TeamState(TeamPhase.Ready, config, previous, stale = true)
                    read
                }
            }
        }

    /** Forgets [profileId]'s state (the profile was deleted or the team turned off). */
    public fun forget(profileId: String) {
        flow(profileId).value = TeamState()
    }

    public companion object {
        /** Dart `PhoneEngineGateway.pollInterval`: how often an open team page reads again. */
        public const val POLL_MS: Long = 3_000
    }
}
