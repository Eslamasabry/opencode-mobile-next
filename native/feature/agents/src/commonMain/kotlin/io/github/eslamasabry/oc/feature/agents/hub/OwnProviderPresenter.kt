package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderProblem
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The own-provider sheet and the "How this works" sheet of the Agents page. Never holds the key. */
public data class OwnProviderUi(
    /** False where no phone Linux is bound: nothing is offered. */
    val available: Boolean = false,
    val supported: ImmutableSet<String> = persistentSetOf(),
    val saved: ImmutableMap<String, OwnProviderView> = persistentMapOf(),
    /** The agent whose own-provider sheet is open. */
    val editing: String? = null,
    val working: Boolean = false,
    val problem: OwnProviderProblem? = null,
    /** Saving or removing did not finish; plain words on the sheet, the technical side under Details. */
    val failure: Failure? = null,
    /** "How this works" is open. */
    val helpOpen: Boolean = false,
)

public interface OwnProviderActions {
    public fun openOwnProvider(agent: String)

    public fun closeOwnProvider()

    /** Any edit clears the last refusal. */
    public fun editedOwnProvider()

    public fun saveOwnProvider(agent: String, endpoint: String, key: String, model: String)

    public fun removeOwnProvider(agent: String)

    public fun openAgentsHelp()

    public fun closeAgentsHelp()
}

/**
 * One operation at a time; a saved or removed provider re-reads the phone's agents so the agent's sign-in state on its
 * tile follows at once (Claude Code reads as signed in with its own provider).
 */
public class OwnProviderPresenter(
    private val scope: CoroutineScope,
    private val providers: PhoneOwnProviders?,
    private val refreshPhone: suspend () -> Unit,
) : OwnProviderActions {
    private val local = MutableStateFlow(
        OwnProviderUi(available = providers != null, supported = providers?.supported.orEmpty().toImmutableSet()),
    )

    public val state: StateFlow<OwnProviderUi> =
        combine(local, providers?.state ?: flowOf(persistentMapOf())) { own, saved -> own.copy(saved = saved) }
            .stateIn(scope, SharingStarted.Eagerly, local.value.copy(saved = providers?.state?.value ?: persistentMapOf()))

    override fun openOwnProvider(agent: String) {
        if (providers == null || agent !in providers.supported) return
        local.update { it.copy(editing = agent, problem = null, failure = null) }
    }

    override fun closeOwnProvider() {
        if (local.value.working) return
        local.update { it.copy(editing = null, problem = null, failure = null) }
    }

    override fun editedOwnProvider() {
        if (local.value.problem != null || local.value.failure != null) local.update { it.copy(problem = null, failure = null) }
    }

    override fun saveOwnProvider(agent: String, endpoint: String, key: String, model: String) {
        val port = providers ?: return
        if (local.value.working) return
        local.update { it.copy(working = true, problem = null, failure = null) }
        scope.launch {
            try {
                when (val saved = port.save(agent, endpoint, key, model)) {
                    is Outcome.Failed -> local.update { it.copy(failure = saved.failure) }
                    is Outcome.Ok -> when (val answer = saved.value) {
                        is OwnProviderSave.Refused -> local.update { it.copy(problem = answer.problem) }
                        OwnProviderSave.Saved -> {
                            local.update { it.copy(editing = null) }
                            refreshPhone()
                        }
                    }
                }
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }

    override fun removeOwnProvider(agent: String) {
        val port = providers ?: return
        if (local.value.working) return
        local.update { it.copy(working = true, problem = null, failure = null) }
        scope.launch {
            try {
                when (val removed = port.remove(agent)) {
                    is Outcome.Failed -> local.update { it.copy(failure = removed.failure) }
                    is Outcome.Ok -> {
                        local.update { it.copy(editing = null) }
                        refreshPhone()
                    }
                }
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }

    override fun openAgentsHelp() {
        local.update { it.copy(helpOpen = true) }
    }

    override fun closeAgentsHelp() {
        local.update { it.copy(helpOpen = false) }
    }
}
