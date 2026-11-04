package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.install.AgentInstaller
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupPhase
import io.github.eslamasabry.oc.engine.agents.install.InstallRefusal
import io.github.eslamasabry.oc.engine.agents.install.InstallStart
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.probe.AuthScriptAction
import io.github.eslamasabry.oc.engine.agents.removal.AgentRemoval
import io.github.eslamasabry.oc.engine.agents.removal.AgentRemovalRunner
import io.github.eslamasabry.oc.engine.agents.removal.RemovalReceipt
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRun
import io.github.eslamasabry.oc.engine.agents.scripts.AgentRemovalScripts
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemoval
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemovalIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.PhoneAgentPayloads
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.security.SecureRandom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The Agents hub reads the real shared payloads and removes only catalog-owned files, keeping
 * profile homes. "Check phone agents" also runs the explicit read-only sign-in probe for the active
 * profile: no helper, sign-in or installer is started.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<PhoneAgents>())
class AppPhoneAgents(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val setupJobs: NativeAgentSetupJobs,
    appScope: AppCoroutineScope,
) : PhoneAgents {
    private val mutable = MutableStateFlow(PhoneAgentsState())
    override val state = mutable.asStateFlow()
    private val lane = Mutex()
    private val linux by lazy { BuiltinLinux.get(app) }
    private val catalog = AgentCatalog.builtIn
    private val installers = HashMap<ProfileId, AgentInstaller>()

    /** Sign-in states from the last explicit probe, valid only for [probedOwner]. */
    @Volatile private var probedOwner: ProfileId? = null
    @Volatile private var probed: Map<String, PhoneAgentStatus> = emptyMap()

    init {
        appScope.launch {
            combine(stores.profiles.activeId, stores.profiles.profiles) { active, saved ->
                    active to saved.any { it.id == active }
                }
                .collect { (active, exists) ->
                    if (probedOwner != null && (probedOwner != active || !exists)) {
                        probedOwner = null
                        probed = emptyMap()
                        mutable.value =
                            mutable.value.copy(
                                agents =
                                    mutable.value.agents
                                        .map { row ->
                                            if (row.status == PhoneAgentStatus.Ready ||
                                                row.status == PhoneAgentStatus.SignInNeeded ||
                                                row.status == PhoneAgentStatus.Checking)
                                                row.copy(status = PhoneAgentStatus.PhoneCheck)
                                            else row
                                        }
                                        .toImmutableList()
                            )
                    }
                }
        }
    }

    private fun installation(owner: ProfileId): AgentInstaller =
        installers.getOrPut(owner) {
            AgentInstaller(
                KvAgentPrefs(stores.prefs, stores.lanes.forProfile(owner)),
                setupJobs,
                guard = setupJobs.guard,
            )
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

    private fun architecture(): AgentArchitecture? =
        when {
            "arm64-v8a" in Build.SUPPORTED_ABIS -> AgentArchitecture.Arm64
            "x86_64" in Build.SUPPORTED_ABIS -> AgentArchitecture.X64
            else -> null
        }

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

    override suspend fun refresh(): Outcome<Unit> = lane.withLock {
        when (val read = refreshLocked()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> probeLocked()
        }
    }

    /** Explicit read-only sign-in checks for installed payloads: unknown results keep known state. */
    private suspend fun probeLocked(): Outcome<Unit> {
        val owner =
            stores.profiles.activeId.value ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val previous = if (probedOwner == owner) probed else emptyMap()
        probedOwner = owner
        probed = previous
        fun current() =
            stores.profiles.activeId.value == owner && stores.profiles.profile(owner) != null
        val rows = mutable.value.agents.toMutableList()
        val targets =
            rows.indices.filter { index ->
                val agent = catalog.byId(AgentId(rows[index].id))
                rows[index].status == PhoneAgentStatus.PhoneCheck &&
                    agent != null &&
                    agent.availability != AgentAvailability.Hidden
            }
        for (index in targets) rows[index] = rows[index].copy(status = PhoneAgentStatus.Checking)
        mutable.value = mutable.value.copy(agents = rows.toImmutableList())
        var probeFailure: Failure? = null
        for (index in targets) {
            val agent = catalog.byId(AgentId(rows[index].id)) ?: continue
            if (!current()) return stale()
            val script = AgentAuthScripts.script(agent, AuthScriptAction.Probe)
            val answer =
                withContext(dispatchers.io) {
                    attempt({ Failure.Unexpected() }) {
                        if (!linux.installed || script == null) null
                        else
                            linux.agentAuthProbe(
                                mapOf(
                                    "profileId" to owner.raw,
                                    "agentId" to agent.id.raw,
                                    "action" to "probe",
                                    "script" to AgentAuthScripts.forProfile(owner, script),
                                    "timeoutSeconds" to if (agent.id == AgentId.CLAUDE) 65 else 10,
                                )
                            )
                    }
                }
            if (!current()) return stale()
            val projection = (answer as? Outcome.Ok)?.value
            if (projection == null) {
                probeFailure = Failure.Unavailable(FeatureId("builtinLinux"))
            } else if (
                projection["state"] == "error" &&
                    projection["error"] != "notInstalled" &&
                    projection["error"] != "probeUnsupported"
            ) {
                probeFailure =
                    if (projection["error"] == "timedOut") Failure.TimedOut()
                    else Failure.ComputerDidNotAnswer()
            }
            val status =
                when (projection?.get("state")) {
                    "signedIn" -> PhoneAgentStatus.Ready
                    "signedOut" -> PhoneAgentStatus.SignInNeeded
                    else ->
                        when (projection?.get("error")) {
                            "notInstalled" -> PhoneAgentStatus.NotInstalled
                            // No private status protocol (gemini, qwen, goose, omp): a check on
                            // the phone is needed; it is not a failed probe.
                            "probeUnsupported" -> PhoneAgentStatus.PhoneCheck
                            else -> previous[agent.id.raw] ?: PhoneAgentStatus.PhoneCheck
                        }
                }
            probed = probed + (agent.id.raw to status)
            rows[index] = rows[index].copy(status = status)
            mutable.value = mutable.value.copy(agents = rows.toImmutableList())
        }
        return probeFailure?.failed() ?: Unit.ok()
    }

    private fun stale(): Outcome<Unit> {
        probedOwner = null
        probed = emptyMap()
        mutable.value =
            mutable.value.copy(
                agents =
                    mutable.value.agents
                        .map {
                            if (it.status == PhoneAgentStatus.Checking)
                                it.copy(status = PhoneAgentStatus.PhoneCheck)
                            else it
                        }
                        .toImmutableList()
            )
        return Failure.Refused(RefusalReason.StaleScope).failed()
    }

    private suspend fun refreshLocked(): Outcome<Unit> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val owner = ownerId()
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
            phase?.let { PhoneAgentInstallation(id.raw, it, data.fraction) }
        }
        val rows = native {
            val installed = linux.installed
            PhoneAgentsState(
                available = installed,
                installation = shown,
                agents =
                    if (!installed) emptyList<PhoneAgentRow>().toImmutableList()
                    else
                        catalog.agents
                            .filter { it.runsOnPhoneHost && it.recipe != null }
                            .map { agent ->
                                val present =
                                    PhoneAgentPayloads.present(linux, agent.recipe!!.executable)
                                PhoneAgentRow(
                                    agent.id.raw,
                                    agent.name,
                                    when (present) {
                                        true ->
                                            probed[agent.id.raw]
                                                ?.takeIf {
                                                    probedOwner == stores.profiles.activeId.value
                                                } ?: PhoneAgentStatus.PhoneCheck
                                        false -> PhoneAgentStatus.NotInstalled
                                        null -> PhoneAgentStatus.Unavailable
                                    },
                                    removable =
                                        removal.removable(agent.id) &&
                                            present == true &&
                                            shown?.phase != PhoneAgentInstallPhase.Installing,
                                    installable =
                                        owner != null &&
                                            architecture()?.let(agent::installableOn) == true &&
                                            shown?.phase != PhoneAgentInstallPhase.Installing,
                                )
                            }
                            .toImmutableList(),
            )
        }
        return when (rows) {
            is Outcome.Ok -> {
                mutable.value = rows.value
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> rows
        }
    }

    override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> = lane.withLock {
        val owner =
            ownerId()
                ?: return@withLock Outcome.Ok(
                    PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
                )
        val abi =
            architecture()
                ?: return@withLock Outcome.Ok(
                    PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
                )
        val answer =
            when (val started = installation(owner).install(owner, AgentId(id), abi)) {
                is Outcome.Failed -> return@withLock started
                is Outcome.Ok ->
                    when (val value = started.value) {
                        InstallStart.Started -> PhoneAgentInstallResult.Started
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
        refreshLocked()
        Outcome.Ok(answer)
    }

    override suspend fun cancelInstall(): Outcome<Unit> = lane.withLock {
        val owner =
            ownerId() ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        when (val cancelled = installation(owner).cancel(owner)) {
            is Outcome.Failed -> cancelled
            is Outcome.Ok -> refreshLocked()
        }
    }

    override suspend fun remove(id: String): Outcome<PhoneAgentRemoval> = lane.withLock {
        val owner =
            try {
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
            } catch (e: Exception) {
                null
            }
        if (owner.isNullOrEmpty())
            return@withLock Outcome.Ok(
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
                is Outcome.Failed -> return@withLock result
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
        refreshLocked()
        Outcome.Ok(answer)
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
}
