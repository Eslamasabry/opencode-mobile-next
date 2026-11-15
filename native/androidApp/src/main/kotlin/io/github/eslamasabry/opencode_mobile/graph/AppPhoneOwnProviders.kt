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
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderAgents
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderKeys
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderRules
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderScript
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderStore
import io.github.eslamasabry.oc.engine.agents.providers.UnharnessedProvider
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderSave
import io.github.eslamasabry.oc.feature.agents.hub.OwnProviderView
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneOwnProviders
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
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
 * Issue 147: "Use your own provider" for Claude Code on this phone. The key goes to the secure
 * store ([OwnProviderStore], `oc.agentProviderKeys.<owner>`) and from there only into Claude Code's
 * own `settings.json` in the agent home, on stdin of a script that prints nothing
 * ([OwnProviderScript]). It is registered with the redactor before anything else, so no log,
 * diagnostics report or analytics text can carry it. Endpoint and model are
 * `oc.agentOwnProviders.<owner>`. Both names are swept with the owner's profile
 * (SecurityFeatureBindings), and the agent home that holds the settings file is erased with it.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<PhoneOwnProviders>())
class AppPhoneOwnProviders(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val phone: PhoneAgents,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
) : PhoneOwnProviders {
    private val lane = Mutex()
    private val linux by lazy { BuiltinLinux.get(app) }

    override val supported: Set<String> = OwnProviderAgents.supported.map { it.raw }.toSet()

    private fun store(owner: ProfileId): OwnProviderStore =
        OwnProviderStore(
            KvAgentPrefs(stores.prefs, stores.lanes.forProfile(owner)),
            StoreSecrets(stores),
            redactor::registerSecret,
        )

    private fun providerOwner(profile: ProfileId, agent: String): ProfileId =
        if (agent == "unharnessed")
            PhoneProviderOwner.resolve(
                profile,
                (stores.prefs.get("oc.phoneAgentOwner.${profile.raw}") as? PrefValue.Str)?.value,
            )
        else profile

    override val state: StateFlow<ImmutableMap<String, OwnProviderView>> = flow {
        if (stores.warm() is Outcome.Failed) {
            emit(persistentMapOf())
            return@flow
        }
        emitAll(
            combine(
                phone.state.map { it.owner }.distinctUntilChanged(),
                stores.prefs.changes.onStart { emit(emptySet()) },
            ) { owner, _ ->
                owner?.let(::read) ?: persistentMapOf()
            }
        )
    }
        .stateIn(appScope, SharingStarted.Eagerly, persistentMapOf())

    private fun read(owner: ProfileId): ImmutableMap<String, OwnProviderView> {
        val privateOwner = providerOwner(owner, "unharnessed")
        val providers = store(owner).read(owner).toMutableMap()
        providers.remove(AgentId("unharnessed"))
        store(privateOwner).read(privateOwner)[AgentId("unharnessed")]?.let {
            providers[AgentId("unharnessed")] = it
        }
        return providers.entries
            .associate { (agent, p) -> agent.raw to OwnProviderView(p.endpoint, p.model) }
            .toImmutableMap()
    }

    override suspend fun save(
        agent: String,
        endpoint: String,
        key: String,
        model: String,
    ): Outcome<OwnProviderSave> = lane.withLock {
        if (key.isNotEmpty()) redactor.registerSecret(key)
        val profile =
            phone.state.value.owner ?: return@withLock Outcome.Failed(Failure.Unexpected())
        val owner = providerOwner(profile, agent)
        val id = AgentId(agent)
        val store = store(owner)
        // Saving without replacing the key keeps the saved one.
        val typed = key.ifEmpty {
            when (val kept = store.key(owner, id)) {
                is Outcome.Failed -> return@withLock kept
                is Outcome.Ok -> kept.value.orEmpty()
            }
        }
        OwnProviderRules.problem(id, endpoint, typed, model)?.let {
            return@withLock Outcome.Ok(OwnProviderSave.Refused(it))
        }
        when (val saved = store.save(owner, id, endpoint, typed, model)) {
            is Outcome.Failed -> return@withLock saved
            is Outcome.Ok -> Unit
        }
        val provider = store.read(owner)[id] ?: return@withLock Outcome.Failed(Failure.Unexpected())
        when (
            val written =
                writeSettings(
                    owner,
                    if (agent == "unharnessed") UnharnessedProvider.input(provider, typed)
                    else OwnProviderScript.apply(provider, typed),
                    agent,
                )
        ) {
            is Outcome.Failed -> written
            is Outcome.Ok -> {
                if (agent == "unharnessed") (phone as? AppPhoneAgents)?.noteSignedIn(profile, id)
                Outcome.Ok(OwnProviderSave.Saved)
            }
        }
    }

    override suspend fun remove(agent: String): Outcome<Unit> = lane.withLock {
        val profile =
            phone.state.value.owner ?: return@withLock Outcome.Failed(Failure.Unexpected())
        val owner = providerOwner(profile, agent)
        // The agent's settings first: a key left there while the app forgot it could never be
        // removed from here.
        when (
            val cleared =
                writeSettings(
                    owner,
                    if (agent == "unharnessed") UnharnessedProvider.input(null)
                    else OwnProviderScript.clear(),
                    agent,
                )
        ) {
            is Outcome.Failed -> return@withLock cleared
            is Outcome.Ok -> Unit
        }
        store(owner).remove(owner, AgentId(agent))
    }

    /**
     * Runs the settings script as the agent user; its output is never logged, the values ride on
     * stdin only.
     */
    private suspend fun writeSettings(
        owner: ProfileId,
        stdin: String,
        agent: String,
    ): Outcome<Unit> =
        withContext(dispatchers.io) {
            try {
                val run =
                    linux.run(
                        if (agent == "unharnessed") UnharnessedProvider.script(owner)
                        else OwnProviderScript.script(owner),
                        60,
                        agentUser = true,
                        logOutput = false,
                        stdin = stdin,
                    )
                if (run.exitCode == OwnProviderScript.OK) Outcome.Ok(Unit)
                else
                    Outcome.Failed(
                        Failure.Unexpected(
                            redactor.details("own provider settings: exit ${run.exitCode}")
                        )
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failed(Failure.Unexpected(Redacted.shape(e)))
            }
        }
}

internal object PhoneProviderOwner {
    fun resolve(profile: ProfileId, alias: String?): ProfileId =
        alias?.takeIf { Regex("[A-Za-z0-9_-]{1,80}").matches(it) }?.let(::ProfileId) ?: profile
}

/**
 * The profile deletion finds the own-provider key (secure store) and its endpoint and model
 * (preferences).
 */
internal object OwnProviderSweep {
    fun register(scope: ProfileScope) {
        scope.registerSecret(OwnProviderKeys.SECRETS_PREFIX, ":engine:agents")
        scope.register(
            KeyShape(
                OwnProviderKeys.PROVIDERS_PREFIX,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:agents",
                "own provider endpoint and model per phone agent (never the key)",
            )
        )
    }
}
