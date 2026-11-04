package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.diagnostics.AgentHelperStatus
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
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
import io.github.eslamasabry.oc.engine.agents.scripts.AgentPhoneScripts
import io.github.eslamasabry.oc.engine.agents.scripts.AgentRemovalScripts
import io.github.eslamasabry.oc.engine.agents.store.SecureBytes
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.profiles.SignInReset
import io.github.eslamasabry.oc.engine.profiles.SignInResetParticipant
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

/**
 * The Agents hub reads the real shared payloads and removes only catalog-owned files, keeping
 * profile homes.
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
) : PhoneAgents, ProfileCloser {
    override val owner: String = ":engine:agents:auth"
    private val admission = Any()
    private val denied = HashSet<ProfileId>()
    private val accounts = HashMap<Pair<ProfileId, AgentId>, AuthProjection>()
    @Volatile private var needsRestart = false
    private val mutable = MutableStateFlow(PhoneAgentsState())
    override val state = mutable.asStateFlow()
    private val lane = Mutex()
    private val linux by lazy { BuiltinLinux.get(app) }
    private val catalog = AgentCatalog.builtIn
    private val installers = HashMap<ProfileId, AgentInstaller>()

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

    private fun agentOwner(profile: ProfileId?): ProfileId? = profile?.let {
        (stores.prefs.get("oc.phoneAgentOwner.${it.raw}") as? PrefValue.Str)
            ?.value
            ?.takeIf { value -> Regex("[A-Za-z0-9_-]{1,80}").matches(value) }
            ?.let(::ProfileId) ?: it
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

    override suspend fun refresh(): Outcome<Unit> = lane.withLock { refreshLocked() }

    private suspend fun refreshLocked(): Outcome<Unit> {
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
            phase?.let { PhoneAgentInstallation(id.raw, it, data.fraction) }
        }
        val rows = native {
            val installed = linux.installed
            val helper =
                if (authOwner != null && synchronized(admission) { authOwner !in denied }) {
                    val record = linux.agentHost.status(authOwner.raw)
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
            PhoneAgentsState(
                available = installed,
                installation = shown,
                helperStoppedUnexpectedly = helper?.unexpectedStop == true,
                helperMayHaveBeenStoppedByAndroid = helper?.possibleResourceKill == true,
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
                                        true -> signInStatus(authOwner, agent)
                                        false -> PhoneAgentStatus.NotInstalled
                                        null -> PhoneAgentStatus.Unavailable
                                    },
                                    previousVersionAvailable =
                                        agent.id == AgentId.CLAUDE &&
                                            present == true &&
                                            shown?.phase != PhoneAgentInstallPhase.Installing &&
                                            previousAvailable(agent),
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
                mutable.value =
                    if (owner != null && synchronized(admission) { owner in denied })
                        PhoneAgentsState(needsRestart = needsRestart)
                    else rows.value
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> rows
        }
    }

    override suspend fun install(id: String): Outcome<PhoneAgentInstallResult> = lane.withLock {
        if (needsRestart)
            return@withLock Outcome.Ok(
                PhoneAgentInstallResult.Refused(PhoneAgentInstallIssue.Unavailable)
            )
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

    private fun previousAvailable(agent: AgentDescriptor): Boolean =
        try {
            linux.run(AgentPhoneScripts.previousVersion(agent), 30, agentUser = true).exitCode == 0
        } catch (_: Exception) {
            false
        }

    override suspend fun usePreviousVersion(id: String): Outcome<Unit> = lane.withLock {
        if (needsRestart || id != AgentId.CLAUDE.raw)
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val owner =
            agentOwner(ownerId())
                ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val agent =
            catalog.byId(AgentId.CLAUDE)
                ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
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
        if (result is Outcome.Failed) return@withLock result
        synchronized(admission) { accounts.remove(owner to agent.id) }
        refreshLocked()
    }

    /** Reset closes clients first; only the engine's allowlisted secure slots are erased. */
    override suspend fun resetSavedSignIns(): Outcome<Unit> = lane.withLock {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return@withLock warm
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
        }
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
     * Private status reads are keyed by the runtime owner, never by the selected remote profile.
     */
    private fun signInStatus(owner: ProfileId?, agent: AgentDescriptor): PhoneAgentStatus {
        if (agent.signInMethod == AgentSignInMethod.None) return PhoneAgentStatus.PhoneCheck
        if (owner == null) return PhoneAgentStatus.Unavailable
        val authored =
            AgentAuthScripts.script(agent, AuthScriptAction.Probe)
                ?: return PhoneAgentStatus.Unavailable
        val key = owner to agent.id
        if (synchronized(admission) { owner in denied }) return PhoneAgentStatus.Unavailable
        val response =
            linux.agentAuthProbe(
                mapOf(
                    "profileId" to owner.raw,
                    "agentId" to agent.id.raw,
                    "action" to "probe",
                    "script" to AgentAuthScripts.forProfile(owner, authored),
                    "timeoutSeconds" to if (agent.id == AgentId.CLAUDE) 65L else 10L,
                )
            )
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
                if (owner in denied) return PhoneAgentStatus.Unavailable
                val previous = accounts[key]
                if (
                    fresh.error in setOf(AuthProbeError.HostUnavailable, AuthProbeError.TimedOut) &&
                        previous != null &&
                        previous.state != AuthState.Error
                )
                    previous
                else fresh.also { accounts[key] = it }
            }
        return when (answer.state) {
            AuthState.SignedOut -> PhoneAgentStatus.SignInNeeded
            AuthState.SignedIn -> PhoneAgentStatus.PhoneCheck
            AuthState.Error -> PhoneAgentStatus.Unavailable
        }
    }

    override fun stopAdmission(profile: ProfileId) {
        synchronized(admission) {
            denied.add(profile)
            accounts.keys.removeAll { it.first == profile }
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = lane.withLock {
        synchronized(admission) { accounts.keys.removeAll { it.first == profile } }
        val nativeOwner = agentOwner(profile) ?: profile
        if (!Regex("[A-Za-z0-9_-]{1,80}").matches(nativeOwner.raw)) return@withLock Outcome.Ok(Unit)
        val retained =
            (nativeOwner != profile && stores.profiles.profile(nativeOwner) != null) ||
                stores.prefs.keys("oc.phoneAgentOwner.").any { key ->
                    val alias = key.removePrefix("oc.phoneAgentOwner.")
                    alias != profile.raw &&
                        (stores.prefs.get(key) as? PrefValue.Str)?.value == nativeOwner.raw &&
                        stores.profiles.profile(ProfileId(alias)) != null
                }
        if (retained) return@withLock Outcome.Ok(Unit)
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
}
