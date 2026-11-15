package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.host.PaseoConfig
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentCheckResult
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentCheckScript
import io.github.eslamasabry.oc.engine.agents.providers.PhoneHelperConfig
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentInstallScript
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentKeys
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentProblem
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentRules
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentStore
import io.github.eslamasabry.oc.engine.agents.store.AgentSecrets
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentSave
import io.github.eslamasabry.oc.feature.agents.hub.OwnAgentView
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneOwnAgents
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Issue 147: "Add your own agent". The agent (name, start words, install command, environment
 * names) is `oc.ownAgents.<owner>`; the values are `oc.ownAgentEnv.<owner>` in the secure store,
 * registered with the redactor whenever they are given or read. The helper learns them at its next
 * start ([phoneHelperConfig]). Install and Check run in the phone's Linux as the agent user, with
 * the values on stdin and no output kept.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<PhoneOwnAgents>())
class AppPhoneOwnAgents(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val phone: PhoneAgents,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
) : PhoneOwnAgents {
    private val lane = Mutex()
    private val linux by lazy { BuiltinLinux.get(app) }

    private fun store(owner: ProfileId): OwnAgentStore = ownAgentStore(stores, owner, redactor::registerSecret)

    override val state: StateFlow<ImmutableList<OwnAgentView>> =
        flow {
                if (stores.warm() is Outcome.Failed) {
                    emit(persistentListOf())
                    return@flow
                }
                emitAll(
                    combine(
                        phone.state.map { it.owner }.distinctUntilChanged(),
                        stores.prefs.changes.onStart { emit(emptySet()) },
                    ) { owner, _ ->
                        owner
                            ?.let { id ->
                                store(id).read(id).map {
                                    OwnAgentView(
                                        it.id.raw,
                                        it.name,
                                        it.start.joinToString(" "),
                                        it.install,
                                        it.envNames.toImmutableList(),
                                    )
                                }
                            }
                            .orEmpty()
                            .toImmutableList()
                    }
                )
            }
            .stateIn(appScope, SharingStarted.Eagerly, persistentListOf())

    override suspend fun save(
        id: String?,
        name: String,
        start: String,
        install: String,
        env: List<Pair<String, String>>,
    ): Outcome<OwnAgentSave> =
        lane.withLock {
            env.forEach { redactor.registerSecret(it.second) }
            val owner = phone.state.value.owner ?: return@withLock Outcome.Failed(Failure.Unexpected())
            OwnAgentRules.problem(name, start, install, env)?.let {
                return@withLock Outcome.Ok(OwnAgentSave.Refused(it))
            }
            val store = store(owner)
            if (id == null && store.read(owner).size >= OwnAgentRules.MAX_AGENTS)
                return@withLock Outcome.Ok(OwnAgentSave.Refused(OwnAgentProblem.TooMany))
            when (val saved = store.save(owner, id?.let(::AgentId), name, start, install, env)) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> Outcome.Ok(OwnAgentSave.Saved(saved.value.id.raw))
            }
        }

    override suspend fun install(id: String): Outcome<Boolean> =
        lane.withLock {
            val owner = phone.state.value.owner ?: return@withLock Outcome.Failed(Failure.Unexpected())
            val agent =
                store(owner).read(owner).firstOrNull { it.id.raw == id }?.takeIf { it.install != null }
                    ?: return@withLock Outcome.Failed(Failure.Unexpected())
            run(OwnAgentInstallScript.script(owner), OwnAgentInstallScript.TIMEOUT_SECONDS, agent.install + "\n")
                .map { it == 0 }
        }

    override suspend fun check(id: String): Outcome<OwnAgentCheckResult> =
        lane.withLock {
            val owner = phone.state.value.owner ?: return@withLock Outcome.Failed(Failure.Unexpected())
            val store = store(owner)
            val agent =
                store.read(owner).firstOrNull { it.id.raw == id }
                    ?: return@withLock Outcome.Failed(Failure.Unexpected())
            val env =
                when (val values = store.env(owner)) {
                    is Outcome.Failed -> return@withLock values
                    is Outcome.Ok -> values.value[agent.id].orEmpty()
                }
            run(OwnAgentCheckScript.script(owner), 60, OwnAgentCheckScript.stdin(agent, env))
                .map(OwnAgentCheckResult::of)
        }

    override suspend fun remove(id: String): Outcome<Unit> =
        lane.withLock {
            val owner = phone.state.value.owner ?: return@withLock Outcome.Failed(Failure.Unexpected())
            store(owner).remove(owner, AgentId(id))
        }

    /** One script as the agent user; nothing it prints is kept or logged, the answer is its exit code. */
    @OptIn(RedactorApi::class)
    private suspend fun run(script: String, timeoutSeconds: Int, stdin: String): Outcome<Int> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(
                    linux
                        .run(script, timeoutSeconds.toLong(), agentUser = true, logOutput = false, stdin = stdin)
                        .exitCode
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failed(Failure.Unexpected(Redacted.shape(e)))
            }
        }
}

/** The secure store as the agents engine's port. */
internal class StoreSecrets(private val stores: PreviewStores) : AgentSecrets {
    override suspend fun read(name: String): Outcome<String?> = stores.secrets.read(name)

    override suspend fun write(name: String, value: String): Outcome<Unit> = stores.secrets.write(name, value)

    override suspend fun delete(name: String): Outcome<Unit> = stores.secrets.delete(name)

    override suspend fun names(prefix: String): Outcome<Set<String>> = stores.secrets.names(prefix)
}

internal fun ownAgentStore(stores: PreviewStores, owner: ProfileId, registerSecret: (String) -> Unit): OwnAgentStore =
    OwnAgentStore(KvAgentPrefs(stores.prefs, stores.lanes.forProfile(owner)), StoreSecrets(stores), registerSecret)

/**
 * The helper's `config.json` for [owner]: the catalog's agents ([PaseoConfig.render]), the person's own agents and
 * Codex with its OS sandbox off (issue 147, [PhoneHelperConfig]). Values that cannot be read leave the agent without
 * them (it then says so when it starts), never the helper without its config. Used by every start of the phone's
 * helper; a computer's Paseo config is never written by the app.
 */
internal suspend fun phoneHelperConfig(stores: PreviewStores, owner: String, registerSecret: (String) -> Unit): String {
    val base = PaseoConfig.render().json
    if (!Regex("^[A-Za-z0-9_-]{1,80}$").matches(owner)) return PhoneHelperConfig.compose(base, emptyList(), emptyMap())
    val id = ProfileId(owner)
    val store = ownAgentStore(stores, id, registerSecret)
    val agents = store.read(id)
    val env = if (agents.isEmpty()) emptyMap() else (store.env(id) as? Outcome.Ok)?.value.orEmpty()
    return PhoneHelperConfig.compose(base, agents, env)
}

/** The profile deletion finds the own agents (preferences) and their values (secure store). */
internal object OwnAgentSweep {
    fun register(scope: ProfileScope) {
        scope.registerSecret(OwnAgentKeys.ENV_PREFIX, ":engine:agents")
        scope.register(
            KeyShape(
                OwnAgentKeys.AGENTS_PREFIX,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:agents",
                "own agents: name, start and install commands, value names (never the values)",
            )
        )
    }
}
