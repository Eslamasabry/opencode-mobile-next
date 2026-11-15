package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentCheckResult
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentProblem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Add your own agent" (issue 147): an ACP program in the phone's Linux, added by name, start command, optional install
 * command and environment values. Bound by the app where the phone's Linux exists. Values never enter any state here:
 * they go from the sheet's fields to [save] and on to the secure store.
 */
public interface PhoneOwnAgents {
    public val state: StateFlow<ImmutableList<OwnAgentView>>

    /** Adds ([id] null) or changes an agent. An empty value for a name the agent already has keeps the saved value. */
    public suspend fun save(
        id: String?,
        name: String,
        start: String,
        install: String,
        env: List<Pair<String, String>>,
    ): Outcome<OwnAgentSave>

    /** Runs the agent's install command in the phone's Linux; true when it ended well. */
    public suspend fun install(id: String): Outcome<Boolean>

    /** Starts the command and asks it to answer as an ACP agent. */
    public suspend fun check(id: String): Outcome<OwnAgentCheckResult>

    public suspend fun remove(id: String): Outcome<Unit>
}

/** An own agent as the page shows it (names of the environment values, never the values). */
public data class OwnAgentView(
    val id: String,
    val name: String,
    val start: String,
    val install: String?,
    val envNames: ImmutableList<String>,
)

public sealed interface OwnAgentSave {
    public data class Saved(val id: String) : OwnAgentSave

    public data class Refused(val problem: OwnAgentProblem) : OwnAgentSave
}

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface PhoneOwnAgentBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneOwnAgents: Set<PhoneOwnAgents>
}

/** The own-agent sheet of the Agents page. */
public data class OwnAgentsUi(
    val available: Boolean = false,
    val agents: ImmutableList<OwnAgentView> = persistentListOf(),
    /** The sheet is open: for a new agent ([editingId] null) or for [editingId]. */
    val sheetOpen: Boolean = false,
    val editingId: String? = null,
    val working: Boolean = false,
    val problem: OwnAgentProblem? = null,
    val failure: Failure? = null,
    /** The last check of [editingId] and what it found. */
    val checked: OwnAgentCheckResult? = null,
    /** The last install of [editingId]: true ended well, false did not. */
    val installed: Boolean? = null,
    val checking: Boolean = false,
    val installing: Boolean = false,
)

public interface OwnAgentActions {
    public fun addOwnAgent()

    public fun editOwnAgent(id: String)

    public fun closeOwnAgent()

    public fun editedOwnAgent()

    /** Saves; when the sheet was for a new agent it then stays open on the saved one, so Check and Install follow. */
    public fun saveOwnAgent(name: String, start: String, install: String, env: List<Pair<String, String>>)

    public fun checkOwnAgent()

    public fun installOwnAgent()

    public fun removeOwnAgent()
}

/** One operation at a time; nothing navigates. */
public class OwnAgentsPresenter(
    private val scope: CoroutineScope,
    private val port: PhoneOwnAgents?,
    private val refreshPhone: suspend () -> Unit,
) : OwnAgentActions {
    private val local = MutableStateFlow(OwnAgentsUi(available = port != null))

    public val state: StateFlow<OwnAgentsUi> =
        combine(local, port?.state ?: flowOf(persistentListOf())) { own, agents -> own.copy(agents = agents) }
            .stateIn(scope, SharingStarted.Eagerly, local.value.copy(agents = port?.state?.value ?: persistentListOf()))

    override fun addOwnAgent() {
        if (port == null || local.value.working) return
        local.update { OwnAgentsUi(available = true, sheetOpen = true) }
    }

    override fun editOwnAgent(id: String) {
        if (port == null || local.value.working) return
        local.update { OwnAgentsUi(available = true, sheetOpen = true, editingId = id) }
    }

    override fun closeOwnAgent() {
        if (local.value.working) return
        local.update { OwnAgentsUi(available = it.available) }
    }

    override fun editedOwnAgent() {
        val now = local.value
        if (now.problem != null || now.failure != null || now.checked != null)
            local.update { it.copy(problem = null, failure = null, checked = null) }
    }

    private fun step(
        mark: (OwnAgentsUi) -> OwnAgentsUi,
        unmark: (OwnAgentsUi) -> OwnAgentsUi,
        work: suspend () -> Unit,
    ) {
        if (port == null || local.value.working) return
        local.update { mark(it.copy(working = true, failure = null, problem = null)) }
        scope.launch {
            try {
                work()
            } finally {
                local.update { unmark(it.copy(working = false)) }
            }
        }
    }

    override fun saveOwnAgent(name: String, start: String, install: String, env: List<Pair<String, String>>) {
        val p = port ?: return
        val id = local.value.editingId
        step({ it }, { it }) {
            when (val saved = p.save(id, name, start, install, env)) {
                is Outcome.Failed -> local.update { it.copy(failure = saved.failure) }
                is Outcome.Ok -> when (val answer = saved.value) {
                    is OwnAgentSave.Refused -> local.update { it.copy(problem = answer.problem) }
                    is OwnAgentSave.Saved -> {
                        local.update { it.copy(editingId = answer.id, checked = null, installed = null) }
                        refreshPhone()
                    }
                }
            }
        }
    }

    override fun checkOwnAgent() {
        val p = port ?: return
        val id = local.value.editingId ?: return
        step({ it.copy(checking = true, checked = null) }, { it.copy(checking = false) }) {
            when (val result = p.check(id)) {
                is Outcome.Failed -> local.update { it.copy(failure = result.failure) }
                is Outcome.Ok -> local.update { it.copy(checked = result.value) }
            }
        }
    }

    override fun installOwnAgent() {
        val p = port ?: return
        val id = local.value.editingId ?: return
        step({ it.copy(installing = true, installed = null) }, { it.copy(installing = false) }) {
            when (val result = p.install(id)) {
                is Outcome.Failed -> local.update { it.copy(failure = result.failure) }
                is Outcome.Ok -> local.update { it.copy(installed = result.value) }
            }
        }
    }

    override fun removeOwnAgent() {
        val p = port ?: return
        val id = local.value.editingId ?: return
        step({ it }, { it }) {
            when (val result = p.remove(id)) {
                is Outcome.Failed -> local.update { it.copy(failure = result.failure) }
                is Outcome.Ok -> {
                    local.update { OwnAgentsUi(available = true) }
                    refreshPhone()
                }
            }
        }
    }
}
