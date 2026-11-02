package io.github.eslamasabry.oc.engine.agents.testing

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.host.AgentHelperExit
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.host.AgentHostStatus
import io.github.eslamasabry.oc.engine.agents.host.AuthAction
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.GuestCommand
import io.github.eslamasabry.oc.engine.agents.host.HelperRunning
import io.github.eslamasabry.oc.engine.agents.host.HelperStart
import io.github.eslamasabry.oc.engine.agents.host.InstalledAgent
import io.github.eslamasabry.oc.engine.agents.host.LegacySignInStatus
import io.github.eslamasabry.oc.engine.agents.host.WorkspaceAccess
import io.github.eslamasabry.oc.engine.agents.host.Abi
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefsEdit
import io.github.eslamasabry.oc.engine.agents.store.AgentSecrets
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** In-memory [AgentPrefs]. [refuseWrites] makes the next commits fail like a refused platform commit. */
class FakeAgentPrefs(initial: Map<String, Any> = emptyMap()) : AgentPrefs {
    val values: MutableMap<String, Any> = initial.toMutableMap()
    var refuseWrites: Boolean = false
    var commits: Int = 0
    private val changed = MutableSharedFlow<Set<String>>(extraBufferCapacity = 64)

    override fun string(key: String): String? = values[key] as? String
    override fun bool(key: String): Boolean? = values[key] as? Boolean

    @Suppress("UNCHECKED_CAST")
    override fun stringList(key: String): List<String>? = values[key] as? List<String>
    override fun keys(prefix: String): Set<String> = values.keys.filter { it.startsWith(prefix) }.toSet()
    override val changes: Flow<Set<String>> get() = changed

    override suspend fun commit(edit: AgentPrefsEdit.() -> Unit): Outcome<Unit> {
        val pending = mutableMapOf<String, Any?>()
        object : AgentPrefsEdit {
            override fun putString(key: String, value: String) { pending[key] = value }
            override fun putBool(key: String, value: Boolean) { pending[key] = value }
            override fun putStringList(key: String, value: List<String>) { pending[key] = value.toList() }
            override fun remove(key: String) { pending[key] = null }
        }.edit()
        if (refuseWrites) return Failure.StorageRefused().failed()
        commits++
        for ((k, v) in pending) if (v == null) values.remove(k) else values[k] = v
        changed.tryEmit(pending.keys)
        return Unit.ok()
    }
}

class FakeAgentSecrets : AgentSecrets {
    val values: MutableMap<String, String> = mutableMapOf()
    var refuseWrites: Boolean = false

    override suspend fun read(name: String): Outcome<String?> = values[name].ok()
    override suspend fun write(name: String, value: String): Outcome<Unit> {
        if (refuseWrites) return Failure.StorageRefused().failed()
        values[name] = value
        return Unit.ok()
    }
    override suspend fun delete(name: String): Outcome<Unit> {
        if (refuseWrites) return Failure.StorageRefused().failed()
        values.remove(name)
        return Unit.ok()
    }
    override suspend fun names(prefix: String): Outcome<Set<String>> = values.keys.filter { it.startsWith(prefix) }.toSet().ok()
}

/** A scriptable [AgentHost]. Every call is recorded in [calls] (names only, never values). */
open class FakeAgentHost : AgentHost {
    val calls: MutableList<String> = mutableListOf()
    val statusFlow: MutableStateFlow<AgentHostStatus> = MutableStateFlow(AgentHostStatus.Stopped)
    var ensureResult: (OwnerId, HelperStart) -> Outcome<HelperRunning> =
        { _, s -> HelperRunning(4099, s.expectedHelperVersion, Abi.Arm64V8a, startedFresh = true).ok() }
    var authResult: suspend (OwnerId, AgentId, AuthAction) -> Outcome<AuthProjection> =
        { _, _, _ -> AuthProjection(AuthState.SignedOut).ok() }
    var installed: MutableMap<AgentId, InstalledAgent> = mutableMapOf()
    var deleteHomeResult: Outcome<Unit> = Unit.ok()
    var stopResult: Outcome<Unit> = Unit.ok()
    var exits: List<AgentHelperExit> = emptyList()
    var lastStart: HelperStart? = null

    override val available: Boolean get() = true
    override val status: StateFlow<AgentHostStatus> get() = statusFlow

    override suspend fun ensureRunning(owner: OwnerId, start: HelperStart): Outcome<HelperRunning> {
        calls += "ensureRunning:${owner.raw}"
        lastStart = start
        return ensureResult(owner, start)
    }
    override suspend fun stop(owner: OwnerId, guardedForUpdate: Boolean): Outcome<Unit> {
        calls += "stop:${owner.raw}:$guardedForUpdate"
        return stopResult
    }
    override suspend fun prepareUpdate(owner: OwnerId): Outcome<Unit> { calls += "prepareUpdate:${owner.raw}"; return Unit.ok() }
    override suspend fun deleteHome(owner: OwnerId): Outcome<Unit> { calls += "deleteHome:${owner.raw}"; return deleteHomeResult }
    override suspend fun installedVersion(agent: AgentId): Outcome<InstalledAgent?> { calls += "installedVersion:${agent.raw}"; return installed[agent].ok() }
    override suspend fun workspaceCheck(owner: OwnerId): Outcome<WorkspaceAccess> { calls += "workspaceCheck"; return WorkspaceAccess(true, true).ok() }
    override suspend fun auth(owner: OwnerId, agent: AgentId, action: AuthAction, timeout: Duration): Outcome<AuthProjection> {
        calls += "auth:${agent.raw}:$action:$timeout"
        return authResult(owner, agent, action)
    }
    override fun signInCommand(owner: OwnerId, agent: AgentId, args: List<String>): Outcome<GuestCommand> =
        GuestCommand(agent.raw, persistentListOf(), persistentMapOf(), "/home/oc/.oc-profiles/${owner.raw}").ok()
    override suspend fun legacySignInStatus(owner: OwnerId, agent: AgentId): Outcome<LegacySignInStatus> = LegacySignInStatus.Unknown.ok()
    override suspend fun helperExits(limit: Int): Outcome<List<AgentHelperExit>> = exits.take(limit).ok()
}
