package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
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
    public suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead>

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

    /**
     * The project editors' drafts for [profileId] (Flutter `oc.teamEditorDrafts.<id>`: one JSON map of target to
     * text, redacted, no secrets); null when none were saved.
     */
    public suspend fun readDrafts(profileId: String): String? = null

    /** Replaces the drafts map; [json] null removes it. */
    public suspend fun writeDrafts(profileId: String, json: String?): Outcome<Unit> = Outcome.Ok(Unit)

    /**
     * Whether this app can turn on the phone's own team for [profileId] now (Flutter's in-app turn-on: the engine is
     * bundled with the app and [profileId] is this phone's OpenCode). False where no wiring can, so the off page
     * offers the set-up step instead of a button that cannot work.
     */
    public suspend fun canTurnOnPhoneTeam(profileId: String): Boolean = false

    /**
     * Turns on the phone's own team for [profileId] (Flutter `openPhoneTeamSetup`): starts the bundled engine, keeps
     * its credential in the Keystore under `oc.teamEngineAuth.<id>` and saves the profile's team config, so the next
     * [config] answers. Never retried; a failure leaves the team off.
     */
    public suspend fun turnOnPhoneTeam(profileId: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("team.on")))
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
    /** The action [refusal] answered, for its words ("The team couldn't start planning"). */
    val refusedAction: String? = null,
    /** The engine's health from the last good read; null before one. */
    val health: TeamHealth? = null,
) {
    /**
     * What the engine can run now; everything for the simulated demo, which is never gated, and before any health is
     * known (Dart `TeamExecutionGate.allows` is true for an unbound controller; the engine still refuses what it cannot).
     */
    public val capabilities: TeamCapabilities
        get() = if (workspace?.simulated == true) TeamCapabilities.ALL else health?.capabilities ?: TeamCapabilities.ALL
}

/** One serial lane for every profile's reads; each profile has its own state. */
public class TeamEngine(
    private val host: TeamHost,
    private val newRequestId: () -> String = Ids()::next,
) {
    private val lane = Mutex()
    private val drafts = Mutex()
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
                    // Dart `teamWorkspace()` words the engine's raw states before any page reads them.
                    val fresh = TeamPhonePresentation.present(read.value.workspace, read.value.health.commandActions)
                    val next = fresh.takeIf { previous == null || it.revision >= previous.revision } ?: previous
                    state.update {
                        it.copy(
                            phase = TeamPhase.Ready,
                            config = config,
                            workspace = next,
                            health = read.value.health,
                            loading = false,
                            stale = false,
                        )
                    }
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
            if (admitted) current.copy(busy = true, refusal = null, refusedAction = null) else current
        }
        if (!admitted) return Outcome.Ok(TeamCommandResult(accepted = false, code = "busy", projectId = command.projectId))
        // Dart `executeProject`: an action the engine does not list, or one that runs work while it cannot, is refused
        // here without a request.
        state.value.health?.takeIf { state.value.workspace?.simulated != true }?.refusal(command)?.let { code ->
            state.update { it.copy(busy = false, refusal = code, refusedAction = command.action) }
            return Outcome.Ok(TeamCommandResult(accepted = false, code = code, projectId = command.projectId))
        }
        try {
            val result = lane.withLock { host.command(profileId, config!!, command, command.body(newRequestId())) }
            val refusal =
                when (result) {
                    is Outcome.Ok -> result.value.code.takeIf { !result.value.accepted }
                    is Outcome.Failed -> "saveFailed"
                }
            state.update { it.copy(refusal = refusal, refusedAction = command.action.takeIf { refusal != null }) }
            refresh(profileId)
            return result
        } finally {
            state.update { it.copy(busy = false) }
        }
    }

    /**
     * Turns the phone's own team on for [profileId] (the off page's primary), then reads the team again, so the same
     * page becomes the team (Flutter `TeamPage`: no hop back, no second page). On the lane, so no read overlaps it.
     */
    public suspend fun turnOn(profileId: String): Outcome<Unit> {
        val done = lane.withLock { host.turnOnPhoneTeam(profileId) }
        if (done is Outcome.Ok) refresh(profileId)
        return done
    }

    /** Whether [turnOn] can work for [profileId] here ([TeamHost.canTurnOnPhoneTeam]). */
    public suspend fun canTurnOn(profileId: String): Boolean = host.canTurnOnPhoneTeam(profileId)

    /** Clears a shown refusal (the page's notice was dismissed). */
    public fun clearRefusal(profileId: String) {
        flow(profileId).update { it.copy(refusal = null, refusedAction = null) }
    }

    /** Dart `readEditorDraft`: the saved text for [target] in [profileId]'s drafts, or null. */
    public suspend fun draft(profileId: String, target: String): String? =
        drafts.withLock { (host.readDrafts(profileId)?.let(DartJson::decodeOrNull) as? Map<*, *>)?.get(target) as? String }

    /**
     * Dart `saveEditorDraft` / `clearEditorDraft`: one serial writer, so two editors never lose each other's text.
     * [text] null removes [target] and its per-field drafts; an empty map removes the key.
     */
    public suspend fun saveDraft(profileId: String, target: String, text: String?): Outcome<Unit> =
        drafts.withLock {
            val current = (host.readDrafts(profileId)?.let(DartJson::decodeOrNull) as? Map<*, *>).orEmpty()
            val next = LinkedHashMap<String, Any?>()
            // Clearing also drops the per-field drafts Flutter's KitDraft kept under "<target>.<field>".
            current.forEach { (k, v) -> if (k is String && k != target && !(text == null && k.startsWith("$target."))) next[k] = v }
            if (text != null) next[target] = text
            host.writeDrafts(profileId, if (next.isEmpty()) null else DartJson.encode(next))
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
