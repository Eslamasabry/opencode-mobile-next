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
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.diagnostics.AgentHelperStatus
import io.github.eslamasabry.oc.engine.agents.diagnostics.HelperNotice
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
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneDevice
import io.github.eslamasabry.oc.feature.agents.hub.phoneInstallStep
import io.github.eslamasabry.oc.feature.agents.hub.phoneSharedPartsInPlace
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.PhoneAgentPayloads
import io.github.eslamasabry.opencode_mobile.ProbeStop
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.security.SecureRandom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** The proof stored for [site]'s agent is dropped before a check, as Flutter did (a failed check proves nothing). */
    internal suspend fun clearGate(site: CheckSite): Outcome<Unit> {
        val prefs = gatePrefs(site.owner)
        val gates = LinkedHashMap<String, JsonElement>(PhoneGate.read(prefs, site.owner)).apply { remove(site.agent.id.raw) }
        forgetVersion(site)
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
                    it.availability != AgentAvailability.Hidden
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
                if (authOwner != null && !installing) remember(authOwner, agents)
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
        mutable.value =
            if (owner != null && synchronized(admission) { owner in denied })
                PhoneAgentsState(needsRestart = needsRestart)
            else shownState
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
        val kept = if (final || authOwner == null) emptyMap() else keptFacts(authOwner)
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
                    .map { agent ->
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
                            if (present == true) knownSignIn(authOwner, agent, kept[agent.id.raw]) else null
                        if (!final && ((needsVersion && version == null) || (present == true && signIn == null)))
                            return@map showing[agent.id.raw] ?: checkingRow(agent)
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
                                                    ),
                                            signIn = signIn,
                                            helperRunning = helperRunning,
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
                            signInUnchecked = present == true && PhoneAgentRules.signInUnchecked(agent),
                            signInCheckFailed =
                                status == PhoneAgentStatus.SignInNeeded &&
                                    PhoneAgentRules.signInCheckFailed(agent, signIn),
                            previousVersionAvailable =
                                agent.id == AgentId.CLAUDE &&
                                    present == true &&
                                    shown?.phase != PhoneAgentInstallPhase.Installing &&
                                    authOwner != null &&
                                    knownPrevious(authOwner, agent, PhoneAgentRules.foundVersion(version)),
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
            status = PhoneAgentStatus.Checking,
            downloadBytes = architecture()?.let(agent::artifactFor)?.downloadBytes,
            supportedInstallation = architecture()?.let(agent::installableOn) == true,
        )

    private fun knownVersion(owner: ProfileId, agent: AgentDescriptor): Map<String, Any?>? =
        synchronized(admission) { versions[owner to agent.id]?.second }

    /** The sign-in a tile shows without checking: the agent's last answer, or (shown while checking) the kept one. */
    private fun knownSignIn(
        owner: ProfileId?,
        agent: AgentDescriptor,
        kept: PhoneAgentFacts.Fact?,
    ): AgentSignInPhase? {
        if (agent.signInMethod == AgentSignInMethod.None) return AgentSignInPhase.SignedIn
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

    private fun keptFacts(owner: ProfileId): Map<String, PhoneAgentFacts.Fact> =
        PhoneAgentFacts.decode(gatePrefs(owner).string(PhoneAgentFacts.key(owner.raw)))

    /** This read's answers, kept for the next app start ([PhoneAgentFacts]); written only when they changed. */
    private suspend fun remember(owner: ProfileId, agents: List<AgentDescriptor>) {
        val facts =
            synchronized(admission) {
                if (owner in denied) return
                agents.associate { agent ->
                    agent.id.raw to
                        PhoneAgentFacts.of(versions[owner to agent.id]?.second, accounts[owner to agent.id])
                }
            }
        val kept = keptFacts(owner)
        val merged = PhoneAgentFacts.merge(kept, facts)
        if (merged == kept) return
        gatePrefs(owner).commit { putString(PhoneAgentFacts.key(owner.raw), PhoneAgentFacts.encode(merged)) }
    }

    /**
     * A person's step on [agent] forgets what was known about it, here and across app starts, so neither the next
     * read nor the tile shown while it runs uses an answer the step may have changed. Other agents keep theirs.
     */
    private suspend fun forgetFacts(owner: ProfileId, agent: AgentId, version: Boolean, signIn: Boolean) {
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
            }
        }
        val kept = keptFacts(owner)
        val left = PhoneAgentFacts.forget(kept, agent.raw, version, signIn)
        if (left == kept) return
        gatePrefs(owner).commit { putString(PhoneAgentFacts.key(owner.raw), PhoneAgentFacts.encode(left)) }
    }

    /** Every kept "signed in" or "signed out" of [owner]'s agents (their versions stay). */
    private suspend fun forgetSignIns(owner: ProfileId) {
        val kept = keptFacts(owner)
        if (kept.values.none { it.signedIn != null }) return
        gatePrefs(owner).commit {
            putString(
                PhoneAgentFacts.key(owner.raw),
                PhoneAgentFacts.encode(kept.mapValues { (_, fact) -> fact.copy(signedIn = null) }),
            )
        }
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
            forgetFacts(it, AgentId(id), version = true, signIn = true)
        }
        rereadLocked()
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
        forgetFacts(owner, agent.id, version = false, signIn = true)
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
        val answer =
            synchronized(admission) {
                // Deletion can revoke while the native reader drains. A late read never repopulates
                // it.
                if (owner in denied) return AgentSignInPhase.Failed
                val previous = accounts[key]
                if (
                    fresh.error in setOf(AuthProbeError.HostUnavailable, AuthProbeError.TimedOut) &&
                        previous != null &&
                        previous.state != AuthState.Error
                )
                    previous
                else
                    fresh.also {
                        accounts[key] = it
                        answeredAt[key] = android.os.SystemClock.elapsedRealtime()
                    }
            }
        return phaseOf(answer)
    }

    private fun phaseOf(answer: AuthProjection): AgentSignInPhase = PhoneAgentRules.phaseOf(answer)

    private val versions = HashMap<Pair<ProfileId, AgentId>, Pair<Long, Map<String, Any?>>>()

    /** When the agent itself last answered its sign-in check ([accounts]). */
    private val answeredAt = HashMap<Pair<ProfileId, AgentId>, Long>()

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
