package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import android.os.SystemClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.diagnostics.AgentHelperStatus
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateDecision
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.engine.agents.scripts.PaseoPhoneScripts
import io.github.eslamasabry.oc.engine.agents.diagnostics.HelperNotice
import io.github.eslamasabry.oc.engine.agents.firstuse.AgentFirstUse
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.install.AgentInstaller
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupFailure
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupPhase
import io.github.eslamasabry.oc.engine.agents.install.AgentVersionCheck
import io.github.eslamasabry.oc.engine.agents.install.InstallRefusal
import io.github.eslamasabry.oc.engine.agents.install.InstallStart
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.probe.AuthScriptAction
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.removal.AgentRemoval
import io.github.eslamasabry.oc.engine.agents.removal.AgentRemovalRunner
import io.github.eslamasabry.oc.engine.agents.removal.RemovalReceipt
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRun
import io.github.eslamasabry.oc.engine.agents.scripts.AgentPhoneScripts
import io.github.eslamasabry.oc.engine.agents.scripts.AgentRemovalScripts
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.profiles.SignInReset
import io.github.eslamasabry.oc.engine.profiles.SignInResetParticipant
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallFailure
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemoval
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemovalIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentSpace
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentVersions
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentUpdateNote
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneDevice
import io.github.eslamasabry.oc.feature.agents.hub.phoneInstallStep
import io.github.eslamasabry.oc.feature.agents.hub.phoneInstalledAgents
import io.github.eslamasabry.oc.feature.agents.hub.phoneSharedPartsInPlace
import io.github.eslamasabry.opencode_mobile.BuildConfig
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.PhoneAgentPayloads
import io.github.eslamasabry.opencode_mobile.ProbeStop
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.security.SecureRandom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

/**
 * The Agents hub reads the real shared payloads and removes only catalog-owned files, keeping
 * profile homes. "Check phone agents" also runs the read-only sign-in probe for the runtime owner:
 * no helper, sign-in or installer is started.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<PhoneAgents>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class AppPhoneAgents(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val setupJobs: NativeAgentSetupJobs,
    private val registry: ConnectionRegistry,
    private val redactor: Redactor,
    private val payloads: PhonePayloadGuard,
) : PhoneAgents, ProfileCloser {
    override val owner: String = ":engine:agents:auth"
    private val admission = Any()
    private val denied = HashSet<ProfileId>()
    private val accounts = HashMap<Pair<ProfileId, AgentId>, AuthProjection>()
    @Volatile private var needsRestart = false
    private val catalog = AgentCatalog.builtIn
    private val mutable = MutableStateFlow(PhoneAgentsState(agents = catalogRows()))
    override val state = mutable.asStateFlow()
    /** Reads and steps take turns here; a read gives way to a step instead of making it wait ([PhoneReadLane]). */
    private val lane = PhoneReadLane { block -> withContext(dispatchers.io) { block() } }
    private val linux by lazy { BuiltinLinux.get(app) }
    private val installers = HashMap<ProfileId, AgentInstaller>()

    private fun installation(owner: ProfileId): AgentInstaller =
        installers.getOrPut(owner) {
            AgentInstaller(
                KvAgentPrefs(stores.prefs, stores.lanes.forProfile(owner)),
                setupJobs,
                guard = setupJobs.guard,
            )
        }

    /** What the phone check needs to know about this phone, or null when it cannot run here. */
    internal class CheckSite(
        val owner: ProfileId,
        val agent: AgentDescriptor,
        val architecture: AgentArchitecture,
    )

    internal fun checkSite(id: String): CheckSite? {
        val owner = agentOwner(ownerId()) ?: return null
        val agent = catalog.byId(AgentId(id))?.takeIf { it.recipe != null } ?: return null
        return CheckSite(owner, agent, architecture() ?: return null)
    }

    private fun gatePrefs(owner: ProfileId) = KvAgentPrefs(stores.prefs, stores.lanes.forProfile(owner))

    /**
     * The proof stored for [site]'s agent is dropped before a check, as Flutter did (a failed check proves nothing),
     * and so is the registry's proof of the version that ran ([PhoneAgentFacts.Fact.proven]).
     */
    internal suspend fun clearGate(site: CheckSite): Outcome<Unit> {
        val prefs = gatePrefs(site.owner)
        val gates = LinkedHashMap<String, JsonElement>(PhoneGate.read(prefs, site.owner)).apply { remove(site.agent.id.raw) }
        forgetVersion(site)
        keepFacts(site.owner) {
            PhoneAgentFacts.forget(it, site.agent.id.raw, version = false, signIn = false, proof = true)
        }
        return PhoneGate.write(prefs, site.owner, gates)
    }

    /** `oc.agentPhoneGate.<owner>` entry for a passed check, the way Flutter stored it. */
    internal suspend fun writeGate(site: CheckSite): Outcome<Unit> {
        val prefs = gatePrefs(site.owner)
        val gates = LinkedHashMap<String, JsonElement>(PhoneGate.read(prefs, site.owner))
        gates[site.agent.id.raw] =
            PhoneGate.entry(
                PhoneGate.fingerprint(site.agent, site.architecture, AgentPins.builtIn),
                site.architecture,
                site.agent.recipe!!.version,
            )
        return PhoneGate.write(prefs, site.owner, gates)
    }

    internal fun forgetVersion(site: CheckSite) {
        synchronized(admission) { versions.remove(site.owner to site.agent.id) }
    }

    /** The pinned version runs and the shared project folder is usable: the check's Version step. */
    internal suspend fun pinnedRuns(site: CheckSite): Boolean {
        forgetVersion(site)
        val recipe = site.agent.recipe ?: return false
        val read =
            native {
                val version = linux.agentHost.checks.version(site.owner.raw, recipe.executable, recipe.version)
                val shared = linux.agentHost.checks.workspace(site.owner.raw)["shared"] == true
                version["installed"] == true && shared
            }
        return (read as? Outcome.Ok)?.value == true
    }

    /**
     * What the automatic agent update ([PhoneAgentAutoUpdate]) reads: the agents the last reads found installed (kept
     * across app starts, [PhoneAgentFacts]), those whose copy is not the pinned version, and the helper's own status.
     * Nothing runs in Linux here except the helper's status read, and no agent is probed. Null when the phone's agents
     * cannot be read (no Linux, no phone server, a restart pending).
     */
    internal class UpdateFacts(
        val installed: List<AgentId>,
        val behind: Set<AgentId>,
        val helperRunning: Boolean,
        val helperVersion: String?,
        /** The helper's install is not at its pin on disk ([readForUpdate]); false when unknown. */
        val helperOnDiskBehind: Boolean = false,
    )

    /** Whether the helper's install was in place at its pin when [readForUpdate] last looked; null before it ran. */
    @Volatile private var helperInPlace: Boolean? = null

    /** The app build the last [readForUpdate] ran under, kept per phone: `oc.agentUpdateRead.<owner>`. */
    /** An install that brings the helper to its pin ended: the on-disk check no longer says behind. */
    internal fun noteHelperInPlace() {
        helperInPlace = true
    }

    /** The stores are loaded before the updater reads the owner and the kept facts (they load lazily). */
    internal suspend fun warmForUpdate(): Boolean = stores.warm() is Outcome.Ok

    /** What the automatic update says on the Agents page's status line; kept so every read's state carries it. */
    @Volatile private var updateNote: Pair<PhoneAgentUpdateNote?, String?> = null to null

    /** The updater's last decision, as the status line words it ([PhoneAgentsState.updateNote]). */
    internal fun setUpdateNote(decision: AgentUpdateDecision) {
        val name = decision.agent?.let { catalog.byId(it)?.name }
        val note =
            when (decision.step) {
                AgentUpdateStep.NotLooked,
                AgentUpdateStep.NothingBehind -> null
                AgentUpdateStep.Reading -> PhoneAgentUpdateNote.Reading
                AgentUpdateStep.ReadFailed -> PhoneAgentUpdateNote.ReadFailed
                AgentUpdateStep.WaitingAgentsBusy -> PhoneAgentUpdateNote.WaitingBusy
                AgentUpdateStep.WaitingSetup -> PhoneAgentUpdateNote.WaitingSetup
                AgentUpdateStep.WaitingSwitch -> PhoneAgentUpdateNote.WaitingSwitch
                AgentUpdateStep.WaitingMetered -> PhoneAgentUpdateNote.WaitingMetered
                AgentUpdateStep.Updating -> PhoneAgentUpdateNote.Updating
                AgentUpdateStep.Updated -> PhoneAgentUpdateNote.Updated
                AgentUpdateStep.Refused,
                AgentUpdateStep.Failed -> PhoneAgentUpdateNote.Refused
            }
        updateNote = note to name
        mutable.update { it.copy(updateNote = note, updateAgentName = name) }
    }

    /** The state with the updater's note; a signed-out agent is the more important thing to say once the update is done. */
    private fun withUpdateNote(state: PhoneAgentsState): PhoneAgentsState {
        val base = withOwnAgents(state).copy(updateNote = updateNote.first, updateAgentName = updateNote.second)
        val needs = base.agents.firstOrNull { it.status == PhoneAgentStatus.SignInNeeded && !it.signInCheckFailed }
        return if (needs != null && (base.updateNote == null || base.updateNote == PhoneAgentUpdateNote.Updated))
            base.copy(updateNote = PhoneAgentUpdateNote.NeedsSignIn, updateAgentName = needs.name)
        else base
    }

    /**
     * Issue 147: the person's own agents join the rows after the catalog's, so New conversation and every other reader
     * offers them. Their state is set up at first use, already seen set up (they have no sign-in the app can read; their
     * first answer tells), or Stopped while the helper is down as for every agent; never installable or removable here
     * (the Agents page's own-agent sheet removes them).
     */
    private fun withOwnAgents(state: PhoneAgentsState): PhoneAgentsState {
        val owner = state.owner ?: return state
        if (!state.available || state.needsRestart) return state
        val own =
            try {
                ownAgentStore(stores, owner, redactor::registerSecret).read(owner)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        if (own.isEmpty()) return state
        val helperDown = state.agents.any { it.status == PhoneAgentStatus.Stopped }
        val rows =
            own.filter { agent -> state.agents.none { it.id == agent.id.raw } }.map { agent ->
                PhoneAgentRow(
                    id = agent.id.raw,
                    name = agent.name,
                    status = if (helperDown) PhoneAgentStatus.Stopped else PhoneAgentStatus.SetUpAtFirstUse,
                    supportedInstallation = false,
                    needsSetUp = false,
                )
            }
        return state.copy(agents = (state.agents + rows).toImmutableList())
    }

    private fun updateReadKey(owner: ProfileId) = "oc.agentUpdateRead.${owner.raw}"

    /**
     * True at the first look after an app update (the build differs from the one the last read ran under) or before
     * any read: the automatic update then asks for one [readForUpdate] before it decides.
     */
    @Suppress("TooGenericExceptionCaught")
    internal fun updateReadDue(): Boolean =
        try {
            if (needsRestart) false
            else {
                val owner = agentOwner(ownerId())
                owner != null && gatePrefs(owner).string(updateReadKey(owner)) != BuildConfig.VERSION_CODE.toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    /** The agent whose automatic update was started and not finished, kept per phone: `oc.agentUpdating.<owner>`. */
    private fun updatingKey(owner: ProfileId) = "oc.agentUpdating.${owner.raw}"

    /** Records that the automatic update of [agent] runs (null: it ended), so a stop in the middle is resumed. */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun markUpdating(agent: AgentId?) {
        try {
            val owner = agentOwner(ownerId()) ?: return
            gatePrefs(owner).commit {
                if (agent == null) remove(updatingKey(owner)) else putString(updatingKey(owner), agent.raw)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A mark that could not be kept only means the next run does not resume; it never stops the update.
        }
    }

    /** The agent whose automatic update an earlier run of the app started and did not finish; null when none. */
    @Suppress("TooGenericExceptionCaught")
    internal fun interruptedUpdate(): AgentId? =
        try {
            val owner = agentOwner(ownerId())
            val raw = owner?.let { gatePrefs(it).string(updatingKey(it)) }
            raw?.takeIf { ID_SHAPE.matches(it) }?.let(::AgentId)?.takeIf { catalog.byId(it)?.recipe != null }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /**
     * One read of every catalog agent's installed version (the Agents page's own cheap read: the payload check and
     * the version program, no sign-in probe, no helper start), kept as facts, and of whether the helper's install is
     * in place at its pin. It gives way to a person's step and never runs under an install. True when it completed.
     */
    internal suspend fun readForUpdate(): Boolean = lane.read { stop -> readForUpdateLocked(stop) } == true

    private suspend fun readForUpdateLocked(stop: ProbeStop): Boolean {
        if (needsRestart) return false
        val owner = agentOwner(ownerId()) ?: return false
        if (mutable.value.installation?.phase == PhoneAgentInstallPhase.Installing) return false
        val agents = phoneCatalog()
        val read =
            native {
                if (!linux.installed) return@native false
                for (agent in agents) {
                    if (stop.isStopped) return@native false
                    val executable = agent.recipe?.executable ?: continue
                    val payload = PhoneAgentPayloads.present(linux, executable)
                    versionOf(owner, agent, payload, installing = false, stop = stop)
                }
                !stop.isStopped
            }
        if ((read as? Outcome.Ok)?.value != true) return false
        // The versions just read are kept as facts; a kept sign-in answer stays (this read does not probe it).
        val merged =
            keepFacts(owner) { kept ->
                PhoneAgentFacts.merge(
                    kept,
                    agents.associate { agent ->
                        agent.id.raw to
                            PhoneAgentFacts.of(knownVersion(owner, agent), null, helperPin, appBuild)
                                .copy(
                                    signedIn = kept[agent.id.raw]?.signedIn,
                                    signedInAt = kept[agent.id.raw]?.signedInAt,
                                )
                    },
                )
            }
        val anyInstalled = merged.values.any { it.installed == true || it.found != null }
        if (anyInstalled) {
            val place =
                native { linux.run(PaseoPhoneScripts.check, 120, agentUser = true, logOutput = false).exitCode }
            helperInPlace = (place as? Outcome.Ok)?.value?.let { it == 0 }
        }
        if (stop.isStopped) return false
        gatePrefs(owner).commit { putString(updateReadKey(owner), BuildConfig.VERSION_CODE.toString()) }
        return true
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun updateFacts(): UpdateFacts? =
        try {
            updateFactsRead()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun updateFactsRead(): UpdateFacts? {
        if (needsRestart || !linux.installed) return null
        val owner = agentOwner(ownerId()) ?: return null
        val kept = keptFacts(owner)
        // A copy that runs but is not the pinned version has installed=false in the version answer (that field means
        // "the pin is installed") and a found version: it is installed, and behind.
        val installed =
            phoneCatalog().filter { kept[it.id.raw]?.let { fact -> fact.installed == true || fact.found != null } == true }
        val behind =
            installed
                .filter { agent ->
                    val found = kept[agent.id.raw]?.found
                    found != null && found != agent.recipe?.version
                }
                .map { it.id }
                .toSet()
        // A helper whose status cannot be read (a half-written record after a stop in the middle of an install) is
        // simply not known to run; it never ends the app.
        val helper =
            try {
                linux.agentHost.status(owner.raw)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
        return UpdateFacts(
            installed.map { it.id },
            behind,
            helper["running"] == true,
            helper["helperVersion"] as? String,
            helperOnDiskBehind = installed.isNotEmpty() && helperInPlace == false,
        )
    }

    /** Only the payload and version probes from this process, never the persisted recall rows. */
    internal suspend fun choiceVersions(): Map<AgentId, String> = lane.read { stop ->
        val owner = agentOwner(ownerId())
        if (owner == null || needsRestart || !linux.installed) return@read emptyMap()
        val installing = setupJobs.anySetupRunning() != false
        buildMap {
            for (agent in phoneCatalog()) {
                if (stop.isStopped) break
                val present = PhoneAgentPayloads.present(linux, agent.recipe?.executable ?: continue)
                val answer = versionOf(owner, agent, present, installing, stop)
                val version = answer?.get("foundVersion") as? String
                if (version != null) put(agent.id, version)
            }
        }
    }.orEmpty()

    /** A shared-only job never includes the anchor agent's own payload step. */
    internal suspend fun startSharedUpdate(anchor: AgentId): Boolean {
        val owner = ownerId() ?: return false
        val shared = installation(owner).components(anchor).filterNot { it.id == "agent-${anchor.raw}" }
        return setupJobs.start(owner, anchor, shared) is Outcome.Ok
    }

    private fun ownerId(): ProfileId? =
        try {
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)
                ?.value
                ?.takeIf { it.isNotEmpty() }
                ?.let(::ProfileId)
                ?.takeIf { stores.profiles.profile(it) != null }
        } catch (e: Exception) {
            null
        }

    private fun agentOwner(profile: ProfileId?): ProfileId? = profile?.let {
        (stores.prefs.get("oc.phoneAgentOwner.${it.raw}") as? PrefValue.Str)
            ?.value
            ?.takeIf { value -> Regex("[A-Za-z0-9_-]{1,80}").matches(value) }
            ?.let(::ProfileId) ?: it
    }

    private fun architecture(): AgentArchitecture? =
        PhoneAgentRules.architectureOf(Build.SUPPORTED_ABIS.toList())

    /**
     * The phone's boot or the app's install/update, whichever is later (wall clock): a helper exit from before it is
     * not said as "stopped in the background" ([AgentHelperStatus.noticeSince]).
     */
    private val stopNewsSince: Long by lazy {
        val boot = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val updated =
            try {
                @Suppress("DEPRECATION") app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
            } catch (_: Exception) {
                0L
            }
        maxOf(boot, updated)
    }

    /** "arm64 · Android 15" for the Agents page's phone status line. */
    private fun device(): PhoneDevice? {
        val processor =
            when (architecture()) {
                AgentArchitecture.Arm64 -> "arm64"
                AgentArchitecture.X64 -> "x86_64"
                null -> return null
            }
        return PhoneDevice(processor, Build.VERSION.RELEASE ?: return null)
    }

    /** Catalog discovery never needs an installed Linux or a selected phone server. */
    private fun catalogRows() =
        catalog.agents
            .filter {
                it.runsOnPhoneHost &&
                    it.recipe != null &&
                    it.availability != AgentAvailability.Hidden &&
                    // A retired agent (Gemini CLI) is offered nowhere; an installed copy appears after the read.
                    it.listedOnPhone(installed = false)
            }
            .map { agent ->
                PhoneAgentRow(
                    id = agent.id.raw,
                    name = agent.name,
                    status = PhoneAgentStatus.Checking,
                    downloadBytes = architecture()?.let(agent::artifactFor)?.downloadBytes,
                    supportedInstallation = architecture()?.let(agent::installableOn) == true,
                )
            }
            .toImmutableList()

    private val removal =
        AgentRemoval(
            object : AgentRemovalRunner {
                override suspend fun remove(
                    owner: ProfileId,
                    agent: AgentId,
                    receiptId: String,
                ): Outcome<RemovalRun> = native {
                    val descriptor = catalog.byId(agent) ?: error("unavailable")
                    val removed =
                        linux.run(
                            AgentRemovalScripts.remove(descriptor, receiptId),
                            120,
                            agentUser = true,
                        )
                    // The root reader emits bounded numeric data only and deletes the nonce receipt
                    // exactly once.
                    val receipt =
                        if (removed.exitCode == 0) {
                            val read = linux.run(AgentRemovalScripts.readReceipt(receiptId), 30)
                            if (read.exitCode == 0) receipt(read.output) else null
                        } else null
                    RemovalRun(removed.exitCode, receipt)
                }
            },
            SecureBytes { count -> ByteArray(count).also(SecureRandom()::nextBytes) },
        )

    /**
     * "Check phone agents" (AGENT-1) re-reads the payloads; each installed agent's read-only sign-in probe runs inside
     * the read ([signInPhase], keyed by the runtime owner). The read gives way to a person's step ([PhoneReadLane]):
     * it stops between or inside its checks and the step's own re-read replaces it.
     */
    override suspend fun refresh(): Outcome<Unit> = lane.read { stop -> refreshLocked(stop) } ?: Outcome.Ok(Unit)

    /** How many states [publish] has set: [recall] publishes only over the catalog's first rows. */
    private var published = 0L

    /**
     * The rows the registry ([PhoneAgentFacts]) holds, published at once when nothing was read yet in this run: no
     * check starts and no lane is taken (the payload folders and the helper's status record are only looked at). Every
     * reader of [state] (New conversation, the home, the widget, the Agents page) then has what is known from its first
     * frame after a cold start, an app update or a phone restart, instead of the catalog's "Checking" rows (which
     * New conversation cannot offer) until the first read publishes.
     */
    override suspend fun recall() {
        if (needsRestart || synchronized(admission) { published } > 0) return
        if (stores.warm() is Outcome.Failed) return
        val owner = ownerId()
        val authOwner = agentOwner(owner) ?: return
        val before = synchronized(admission) { published }
        if (before > 0) return
        val known = native { snapshot(owner, authOwner, null, false, phoneCatalog(), final = false) }
        if (known !is Outcome.Ok) return
        synchronized(admission) {
            // A read that published meanwhile knows more: its state stands.
            if (published != before || (owner != null && owner in denied) || authOwner in denied) return
            published++
            mutable.value = withUpdateNote(known.value)
        }
    }

    /** A step's re-read at its end, under the lane the step holds; it gives way to the next step as well. */
    private suspend fun rereadLocked(): Outcome<Unit> =
        lane.readHeld { stop -> refreshLocked(stop) } ?: Outcome.Ok(Unit)

    private fun phoneCatalog(): List<AgentDescriptor> =
        catalog.agents.filter {
            it.runsOnPhoneHost && it.recipe != null && it.availability != AgentAvailability.Hidden
        }

    private suspend fun refreshLocked(stop: ProbeStop): Outcome<Unit> {
        if (needsRestart) {
            mutable.value = PhoneAgentsState(needsRestart = true)
            return Outcome.Ok(Unit)
        }
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val owner = ownerId()
        val authOwner = agentOwner(owner)
        val progress = owner?.let { id ->
            when (val restored = installation(id).restore(id)) {
                is Outcome.Ok -> restored.value
                is Outcome.Failed -> return restored
            }
        }
        val shown = progress?.let { data ->
            val id = data.agentId ?: return@let null
            val phase =
                when (data.phase) {
                    AgentSetupPhase.Idle -> null
                    AgentSetupPhase.Installing -> PhoneAgentInstallPhase.Installing
                    AgentSetupPhase.Done -> PhoneAgentInstallPhase.Done
                    AgentSetupPhase.Failed -> PhoneAgentInstallPhase.Failed
                    AgentSetupPhase.Interrupted -> PhoneAgentInstallPhase.Interrupted
                }
            val ended =
                phase == PhoneAgentInstallPhase.Failed || phase == PhoneAgentInstallPhase.Interrupted
            phase?.let {
                val failureText = if (ended) setupJobs.lastFailureText() else null
                PhoneAgentInstallation(
                    id.raw,
                    it,
                    data.fraction,
                    failure = if (ended) installFailure(data.failure) else null,
                    details =
                        if (ended)
                            redactor.details(
                                "install ${id.raw}: ${data.failure ?: "unknown"}" +
                                    (failureText?.let { text -> "; $text" } ?: "")
                            )
                        else null,
                    // Both versions of another version, the pinned one of a withdrawn download: from the step's line.
                    versions =
                        if (data.failure == AgentSetupFailure.Version || data.failure == AgentSetupFailure.Withdrawn)
                            AgentVersionCheck.reported(failureText)?.let { PhoneAgentVersions(it.found, it.expected) }
                        else null,
                    step = phoneInstallStep(data.componentId, id.raw),
                    doneBytes = data.downloadedBytes,
                    totalBytes = data.totalBytes,
                )
            }
        }
        val installing = shown?.phase == PhoneAgentInstallPhase.Installing
        val agents = phoneCatalog()
        // Every check starts a process in the phone's Linux: none while an install runs (it is what they could get
        // killed), and one at a time, each ended at once when a person's step asks for the lane.
        if (authOwner != null && !installing) {
            // What is known before any check starts (the last answers, kept across app starts): shown at once.
            when (val now = native { snapshot(owner, authOwner, shown, false, agents, final = false) }) {
                is Outcome.Failed -> return now
                is Outcome.Ok -> publish(owner, now.value, final = false)
            }
            for (agent in agents) {
                if (stop.isStopped) return Outcome.Ok(Unit)
                when (val checked = native { check(authOwner, agent, stop) }) {
                    is Outcome.Failed -> return checked
                    is Outcome.Ok -> if (!checked.value) continue
                }
                // A check cut short is not news: its answer was dropped and the step's re-read checks again.
                if (stop.isStopped) return Outcome.Ok(Unit)
                when (val now = native { snapshot(owner, authOwner, shown, false, agents, final = false) }) {
                    is Outcome.Failed -> return now
                    is Outcome.Ok -> publish(owner, now.value, final = false)
                }
            }
        }
        if (stop.isStopped) return Outcome.Ok(Unit)
        return when (val rows = native { snapshot(owner, authOwner, shown, installing, agents, final = true) }) {
            is Outcome.Ok -> {
                publish(owner, rows.value, final = true)
                if (authOwner != null && !installing) remember(authOwner, agents, rows.value.agents)
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> rows
        }
    }

    private suspend fun publish(owner: ProfileId?, state: PhoneAgentsState, final: Boolean) {
        var shownState = state
        if (owner != null && PhoneAgentRules.staleResult(shownState.installation, shownState.agents)) {
            // The agent is there: the earlier cut-short install is forgotten, silently.
            if (final) installation(owner).dismissResult(owner)
            shownState = shownState.copy(installation = null)
        }
        synchronized(admission) {
            published++
            mutable.value =
                if (owner != null && owner in denied) PhoneAgentsState(needsRestart = needsRestart)
                else withUpdateNote(shownState)
        }
    }

    /**
     * One agent's checks, one process at a time: its version, its sign-in and (Claude Code) whether the previous
     * version is kept. False when nothing had to run. [stop] ends the running one and keeps the rest from starting.
     */
    private fun check(owner: ProfileId, agent: AgentDescriptor, stop: ProbeStop): Boolean {
        if (!linux.installed) return false
        val payload = PhoneAgentPayloads.present(linux, agent.recipe!!.executable)
        val version = versionOf(owner, agent, payload, installing = false, stop = stop)
        if (stop.isStopped || PhoneAgentRules.present(payload, version) != true) return true
        signInPhase(owner, agent, installing = false, stop = stop)
        if (agent.id == AgentId.CLAUDE && !stop.isStopped)
            previousOf(owner, agent, PhoneAgentRules.foundVersion(version), stop)
        return true
    }

    /**
     * The page's state from what is known now; no process starts here. [final] is the read's own answer, built from
     * this read's checks as before. Otherwise (shown while the checks run) the last answers stand in, also those kept
     * across app starts ([PhoneAgentFacts]), and an agent with no known answer keeps the row it shows.
     */
    private fun snapshot(
        owner: ProfileId?,
        authOwner: ProfileId?,
        shown: PhoneAgentInstallation?,
        installing: Boolean,
        agents: List<AgentDescriptor>,
        final: Boolean,
    ): PhoneAgentsState {
        val installed = linux.installed
        var helperRunning: Boolean? = null
        val helper =
            if (authOwner != null && synchronized(admission) { authOwner !in denied }) {
                val record = linux.agentHost.status(authOwner.raw)
                helperRunning = record["running"] as? Boolean
                AgentHelperStatus.fromRecord(
                    JsonObject(
                        record.mapValues { (_, value) ->
                            when (value) {
                                null -> JsonNull
                                is Boolean -> JsonPrimitive(value)
                                is Number -> JsonPrimitive(value)
                                else -> JsonPrimitive(value as? String ?: "")
                            }
                        }
                    )
                )
            } else null
        val showing = mutable.value.agents.associateBy { it.id }
        // The registry: its proof counts in every read; its answers stand in while checks run, and in a read under an
        // install, which runs no check at all (before, every row then lost its answers and read "Checking").
        val record = if (authOwner == null) emptyMap() else keptFacts(authOwner)
        val kept = if (final && !installing) emptyMap() else record
        return PhoneAgentsState(
            available = installed,
            owner = owner,
            // The helper's own profile (AppLocalAgentHostStarter: "<agent owner>-agents").
            conversations = authOwner?.let { ProfileId("${it.raw}-agents") },
            device = device(),
            installation = shown,
            helperStoppedUnexpectedly = HelperStopNews.stopped(helper, stopNewsSince),
            helperMayHaveBeenStoppedByAndroid = HelperStopNews.byAndroid(helper, stopNewsSince),
            agents =
                agents
                    .mapNotNull { agent ->
                        val payload =
                            if (installed) PhoneAgentPayloads.present(linux, agent.recipe!!.executable)
                            else false
                        val needsVersion =
                            installed &&
                                authOwner != null &&
                                PhoneAgentRules.needsVersionProbe(agent.id, payload)
                        val version =
                            if (needsVersion)
                                knownVersion(authOwner!!, agent)
                                    ?: PhoneAgentFacts.version(kept[agent.id.raw])
                            else null
                        val present = PhoneAgentRules.present(payload, version)
                        val signIn =
                            if (present == true)
                                knownSignIn(authOwner, agent, kept[agent.id.raw], record[agent.id.raw])
                            else null
                        if (!final && ((needsVersion && version == null) || (present == true && signIn == null)))
                            return@mapNotNull showing[agent.id.raw]
                                ?: checkingRow(agent).takeIf { agent.listedOnPhone(installed = false) }
                        // A retired agent (Gemini CLI) keeps its row, check and Remove only while a copy is here.
                        if (!agent.listedOnPhone(installed = present == true || payload == true)) return@mapNotNull null
                        // Never seen set up (the registry's own fact): its set-up comes before its first conversation.
                        val setUpFirst = PhoneAgentRules.setUpFirst(agent, seen = record[agent.id.raw]?.setUp == true)
                        val status =
                            when (present) {
                                null -> PhoneAgentStatus.Unavailable
                                else ->
                                    architecture()?.let { arch ->
                                        PhoneAgentRules.status(
                                            agent,
                                            arch,
                                            installed = present == true,
                                            payloadPresent = payload == true,
                                            qualified =
                                                authOwner != null &&
                                                    PhoneAgentRules.qualified(
                                                        agent,
                                                        arch,
                                                        PhoneGate.read(
                                                            KvAgentPrefs(
                                                                stores.prefs,
                                                                stores.lanes.forProfile(authOwner),
                                                            ),
                                                            authOwner,
                                                        )[agent.id.raw],
                                                        PhoneAgentRules.foundVersion(version),
                                                        record = record[agent.id.raw],
                                                    ),
                                            signIn = signIn,
                                            helperRunning = helperRunning,
                                            setUpFirst = setUpFirst,
                                        )
                                    } ?: PhoneAgentStatus.Unavailable
                            }
                        PhoneAgentRow(
                            agent.id.raw,
                            agent.name,
                            status,
                            signOutAvailable =
                                owner != null &&
                                    signIn == AgentSignInPhase.SignedIn &&
                                    AgentAuthScripts.supportsSignOut(agent.id) &&
                                    !installing,
                            // An agent whose sign-in no check can read is set up at first use now
                            // (PhoneAgentStatus.SetUpAtFirstUse): the "can't check its sign-in yet" line is never shown.
                            signInCheckFailed =
                                status == PhoneAgentStatus.SignInNeeded &&
                                    PhoneAgentRules.signInCheckFailed(agent, signIn),
                            previousVersionAvailable =
                                agent.id == AgentId.CLAUDE &&
                                    present == true &&
                                    shown?.phase != PhoneAgentInstallPhase.Installing &&
                                    authOwner != null &&
                                    knownPrevious(authOwner, agent, PhoneAgentRules.foundVersion(version)),
                            installedVersion = PhoneAgentRules.foundVersion(version),
                            limitResets =
                                if (status == PhoneAgentStatus.Limit)
                                    record[agent.id.raw]?.limitResetAt?.let(::shortTime)
                                else null,
                            signInEvidence =
                                if (
                                    authOwner != null &&
                                        (status == PhoneAgentStatus.SignInNeeded || status == PhoneAgentStatus.Limit)
                                )
                                    evidenceOf(authOwner, agent, record[agent.id.raw])
                                else null,
                            needsSetUp = setUpFirst && status == PhoneAgentStatus.SetUpAtFirstUse,
                            retired = agent.retired,
                            removable =
                                removal.removable(agent.id) &&
                                    present == true &&
                                    shown?.phase != PhoneAgentInstallPhase.Installing,
                            downloadBytes = architecture()?.let(agent::artifactFor)?.downloadBytes,
                            supportedInstallation = architecture()?.let(agent::installableOn) == true,
                            signInAvailable =
                                owner != null &&
                                    present == true &&
                                    agent.signInMethod != AgentSignInMethod.None &&
                                    shown?.phase != PhoneAgentInstallPhase.Installing,
                            installable =
                                owner != null &&
                                    present == false &&
                                    architecture()?.let(agent::installableOn) == true &&
                                    shown?.phase != PhoneAgentInstallPhase.Installing,
                        )
                    }
                    .toImmutableList(),
        )
    }

    /** The row an agent shows while nothing is known about it (the catalog's own rows say "checking" too). */
    private fun checkingRow(agent: AgentDescriptor) =
        PhoneAgentRow(
            id = agent.id.raw,
            name = agent.name,
            retired = agent.retired,
            status = PhoneAgentStatus.Checking,
            downloadBytes = architecture()?.let(agent::artifactFor)?.downloadBytes,
            supportedInstallation = architecture()?.let(agent::installableOn) == true,
        )

    private fun knownVersion(owner: ProfileId, agent: AgentDescriptor): Map<String, Any?>? =
        synchronized(admission) { versions[owner to agent.id]?.second }

    /**
     * The sign-in a tile shows without checking: the agent's last answer, or (shown while checking) the kept one, from
     * the newest evidence ([PhoneAgentFacts.lapseStands]): a lapse a run reported ([noteSignInLapsed], kept in
     * [record]) reads Sign in needed only while no sign-in check that answered was asked after it.
     */
    private fun knownSignIn(
        owner: ProfileId?,
        agent: AgentDescriptor,
        kept: PhoneAgentFacts.Fact?,
        record: PhoneAgentFacts.Fact? = null,
    ): AgentSignInPhase? {
        if (agent.signInMethod == AgentSignInMethod.None) return AgentSignInPhase.SignedIn
        if (PhoneAgentFacts.lapseStands(record, owner?.let { checkedAt(it, agent.id, record) }))
            return AgentSignInPhase.SignedOut
        val read = readSignIn(owner, agent, kept)
        // signin-truth: a reply that was the plan's usage limit is the agent working, signed in: "Plan limit reached"
        // until the reset it named, unless a sign-in check says it is signed out.
        return if (
            read != AgentSignInPhase.SignedOut && PhoneAgentFacts.limitStands(record, System.currentTimeMillis())
        )
            AgentSignInPhase.LimitReached
        else read
    }

    private fun readSignIn(owner: ProfileId?, agent: AgentDescriptor, kept: PhoneAgentFacts.Fact?): AgentSignInPhase? {
        // No script, or one that refuses this agent before it starts (Gemini CLI, Qwen Code, Goose, omp): the same
        // answer every time, so the row says it at once and never shows "Checking sign-in" first.
        if (
            owner == null ||
                AgentAuthScripts.script(agent, AuthScriptAction.Probe) == null ||
                !AgentAuthScripts.checksSignIn(agent.id)
        )
            return AgentSignInPhase.Failed
        return synchronized(admission) {
            if (owner in denied) return AgentSignInPhase.Failed
            accounts[owner to agent.id]
        }
            ?.let(::phaseOf) ?: PhoneAgentFacts.signIn(kept)?.let(::phaseOf)
    }

    /**
     * When (wall clock) the newest sign-in check of [agent] that answered was asked: this run's own answer, else the
     * registry's ([record]). A check that could not answer (timed out, no answer) is no evidence and has no time here.
     */
    private fun checkedAt(owner: ProfileId, agent: AgentId, record: PhoneAgentFacts.Fact?): Long? {
        val key = owner to agent
        val live =
            synchronized(admission) {
                accounts[key]?.takeIf { it.state != AuthState.Error }?.let { askedAt[key] ?: Long.MIN_VALUE }
            }
        if (live != null && live != Long.MIN_VALUE) return wallOf(live)
        return record?.signedInAt?.takeIf { record.signedIn != null }
    }

    /**
     * signin-truth: where [agent]'s sign-in state comes from, for its sheet's Details: the sign-in check's answer and
     * when it was asked, a reported lapse (reply or refused start) and when, a reported plan limit and its reset, a
     * completed sign-in. Technical words only; never the agent's own text, never an account.
     */
    private fun evidenceOf(owner: ProfileId, agent: AgentDescriptor, record: PhoneAgentFacts.Fact?): Redacted {
        val key = owner to agent.id
        val (live, asked) = synchronized(admission) { accounts[key] to askedAt[key] }
        val noAnswer = synchronized(admission) { lastNoAnswer[key] }
        val lines = buildList {
            when (noAnswer) {
                null -> Unit
                AuthProbeError.AgentBusy -> add("newest check: not run, Claude Code was working in a conversation")
                else -> add("newest check: no answer (${noAnswer.name})")
            }
            when {
                live != null && asked == null && record?.signedIn != null ->
                    add(
                        "sign-in check (kept): ${if (record.signedIn) "signed in" else "signed out"}" +
                            (record.signedInAt?.let { ", asked ${stamp(it)}" } ?: "")
                    )
                live != null ->
                    add(
                        "sign-in check: " +
                            when (live.state) {
                                AuthState.SignedIn -> "signed in"
                                AuthState.SignedOut -> "signed out"
                                AuthState.Error -> "no answer (${live.error?.name ?: "error"})"
                            } + (asked?.let { ", asked ${stamp(wallOf(it))}" } ?: "")
                    )
                record?.signedIn != null ->
                    add(
                        "sign-in check (kept): ${if (record.signedIn) "signed in" else "signed out"}" +
                            (record.signedInAt?.let { ", asked ${stamp(it)}" } ?: "")
                    )
                else -> add("sign-in check: no answer yet")
            }
            record?.lapsedAt?.let { at ->
                val from =
                    when (record.lapseSource) {
                        PhoneAgentFacts.LapseSource.Reply -> "a reply ended on the sign-in"
                        PhoneAgentFacts.LapseSource.Start -> "the helper refused a start for the sign-in"
                        null -> "a run reported the sign-in ended"
                    }
                val standing =
                    if (PhoneAgentFacts.lapseStands(record, checkedAt(owner, agent.id, record))) "newest evidence"
                    else "older than the check"
                add("reported: $from, ${stamp(at)} ($standing)")
            }
            record?.limitAt?.let { at ->
                add(
                    "plan limit: a reply said so, ${stamp(at)}" +
                        (record.limitResetAt?.let { ", resets ${stamp(it)}" } ?: ", no reset named")
                )
            }
            record?.signInDoneAt?.let { add("sign-in completed in the app: ${stamp(it)}") }
        }
        return redactor.details(lines.joinToString("\n"))
    }

    private fun stamp(wallMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm zzz", java.util.Locale.ROOT).format(java.util.Date(wallMs))

    /** A reset time as this phone words a short date and time. */
    private fun shortTime(wallMs: Long): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(wallMs))

    /** [elapsed] (`SystemClock.elapsedRealtime`) as wall-clock millis. */
    private fun wallOf(elapsed: Long): Long = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsed)

    /**
     * Whether Claude Code's previous version is kept, for the copy found now. Its check hashes the kept program and
     * runs it, so it runs once per version found (it used to run on every read), and only an install, a removal or
     * going back to the previous version asks again. Like the read's other checks, [stop] ends it at once.
     */
    private fun previousOf(owner: ProfileId, agent: AgentDescriptor, found: String?, stop: ProbeStop) {
        val key = owner to agent.id
        val cached = synchronized(admission) { rollbacks[key] }
        if (cached != null && cached.first == found) return
        // A check that did not finish (phone busy, time limit, a person's step) is asked again by the next read.
        val available = previousCheck(agent, stop) ?: return
        if (stop.isStopped) return
        synchronized(admission) { rollbacks[key] = found to available }
    }

    private fun knownPrevious(owner: ProfileId, agent: AgentDescriptor, found: String?): Boolean =
        synchronized(admission) { rollbacks[owner to agent.id] }?.let { it.first == found && it.second } == true

    /** The registry as kept, with the set-up list of earlier builds read in ([PhoneAgentFacts.withEarlierSetUp]). */
    private fun keptFacts(owner: ProfileId): Map<String, PhoneAgentFacts.Fact> {
        val prefs = gatePrefs(owner)
        return PhoneAgentFacts.withEarlierSetUp(
            PhoneAgentFacts.decode(prefs.string(PhoneAgentFacts.key(owner.raw))),
            prefs.stringList(AgentKeys.agentSetUp(owner)),
        )
    }

    /**
     * This read's answers, kept for the next app start ([PhoneAgentFacts]); written only when they changed. A row past
     * the phone check ([rows], [PhoneAgentRules.pastCheck]) proves the version it runs, under this helper pin and
     * build; a version the registry already proves keeps the proof it has (a row qualified by that proof is no new
     * check).
     */
    private suspend fun remember(owner: ProfileId, agents: List<AgentDescriptor>, rows: List<PhoneAgentRow>) {
        val shown = rows.associateBy { it.id }
        val record = keptFacts(owner)
        val wallNow = System.currentTimeMillis()
        val bootNow = SystemClock.elapsedRealtime()
        val facts =
            synchronized(admission) {
                if (owner in denied) return
                agents.associate { agent ->
                    val key = owner to agent.id
                    val fact =
                        PhoneAgentFacts.of(
                            versions[key]?.second,
                            accounts[key],
                            helperPin,
                            appBuild,
                            // When the check was asked, not when it answered: a failure reported meanwhile is newer.
                            signedInAt = askedAt[key]?.let { wallNow - (bootNow - it) },
                        )
                    val row = shown[agent.id.raw]
                    val runs = row?.installedVersion
                    agent.id.raw to
                        if (
                            row != null &&
                                runs != null &&
                                PhoneAgentRules.pastCheck(row.status) &&
                                fact.proven == null &&
                                record[agent.id.raw]?.proven != runs
                        )
                            PhoneAgentFacts.proven(fact, runs, helperPin, appBuild)
                        else fact
                }
            }
        keepFacts(owner) { PhoneAgentFacts.merge(it, facts) }
    }

    /**
     * The one path that writes the registry ([PhoneAgentFacts]): every read, step and check that changes what is known
     * about [owner]'s agents goes through here, so each change reaches every reader of [state]. Written only when it
     * changed; answers the record as it now stands.
     */
    private suspend fun keepFacts(
        owner: ProfileId,
        change: (Map<String, PhoneAgentFacts.Fact>) -> Map<String, PhoneAgentFacts.Fact>,
    ): Map<String, PhoneAgentFacts.Fact> =
        // A conversation's first answer writes here from outside the read lane ([noteSetUp]): one change at a time.
        factsWrite.withLock {
            val kept = keptFacts(owner)
            val next = change(kept)
            if (next != kept)
                gatePrefs(owner).commit {
                    putString(PhoneAgentFacts.key(owner.raw), PhoneAgentFacts.encode(next))
                    // The earlier builds' own set-up list is in the record now ([keptFacts]).
                    remove(AgentKeys.agentSetUp(owner))
                }
            next
        }

    private val factsWrite = Mutex()

    /**
     * The app has seen [agent] set up: its set-up terminal ended and the person came back (`NativePhoneSignIn`), or a
     * conversation with it got an answer (the chat, through `AndroidChatOutside`). Kept in the registry
     * ([PhoneAgentFacts.Fact.setUp]) for [phone], this phone's own OpenCode; from then on the agent is a normal choice
     * for conversations ([PhoneAgentRules.setUpFirst]). Only agents whose sign-in no check can read are marked; nothing
     * about the account is read or kept.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun noteSetUp(phone: ProfileId, agent: AgentId) {
        val descriptor = catalog.byId(agent)?.takeIf(AgentFirstUse::applies) ?: return
        if (needsRestart || ownerId() != phone) return
        val owner = agentOwner(phone) ?: return
        if (synchronized(admission) { owner in denied || phone in denied }) return
        try {
            keepFacts(owner) { PhoneAgentFacts.seenSetUp(it, descriptor.id.raw) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not kept this time: the next answer or set-up says it again.
            return
        }
        // Every reader of [state] has it at once; the next read builds the row from the record as well.
        synchronized(admission) {
            if (owner in denied) return
            mutable.update { now ->
                now.copy(
                    agents =
                        now.agents
                            .map { if (it.id == descriptor.id.raw) it.copy(needsSetUp = false) else it }
                            .toImmutableList()
                )
            }
        }
    }

    /**
     * Issue 147: a run of [phone]'s agent behind [provider] reported at [atMs] (wall clock) that its sign-in no longer
     * works: a create the helper refused for its sign-in, or a turn that ended on it ([source]). Kept in the registry
     * ([PhoneAgentFacts.Fact.lapsedAt]) while it is the newest evidence: a sign-in check asked after it, or a completed
     * sign-in ([noteSignedIn]), decides instead. Every reader of [state] reads the agent as Sign in needed at once. A
     * report older than the last answered check or completed sign-in changes nothing.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun noteSignInLapsed(
        phone: ProfileId,
        provider: String,
        atMs: Long,
        source: PhoneAgentFacts.LapseSource = PhoneAgentFacts.LapseSource.Reply,
    ) {
        val descriptor =
            catalog.agents.firstOrNull { it.providerId == provider && it.signInMethod != AgentSignInMethod.None }
                ?: return
        if (needsRestart || ownerId() != phone) return
        val owner = agentOwner(phone) ?: return
        if (synchronized(admission) { owner in denied || phone in denied }) return
        // A sign-in check this run asked after the reported failure is newer evidence: the report is old news.
        val asked =
            synchronized(admission) {
                val key = owner to descriptor.id
                accounts[key]?.takeIf { it.state != AuthState.Error }?.let { askedAt[key] }
            }
        if (asked != null && wallOf(asked) >= atMs) return
        val now =
            try {
                keepFacts(owner) { PhoneAgentFacts.lapsed(it, descriptor.id.raw, atMs, source) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        if (now[descriptor.id.raw]?.lapsed != true) return
        synchronized(admission) {
            if (owner in denied) return
            // The kept "signed in" answer is older than the report: the next read asks the agent again, and only a
            // check asked after the report ends it ([knownSignIn]).
            accounts.remove(owner to descriptor.id)
            askedAt.remove(owner to descriptor.id)
            mutable.update { state ->
                state.copy(
                    agents =
                        state.agents
                            .map {
                                if (it.id == descriptor.id.raw && it.status == PhoneAgentStatus.Ready)
                                    it.copy(status = PhoneAgentStatus.SignInNeeded, signInCheckFailed = false)
                                else it
                            }
                            .toImmutableList()
                )
            }
        }
    }

    /**
     * signin-truth (Galaxy, build 10050): a reply of [phone]'s agent behind [provider] at [atMs] was its plan's usage
     * limit, resetting at [resetAtMs] when it said so. The agent is signed in: every reader of [state] reads "Plan limit
     * reached" (never "Sign in needed") until the reset ([PhoneAgentFacts.limitStands]).
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun noteLimitReached(phone: ProfileId, provider: String, atMs: Long, resetAtMs: Long?) {
        val descriptor =
            catalog.agents.firstOrNull { it.providerId == provider && it.signInMethod != AgentSignInMethod.None }
                ?: return
        if (needsRestart || ownerId() != phone) return
        val owner = agentOwner(phone) ?: return
        if (synchronized(admission) { owner in denied || phone in denied }) return
        val now =
            try {
                keepFacts(owner) { PhoneAgentFacts.limited(it, descriptor.id.raw, atMs, resetAtMs) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        val fact = now[descriptor.id.raw]
        if (!PhoneAgentFacts.limitStands(fact, System.currentTimeMillis())) return
        synchronized(admission) {
            if (owner in denied) return
            mutable.update { state ->
                state.copy(
                    agents =
                        state.agents
                            .map {
                                if (
                                    it.id == descriptor.id.raw &&
                                        (it.status == PhoneAgentStatus.Ready ||
                                            it.status == PhoneAgentStatus.SignInNeeded)
                                )
                                    it.copy(
                                        status = PhoneAgentStatus.Limit,
                                        signInCheckFailed = false,
                                        limitResets = fact?.limitResetAt?.let(::shortTime),
                                    )
                                else it
                            }
                            .toImmutableList()
                )
            }
        }
    }

    /** [noteSignInLapsed] for this phone's own agents (the phone the app runs them for). */
    internal suspend fun noteSignInLapsedHere(provider: String, atMs: Long) {
        val phone = ownerId() ?: return
        noteSignInLapsed(phone, provider, atMs, PhoneAgentFacts.LapseSource.Start)
    }

    /**
     * A sign-in of [phone]'s [agent] completed (its sign-in page's own check read "signed in"): its lapse, if any,
     * ends, and that answer is the agent's newest one at once. B-454: the chat that offered "Sign in again" keeps its
     * Claude Code program loaded, so the reads of this list that follow are held back ("agentBusy") and keep the last
     * answer; that answer is now this one, never an older "signed out", and every reader of [state] reads the agent
     * signed in without another tap.
     */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun noteSignedIn(phone: ProfileId, agent: AgentId) {
        if (needsRestart || ownerId() != phone) return
        val owner = agentOwner(phone) ?: return
        if (synchronized(admission) { owner in denied || phone in denied }) return
        try {
            keepFacts(owner) { PhoneAgentFacts.signedInAgain(it, agent.raw, System.currentTimeMillis()) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not kept this time: the next completed sign-in says it again.
        }
        synchronized(admission) {
            if (owner in denied) return
            val key = owner to agent
            val now = SystemClock.elapsedRealtime()
            // The page's answer carries no account name: a "signed in" already known keeps its own.
            if (accounts[key]?.state != AuthState.SignedIn) accounts[key] = AuthProjection(AuthState.SignedIn)
            answeredAt[key] = now
            askedAt[key] = now
            lastNoAnswer.remove(key)
            // The mirror of noteSignInLapsed: the next read works out the rest of the row from this answer.
            mutable.update { state ->
                state.copy(
                    agents =
                        state.agents
                            .map {
                                if (it.id == agent.raw && it.status == PhoneAgentStatus.SignInNeeded)
                                    it.copy(
                                        status = PhoneAgentStatus.Ready,
                                        signInCheckFailed = false,
                                        signInEvidence = null,
                                    )
                                else it
                            }
                            .toImmutableList()
                )
            }
        }
    }

    /**
     * B-458: the sign-in of [phone]'s agents as [state] reads it now, by provider id: true when the agent reads ready
     * (or at its plan limit, which is signed in), false when it reads Sign in needed. An agent whose sign-in is not
     * known yet (checking, not installed, stopped) is absent. Empty when [state] is not [phone]'s. A chat's stopped
     * turns follow this, so a confirmed sign-in ([noteSignedIn]) clears their "Sign in again" everywhere at once.
     */
    internal fun signIns(phone: ProfileId, state: PhoneAgentsState): Map<String, Boolean> {
        if (state.owner != phone) return emptyMap()
        return state.agents
            .mapNotNull { row ->
                val provider = catalog.byId(AgentId(row.id))?.providerId ?: return@mapNotNull null
                when (row.status) {
                    PhoneAgentStatus.Ready,
                    PhoneAgentStatus.Limit -> provider to true
                    PhoneAgentStatus.SignInNeeded -> provider to false
                    else -> null
                }
            }
            .toMap()
    }

    /** The agents [phone]'s registry has seen set up ([noteSetUp]); empty when none or not this phone. */
    @Suppress("TooGenericExceptionCaught")
    internal fun setUpAgents(phone: ProfileId): Set<String> =
        try {
            agentOwner(phone)?.let { PhoneAgentFacts.setUp(keptFacts(it)) }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }

    /** The preference keys whose change may change [setUpAgents] for [phone]. */
    internal fun setUpKeys(phone: ProfileId): Set<String> =
        agentOwner(phone)?.let { setOf(PhoneAgentFacts.key(it.raw), AgentKeys.agentSetUp(it)) }.orEmpty()

    /** The helper pin and the app build a proof is checked against ([PhoneAgentFacts.provenRuns]). */
    private val helperPin: String
        get() = AgentPins.builtIn.paseo.version

    private val appBuild: Long
        get() = BuildConfig.VERSION_CODE.toLong()

    /**
     * A person's step on [agent] forgets what was known about it, here and across app starts, so neither the next
     * read nor the tile shown while it runs uses an answer the step may have changed. Other agents keep theirs.
     */
    private suspend fun forgetFacts(
        owner: ProfileId,
        agent: AgentId,
        version: Boolean,
        signIn: Boolean,
        proof: Boolean = false,
        setUp: Boolean = false,
    ) {
        val key = owner to agent
        synchronized(admission) {
            if (version) {
                // Kept for the tile while an install runs (no check runs then), never reused as fresh.
                versions[key]?.let { versions[key] = STALE to it.second }
                rollbacks.remove(key)
            }
            if (signIn) {
                accounts.remove(key)
                answeredAt.remove(key)
                askedAt.remove(key)
                lastNoAnswer.remove(key)
            }
        }
        keepFacts(owner) { PhoneAgentFacts.forget(it, agent.raw, version, signIn, proof, setUp) }
    }

    /** Every kept "signed in" or "signed out" of [owner]'s agents, and their set-up (see [PhoneAgentFacts]). */
    private suspend fun forgetSignIns(owner: ProfileId) {
        keepFacts(owner, PhoneAgentFacts::forgetSignIns)
    }

    override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> = lane.step {
        if (needsRestart)
            return@step Outcome.Ok(
                PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
            )
        val owner =
            ownerId()
                ?: return@step Outcome.Ok(
                    PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
                )
        val abi =
            architecture()
                ?: return@step Outcome.Ok(
                    PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
                )
        val answer =
            when (val started = installation(owner).install(owner, AgentId(id), abi)) {
                is Outcome.Failed -> return@step started
                is Outcome.Ok ->
                    when (val value = started.value) {
                        InstallStart.Started -> {
                            agentOwner(owner)?.let {
                                forgetFacts(it, AgentId(id), version = true, signIn = false)
                            }
                            PhoneAgentInstallResult.Started
                        }
                        is InstallStart.Refused ->
                            PhoneAgentInstallResult.Refused(
                                when (value.reason) {
                                    InstallRefusal.SetupBusy,
                                    InstallRefusal.AlreadyInstalling -> PhoneAgentInstallIssue.Busy
                                    InstallRefusal.NotEnoughSpace -> PhoneAgentInstallIssue.Space
                                    InstallRefusal.NotInstallable ->
                                        PhoneAgentInstallIssue.Unavailable
                                }
                            )
                    }
            }
        rereadLocked()
        Outcome.Ok(answer)
    }

    /**
     * What installing [id] needs free before it starts (the agent's own room, which every step of the job checks, plus
     * the shared parts not yet on this phone at the job's per-step floor each) and what the phone has. It reads no
     * Linux and takes no lane (the last read's rows say whether the shared parts are in place), so the review can say
     * it at once.
     */
    internal fun nameOf(agent: AgentId): String? = catalog.byId(agent)?.name

    override suspend fun installSpace(id: String): PhoneAgentSpace? {
        val owner = ownerId() ?: return null
        val abi = architecture() ?: return null
        val needed =
            installation(owner)
                .requiredBytesBeforeStart(AgentId(id), abi, phoneSharedPartsInPlace(state.value.agents))
                ?: return null
        val free = setupJobs.freeBytes() ?: return null
        return PhoneAgentSpace(needed, free)
    }

    override suspend fun cancelInstall(): Outcome<Unit> = lane.step {
        val owner =
            ownerId() ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        when (val cancelled = installation(owner).cancel(owner)) {
            is Outcome.Failed -> cancelled
            is Outcome.Ok -> rereadLocked()
        }
    }

    override suspend fun remove(id: String): Outcome<PhoneAgentRemoval> {
        val mutation =
            when (val claim = payloads.beginMutation(ownerId())) {
                is Outcome.Failed ->
                    return Outcome.Ok(PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Busy))
                is Outcome.Ok -> claim.value
            }
        try {
            return removeLocked(id)
        } finally {
            mutation.release()
        }
    }

    private suspend fun removeLocked(id: String): Outcome<PhoneAgentRemoval> = lane.step {
        val owner =
            try {
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
            } catch (e: Exception) {
                null
            }
        if (owner.isNullOrEmpty())
            return@step Outcome.Ok(
                PhoneAgentRemoval.Refused(PhoneAgentRemovalIssue.Unavailable)
            )
        val busy =
            when (
                val read = native {
                    SetupRunner.get(app).status()?.let { text ->
                        JSONObject(text).optString("state") !in
                            setOf("done", "failed", "interrupted", "cancelled")
                    } ?: false
                }
            ) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> true
            }
        val answer =
            when (val result = removal.remove(ProfileId(owner), AgentId(id), busy)) {
                is Outcome.Failed -> return@step result
                is Outcome.Ok ->
                    when (val value = result.value) {
                        is RemovalResult.Removed -> PhoneAgentRemoval.Removed(value.freedBytes)
                        is RemovalResult.Refused ->
                            PhoneAgentRemoval.Refused(
                                when (value.reason) {
                                    RemovalRefusal.Busy -> PhoneAgentRemovalIssue.Busy
                                    RemovalRefusal.Unsafe -> PhoneAgentRemovalIssue.Unsafe
                                    RemovalRefusal.Unconfirmed -> PhoneAgentRemovalIssue.Unconfirmed
                                    RemovalRefusal.NotRemovable,
                                    RemovalRefusal.Failed -> PhoneAgentRemovalIssue.Unavailable
                                }
                            )
                    }
            }
        synchronized(admission) { versions.clear() }
        agentOwner(ProfileId(owner))?.let {
            val removed = answer is PhoneAgentRemoval.Removed
            // A copy installed again later starts over with its set-up.
            forgetFacts(it, AgentId(id), version = true, signIn = true, proof = removed, setUp = removed)
        }
        rereadLocked()
        if (answer is PhoneAgentRemoval.Removed && phoneInstalledAgents(mutable.value).isEmpty()) {
            payloads.sweepAgentHelperLastKnown(agentOwner(ProfileId(owner)) ?: ProfileId(owner))
        }
        Outcome.Ok(answer)
    }

    private fun previousAvailable(agent: AgentDescriptor): Boolean = previousCheck(agent) == true

    /** Null when the check did not finish (refused, cut at its time limit or by [stop]: `run` answers -1). */
    private fun previousCheck(agent: AgentDescriptor, stop: ProbeStop? = null): Boolean? =
        try {
            linux.run(AgentPhoneScripts.previousVersion(agent), 30, agentUser = true, stop = stop).exitCode
                .takeIf { it != -1 }
                ?.let { it == 0 }
        } catch (_: Exception) {
            null
        }

    override suspend fun usePreviousVersion(id: String): Outcome<Unit> = lane.step {
        if (needsRestart || id != AgentId.CLAUDE.raw)
            return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val owner =
            agentOwner(ownerId())
                ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val agent =
            catalog.byId(AgentId.CLAUDE)
                ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val result = native {
            check(synchronized(admission) { owner !in denied })
            // Both the busy check and the official backup verification are fresh after
            // confirmation.
            linux.agentHost.prepareUpdate(owner.raw)
            check(previousAvailable(agent))
            linux.agentHost.stop(owner.raw, true)
            check(
                linux
                    .run(
                        AgentPhoneScripts.usePreviousVersion(
                            agent,
                            AgentPhoneScripts.previousClaudeVersion,
                        ),
                        90,
                        agentUser = true,
                    )
                    .exitCode == 0
            )
        }
        if (result is Outcome.Failed) return@step result
        synchronized(admission) {
            accounts.remove(owner to agent.id)
            versions.clear()
        }
        forgetFacts(owner, agent.id, version = true, signIn = true)
        rereadLocked()
    }

    /**
     * Runs the agent's own logout, then reads the status again: signed out only when that read
     * says so (the logout's exit code is not trusted). Conversations are not touched.
     */
    override suspend fun signOut(id: String): Outcome<Boolean> = lane.step {
        val agent =
            catalog.byId(AgentId(id))
                ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val owner =
            agentOwner(ownerId())
                ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val script =
            AgentAuthScripts.script(agent, AuthScriptAction.SignOut)
                ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        if (!AgentAuthScripts.supportsSignOut(agent.id) || synchronized(admission) { owner in denied })
            return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val ran = native {
            linux.agentAuthProbe(
                mapOf(
                    "profileId" to owner.raw,
                    "agentId" to agent.id.raw,
                    "action" to "logout",
                    "script" to AgentAuthScripts.forProfile(owner, script),
                    "timeoutSeconds" to 20L,
                )
            )
        }
        if (ran is Outcome.Failed) return@step ran
        forgetFacts(owner, agent.id, version = false, signIn = true, setUp = true)
        val after = native { signInPhase(owner, agent, installing = false) }
        rereadLocked()
        when (after) {
            is Outcome.Failed -> after
            is Outcome.Ok -> Outcome.Ok(after.value == AgentSignInPhase.SignedOut)
        }
    }

    /** Reset closes clients first; only the engine's allowlisted secure slots are erased. */
    override suspend fun resetSavedSignIns(): Outcome<Unit> = lane.step {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return@step warm
            is Outcome.Ok -> Unit
        }
        val owners = buildSet {
            ownerId()?.let {
                add(it)
                agentOwner(it)?.let(::add)
            }
            stores.prefs.keys("oc.phoneAgentOwner.").forEach { key ->
                (stores.prefs.get(key) as? PrefValue.Str)
                    ?.value
                    ?.takeIf { Regex("[A-Za-z0-9_-]{1,80}").matches(it) }
                    ?.let { add(ProfileId(it)) }
            }
        }
        needsRestart = true
        synchronized(admission) {
            denied.addAll(owners)
            accounts.clear()
            answeredAt.clear()
            askedAt.clear()
            lastNoAnswer.clear()
        }
        // Sign-ins are being reset: no tile may show a kept "signed in" after the restart.
        owners.forEach { forgetSignIns(it) }
        mutable.value = PhoneAgentsState(needsRestart = true)
        registry.live.value.keys.toList().forEach { registry.close(it) }
        val participant =
            object : SignInResetParticipant {
                override val owner: String = ":engine:agents"

                override suspend fun reset(found: Set<String>): Outcome<Unit> {
                    val allOwners =
                        owners +
                            found.mapNotNull { key ->
                                key.takeIf { it.startsWith("oc.agentHostSecret.") }
                                    ?.removePrefix("oc.agentHostSecret.")
                                    ?.takeIf { Regex("[A-Za-z0-9_-]{1,80}").matches(it) }
                                    ?.let(::ProfileId)
                            }
                    for (id in allOwners) {
                        setupJobs.stopAdmission(id)
                        when (val cancelled = setupJobs.drain(id)) {
                            is Outcome.Failed -> return cancelled
                            is Outcome.Ok -> Unit
                        }
                        when (
                            val closed = native {
                                linux.blockAgentProfile(id.raw)
                                linux.agentSignIn.deleteProfile(id.raw)
                                linux.agentHost.stop(id.raw, false)
                            }
                        ) {
                            is Outcome.Failed -> return closed
                            is Outcome.Ok -> Unit
                        }
                    }
                    return Outcome.Ok(Unit)
                }
            }
        SignInReset(
                stores.profiles,
                stores.prefs,
                stores.secrets,
                stores.lanes,
                redactor,
                setOf(participant),
            )
            .reset()
    }

    /**
     * Private status reads are keyed by the runtime owner, never by the selected remote profile. A read passes its
     * [stop]: it may then reuse a recent "signed in" ([PhoneAgentRules.reuseSignIn]), and a probe it cut short leaves
     * the last answer as it was.
     */
    private fun signInPhase(
        owner: ProfileId?,
        agent: AgentDescriptor,
        installing: Boolean,
        stop: ProbeStop? = null,
    ): AgentSignInPhase? {
        // Nothing to sign in to.
        if (agent.signInMethod == AgentSignInMethod.None) return AgentSignInPhase.SignedIn
        if (owner == null) return AgentSignInPhase.Failed
        val authored =
            AgentAuthScripts.script(agent, AuthScriptAction.Probe)
                ?: return AgentSignInPhase.Failed
        // The script refuses this agent before it starts anything: no process for an answer known in advance.
        if (!AgentAuthScripts.checksSignIn(agent.id)) return AgentSignInPhase.Failed
        val key = owner to agent.id
        if (synchronized(admission) { owner in denied }) return AgentSignInPhase.Failed
        // A probe starts a process, and Android stops an app's child processes past a limit: none while an install
        // runs. The last answer stands in, or none (the row says it is checking).
        if (!PhoneAgentRules.mayProbeSignIn(installing))
            return synchronized(admission) { accounts[key] }?.let(::phaseOf)
        if (stop != null) {
            val now = android.os.SystemClock.elapsedRealtime()
            synchronized(admission) {
                val last = accounts[key]
                if (last != null && PhoneAgentRules.reuseSignIn(last, answeredAt[key]?.let { now - it }))
                    return phaseOf(last)
            }
        }
        val asked = android.os.SystemClock.elapsedRealtime()
        val response =
            linux.agentAuthProbe(
                mapOf(
                    "profileId" to owner.raw,
                    "agentId" to agent.id.raw,
                    "action" to "probe",
                    "script" to AgentAuthScripts.forProfile(owner, authored),
                    "timeoutSeconds" to if (agent.id == AgentId.CLAUDE) 65L else 10L,
                ),
                stop,
            )
        // Ended by a person's step: not news about the account.
        if (stop?.isStopped == true) return synchronized(admission) { accounts[key] }?.let(::phaseOf)
        val probed =
            AuthProjectionJson.decode(
                JsonObject(
                    response.mapValues { (_, value) ->
                        if (value == null) JsonNull else JsonPrimitive(value as? String ?: "")
                    }
                )
            )
        val fresh =
            if (agent.id == AgentId.CLAUDE && probed.error == AuthProbeError.ProbeUnsupported) {
                // A legacy host can still answer Claude's direct native status without starting a
                // helper.
                when (
                    linux.agentSignIn
                        .status(
                            owner.raw,
                            agent.id.raw,
                            "native-status-${owner.raw}",
                            "browserOAuthHost",
                        )["phase"]
                ) {
                    "signedIn",
                    "limitReached" -> AuthProjection(AuthState.SignedIn)
                    "signedOut" -> AuthProjection(AuthState.SignedOut)
                    else -> AuthProjectionJson.failed(AuthProbeError.HostUnavailable)
                }
            } else probed
        // signin-truth-2: a check that gave no answer is no evidence: the last answer stands, this run's or else the
        // registry's (after an app start), with its own time; only with neither is the sign-in "not confirmed".
        val kept =
            if (fresh.error in PhoneAgentRules.NO_ANSWER) PhoneAgentFacts.signIn(keptFacts(owner)[agent.id.raw])
            else null
        val answer =
            synchronized(admission) {
                // Deletion can revoke while the native reader drains. A late read never repopulates
                // it.
                if (owner in denied) return AgentSignInPhase.Failed
                val previous = accounts[key]
                lastNoAnswer[key] = fresh.error?.takeIf { it in PhoneAgentRules.NO_ANSWER }
                val standIn = PhoneAgentRules.standIn(fresh, previous, kept)
                when {
                    standIn == null ->
                        fresh.also {
                            accounts[key] = it
                            answeredAt[key] = android.os.SystemClock.elapsedRealtime()
                            askedAt[key] = asked
                        }
                    standIn === previous -> standIn
                    // The registry's answer: no ask time of this run (its own time is in the registry).
                    else ->
                        standIn.also {
                            accounts[key] = it
                            answeredAt.remove(key)
                            askedAt.remove(key)
                        }
                }
            }
        return phaseOf(answer)
    }

    private fun phaseOf(answer: AuthProjection): AgentSignInPhase = PhoneAgentRules.phaseOf(answer)

    private val versions = HashMap<Pair<ProfileId, AgentId>, Pair<Long, Map<String, Any?>>>()

    /** When the agent itself last answered its sign-in check ([accounts]). */
    private val answeredAt = HashMap<Pair<ProfileId, AgentId>, Long>()

    /** When that answered check was asked: the time its evidence is from ([PhoneAgentFacts.lapseStands]). */
    private val askedAt = HashMap<Pair<ProfileId, AgentId>, Long>()

    /** Why the newest check gave no answer (timed out, no answer, Claude Code busy), for Details; null when it did. */
    private val lastNoAnswer = HashMap<Pair<ProfileId, AgentId>, AuthProbeError?>()

    /** Claude Code's previous version: kept or not, for the version found then ([previousOf]). */
    private val rollbacks = HashMap<Pair<ProfileId, AgentId>, Pair<String?, Boolean>>()

    /**
     * What `agentHostVersion` says about the agent (Flutter read it for every row). Only Claude Code, which the
     * payload inventory has no folder for, and agents that have a payload are asked: the rest cannot be installed.
     * A version read starts a process, so none runs while an install does, and an answer is reused for a minute. A
     * read cut short by a person's step ([stop]) keeps the last answer.
     */
    private fun versionOf(
        owner: ProfileId,
        agent: AgentDescriptor,
        payload: Boolean?,
        installing: Boolean,
        stop: ProbeStop? = null,
    ): Map<String, Any?>? {
        if (!PhoneAgentRules.needsVersionProbe(agent.id, payload)) return null
        val key = owner to agent.id
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = synchronized(admission) { versions[key] }
        if (cached != null && (installing || (cached.first != STALE && now - cached.first < VERSION_REUSE_MS)))
            return cached.second
        if (installing) return null
        val recipe = agent.recipe ?: return null
        val read =
            try {
                linux.agentHost.checks.version(owner.raw, recipe.executable, recipe.version, stop)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        if (stop?.isStopped == true) return cached?.second
        synchronized(admission) {
            if (read != null) versions[key] = now to read else versions.remove(key)
        }
        return read
    }

    private fun installFailure(failure: AgentSetupFailure?): PhoneAgentInstallFailure =
        when (failure) {
            AgentSetupFailure.Download -> PhoneAgentInstallFailure.Download
            AgentSetupFailure.Checksum -> PhoneAgentInstallFailure.Checksum
            AgentSetupFailure.Storage -> PhoneAgentInstallFailure.Storage
            AgentSetupFailure.Interrupted -> PhoneAgentInstallFailure.Interrupted
            AgentSetupFailure.Version -> PhoneAgentInstallFailure.Version
            AgentSetupFailure.NotStarted -> PhoneAgentInstallFailure.NotStarted
            AgentSetupFailure.Withdrawn -> PhoneAgentInstallFailure.Withdrawn
            AgentSetupFailure.Unknown,
            null -> PhoneAgentInstallFailure.Unknown
        }

    override suspend fun dismissInstallResult(): Outcome<Unit> = lane.step {
        val owner =
            ownerId() ?: return@step Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        when (val cleared = installation(owner).dismissResult(owner)) {
            is Outcome.Failed -> cleared
            is Outcome.Ok -> rereadLocked()
        }
    }

    override fun stopAdmission(profile: ProfileId) {
        synchronized(admission) {
            denied.add(profile)
            accounts.keys.removeAll { it.first == profile }
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = lane.step {
        synchronized(admission) { accounts.keys.removeAll { it.first == profile } }
        val nativeOwner = agentOwner(profile) ?: profile
        if (!Regex("[A-Za-z0-9_-]{1,80}").matches(nativeOwner.raw)) return@step Outcome.Ok(Unit)
        val retained =
            (nativeOwner != profile && stores.profiles.profile(nativeOwner) != null) ||
                stores.prefs.keys("oc.phoneAgentOwner.").any { key ->
                    val alias = key.removePrefix("oc.phoneAgentOwner.")
                    alias != profile.raw &&
                        (stores.prefs.get(key) as? PrefValue.Str)?.value == nativeOwner.raw &&
                        stores.profiles.profile(ProfileId(alias)) != null
                }
        if (retained) return@step Outcome.Ok(Unit)
        synchronized(admission) {
            denied.add(nativeOwner)
            accounts.keys.removeAll { it.first == nativeOwner }
        }
        // The native revocation drains private probe children and closes any retired sign-in relay.
        // It keeps the home and conversations; the profile deletion transaction owns stored
        // cleanup.
        val result = native {
            linux.blockAgentProfile(nativeOwner.raw)
            linux.agentSignIn.deleteProfile(nativeOwner.raw)
            linux.agentHost.stop(nativeOwner.raw, false)
        }
        mutable.value = PhoneAgentsState(needsRestart = needsRestart)
        when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok -> Outcome.Ok(Unit)
        }
    }

    override fun reopen(profile: ProfileId) {
        synchronized(admission) { denied.remove(profile) }
    }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            }
        }

    private fun receipt(output: String): RemovalReceipt? {
        val parts = Regex("^(0|[1-9][0-9]{0,18}) ([01])\\n?$").matchEntire(output) ?: return null
        val bytes = parts.groupValues[1].toLongOrNull() ?: return null
        val absent = parts.groupValues[2] == "1"
        return if (absent && bytes != 0L) null else RemovalReceipt(bytes, absent)
    }

    private companion object {
        const val VERSION_REUSE_MS = 60_000L

        /** An agent id as the kept marker holds it. */
        val ID_SHAPE = Regex("[a-z0-9-]{1,40}")

        /** A version answer a step made out of date ([forgetFacts]). */
        const val STALE = Long.MIN_VALUE
    }
}

/**
 * The Agents state's two stop flags from the helper's exit record ([AgentHelperStatus.noticeSince]). A SIGKILL code on
 * a stop the person asked for, an exit older than the helper's latest launch, or one from before [since] (the phone's
 * boot or the app's install/update) is not a stop on its own; the "maybe Android" flag only refines a real one.
 */
internal object HelperStopNews {
    fun stopped(helper: AgentHelperStatus?, since: Long): Boolean = helper?.noticeSince(since) != null

    fun byAndroid(helper: AgentHelperStatus?, since: Long): Boolean =
        helper?.noticeSince(since) == HelperNotice.PossiblyStoppedByAndroid
}
