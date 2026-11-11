package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant
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
     * The phone's one-tap set-up for [profileId] (Flutter `openPhoneTeamSetup`): the ports that stop OpenCode and the
     * terminals, start the bundled engine, bring OpenCode back protected and keep the credential and the team config.
     * Null where no wiring can (the run then ends at "add a server first").
     */
    public fun phoneSetup(profileId: String): PhoneSetupPorts? = null

    /**
     * Turns the phone's team off for [profileId] (Flutter `turnOffTeam`): stops the bundled engine and clears the
     * profile's team config, so the next [config] answers null. Keeps the profile's projects on the phone; never retried.
     */
    public suspend fun turnOffPhoneTeam(profileId: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("team.off")))

    /**
     * Turns a computer's team off for [profileId] (Flutter `turnOffTeam` for Gas City): clears the profile's team
     * config, so the next [config] answers null. Nothing changes on the computer; never retried.
     */
    public suspend fun turnOffComputerTeam(profileId: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("team.off")))

    /**
     * Saves [config] as the profile's computer team (Flutter `saveTeamHost`): the one `orchestration` object the profile
     * already keeps, so a profile moved over from Flutter reads it unchanged. No credential is stored for this host and
     * no other key is written. The caller has checked the address rule; never retried.
     */
    public suspend fun saveComputerTeam(profileId: String, config: TeamConfig): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("team.address")))
}

/** What the team page shows (Dart `TeamPage`'s branches). */
public enum class TeamPhase {
    /** The first read is under way. */
    Loading,

    /** No team on this server: the intro and turn-on. */
    Off,

    /**
     * A team that runs on a computer (Gas City): [TeamEngine.fleet] has it ([TeamState.config] is its address). Also
     * the demo fixture, which only says so.
     */
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
    /** [workspace] is the one kept from an earlier run, not read yet in this one: any live read replaces it. */
    val fromKept: Boolean = false,
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
    private val kept: TeamLastKnown? = null,
    /** Times the turn-on's steps; the app passes its clock, a test that does not read times needs none. */
    private val clock: Clock = Unset,
    /** A computer's team (Gas City); null where no wiring can reach one. */
    public val fleet: io.github.eslamasabry.oc.engine.team.fleet.FleetEngine? = null,
    /** How the address form reaches a computer to test it; null where no wiring can (the test then finds nobody). */
    private val computerTransports: io.github.eslamasabry.oc.engine.team.fleet.FleetTransports? = null,
    /** Last, so a test can pass it as a trailing lambda. */
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
            // Cache-first: the kept workspace is the first frame, drawn as the real (not stale) Ready layout.
            val seed = kept?.peek(profileId)?.decode()
            val created = MutableStateFlow(if (seed != null) TeamState(TeamPhase.Ready, workspace = seed, fromKept = true) else TeamState())
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
            if (state.value.workspace == null) {
                kept?.read(profileId)?.decode()?.let { seed ->
                    state.update { if (it.workspace == null) it.copy(phase = TeamPhase.Ready, workspace = seed, fromKept = true) else it }
                }
            }
            val config = host.config(profileId)
            if (config == null) {
                state.value = TeamState(TeamPhase.Off)
                return@withLock Outcome.Ok(Unit)
            }
            if (config.provider != TeamProvider.PhoneEngine) {
                state.value = TeamState(TeamPhase.Computer, config)
                return@withLock Outcome.Ok(Unit)
            }
            // Before the first live read the workspace is the kept one (no config yet): it stands in for the last read.
            val fromKept = state.value.fromKept && state.value.workspace != null
            val previous = state.value.workspace?.takeIf { fromKept || state.value.config == config }
            when (val read = host.workspace(profileId, config)) {
                is Outcome.Ok -> {
                    // Dart `teamWorkspace()` words the engine's raw states before any page reads them.
                    val fresh = TeamPhonePresentation.present(read.value.workspace, read.value.health.commandActions)
                    val next = fresh.takeIf { previous == null || fromKept || it.revision >= previous.revision } ?: previous
                    state.update {
                        it.copy(
                            phase = TeamPhase.Ready,
                            config = config,
                            workspace = next,
                            health = read.value.health,
                            loading = false,
                            stale = false,
                            fromKept = false,
                        )
                    }
                    next?.let { w -> KeptTeam.of(w)?.let { kept?.keep(profileId, it) } }
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

    private val setups = AtomicReference<PersistentMap<String, PhoneTeamSetup>>(persistentHashMapOf())

    private fun setup(profileId: String): PhoneTeamSetup {
        while (true) {
            val current = setups.load()
            current[profileId]?.let { return it }
            val created = PhoneTeamSetup(host.phoneSetup(profileId) ?: NoPhoneSetup, clock)
            if (setups.compareAndSet(current, current.put(profileId, created))) return created
        }
    }

    /** The turn-on's progress for [profileId]: it outlives the page that started it, so a page opened later joins it. */
    public fun setupState(profileId: String): StateFlow<SetupState> = setup(profileId).state

    /**
     * Runs the phone's set-up for [profileId] (the off page's primary, the phone-off page's, Team settings'), then reads
     * the team again, so the page behind becomes the team (Flutter `TeamPage`: no hop back, no second page). A run
     * already going is joined, not doubled. Not on the lane: a person may take minutes to answer its one question.
     */
    public suspend fun runSetup(profileId: String) {
        val run = setup(profileId)
        run.run()
        if (run.state.value.phase == SetupPhase.Done) refresh(profileId)
    }

    /** The person's yes or no to stopping OpenCode and the terminals. */
    public fun answerSetup(profileId: String, yes: Boolean): Unit = setup(profileId).answer(yes)

    /** Forgets a finished or failed run, so the page shows its start again. */
    public fun dismissSetup(profileId: String): Unit = setup(profileId).dismiss()

    /** Whether [runSetup] can work for [profileId] here ([TeamHost.canTurnOnPhoneTeam]). */
    public suspend fun canTurnOn(profileId: String): Boolean = host.canTurnOnPhoneTeam(profileId)

    /**
     * Turns the team off for [profileId]: the engine stops, the config goes and the kept workspace is blanked, so no
     * old team shows first next time. A failure leaves the team as it was.
     */
    public suspend fun turnOff(profileId: String): Outcome<Unit> {
        val provider = (flow(profileId).value.config ?: host.config(profileId))?.provider
        val done =
            lane.withLock {
                if (provider == null || provider == TeamProvider.PhoneEngine) host.turnOffPhoneTeam(profileId)
                else host.turnOffComputerTeam(profileId)
            }
        if (done is Outcome.Ok) {
            fleet?.forget(profileId)
            // The kept shelf has no delete: an empty workspace reads back as nothing to show.
            kept?.keep(profileId, KeptTeam(""))
            setup(profileId).dismiss()
            forget(profileId)
            refresh(profileId)
        }
        return done
    }

    /**
     * Tests an address for a computer's team before it is saved (Flutter's Test and turn on): the address rule first,
     * then the same probe the live page uses. Writes nothing.
     */
    public suspend fun testComputerTeam(url: String, city: String?): io.github.eslamasabry.oc.engine.team.fleet.ComputerTeamTest {
        val transports = computerTransports
        val notFound = { why: io.github.eslamasabry.oc.engine.team.fleet.FleetFailure ->
            io.github.eslamasabry.oc.engine.team.fleet.ComputerTeamTest.NotFound(why)
        }
        if (transports == null) return notFound(io.github.eslamasabry.oc.engine.team.fleet.FleetFailure.Unreachable)
        return io.github.eslamasabry.oc.engine.team.fleet.testComputerTeam(transports, url.trim(), city?.trim())
    }

    /**
     * Saves the address as [profileId]'s computer team (Flutter `saveTeamHost`) and shows the team from it. A refused
     * address (not https, and not this device or a Tailscale address) is never saved. The old host's snapshot is dropped
     * so nothing of it shows under the new address; the computer itself is not touched. A failure changes nothing.
     */
    public suspend fun saveComputerTeam(profileId: String, url: String, city: String, front: Boolean): Outcome<Unit> {
        val address = url.trim()
        if (!io.github.eslamasabry.oc.engine.team.fleet.FleetAddress.allowed(address)) {
            return Outcome.Failed(Failure.Unavailable(FeatureId("team.address")))
        }
        val team = TeamConfig(TeamProvider.GasCity, address, city.trim(), TeamHostMode.Computer, front)
        val done = lane.withLock { host.saveComputerTeam(profileId, team) }
        if (done is Outcome.Ok) {
            fleet?.forget(profileId)
            forget(profileId)
            refresh(profileId)
        }
        return done
    }

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

/** The ports of a host that cannot set the phone's team up: the run ends at "add a server first". */
internal object NoPhoneSetup : PhoneSetupPorts {
    private val none = Outcome.Failed(Failure.Unavailable(FeatureId("team.on")))

    override suspend fun hasServer(): Boolean = false

    override suspend fun replyRunning(): Boolean = false

    override suspend fun inspect(): SetupHost = SetupHost(serverRunning = false, terminals = 0, restartRequired = false)

    override suspend fun stopServer(): Outcome<Unit> = none

    override suspend fun closeTerminals(): Outcome<Unit> = none

    override suspend fun startEngine(): Outcome<SetupHealth> = none

    override suspend fun stopEngine() = Unit

    override suspend fun probeEngine(): Outcome<SetupHealth> = none

    override suspend fun startServer(): Outcome<Unit> = none

    override suspend fun saveTeam(): Outcome<Unit> = none

    override suspend fun restoreServer(): Boolean = false
}

internal object Unset : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(0)
}
