package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.*
import io.github.eslamasabry.oc.engine.agents.probe.*
import io.github.eslamasabry.oc.feature.agents.hub.*
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Explicit read-only checks: no helper, sign-in or installer is started. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PhoneAgents>())
class AppPhoneAgents(
    private val application: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : PhoneAgents {
    private val mutable = MutableStateFlow(PhoneAgentsState(available = true))
    override val state: StateFlow<PhoneAgentsState> = mutable
    private val checking = Mutex()
    @Volatile private var checkedOwner: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null

    init {
        appScope.launch {
            combine(stores.profiles.activeId, stores.profiles.profiles) { active, saved ->
                    active to saved.any { it.id == active }
                }
                .collect { (active, exists) ->
                    if (checkedOwner != active || !exists)
                        mutable.value = PhoneAgentsState(available = true)
                }
        }
    }

    override suspend fun refresh(): Outcome<Unit> = checking.withLock {
        when (val warmed = stores.warm()) {
            is Outcome.Failed -> return@withLock warmed
            is Outcome.Ok -> Unit
        }
        val owner =
            stores.profiles.activeId.value
                ?: return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
        val linux = BuiltinLinux.get(application)
        val catalog =
            AgentCatalog.builtIn.agents.filter {
                it.recipe != null && it.availability != AgentAvailability.Hidden
            }
        val previous =
            if (checkedOwner == owner) mutable.value.agents.associateBy { it.id } else emptyMap()
        checkedOwner = owner
        val rows =
            catalog
                .map { PhoneAgentRow(it.id.raw, it.name, PhoneAgentStatus.Checking) }
                .toMutableList()
        mutable.value = PhoneAgentsState(available = true, agents = rows.toImmutableList())
        fun current() =
            stores.profiles.activeId.value == owner && stores.profiles.profile(owner) != null
        var probeFailure: Failure? = null
        for ((index, agent) in catalog.withIndex()) {
            if (!current()) {
                mutable.value = PhoneAgentsState(available = true)
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            }
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
            if (!current()) {
                mutable.value = PhoneAgentsState(available = true)
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            }
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
                            else -> previous[agent.id.raw]?.status ?: PhoneAgentStatus.Unavailable
                        }
                }
            rows[index] = rows[index].copy(status = status)
            mutable.value = PhoneAgentsState(available = true, agents = rows.toImmutableList())
        }
        probeFailure?.failed() ?: Unit.ok()
    }
}
