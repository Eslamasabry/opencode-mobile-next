package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentChoiceHost
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoiceEngine
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemoval
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemovalIssue
import io.github.eslamasabry.opencode_mobile.BuildConfig
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<AgentUpdateChoices>())
class PhoneUpdateChoices(
    app: Application,
    stores: PreviewStores,
    private val phones: AppPhoneAgents,
    private val dispatchers: AppDispatchers,
    private val runtime: BuiltinRuntime,
) : AgentUpdateChoices {
    private val disk = PhoneChoiceInventory(app, phones)
    private val host = object : AgentChoiceHost {
        override suspend fun inventory() = disk.read()
        override suspend fun busy() = phones.choiceInstallBusy() ||
            runtime.agentWorkBusy() || !runtime.mayUpdate()

        override suspend fun remove(id: AgentId): Outcome<RemovalResult> {
            return phones.removeWithInstallGate(id).map { result ->
                when (result) {
                    is PhoneAgentRemoval.Removed -> RemovalResult.Removed(result.freedBytes)
                    is PhoneAgentRemoval.Refused -> RemovalResult.Refused(refusal(result.issue))
                }
            }
        }
    }
    private val engine = AgentUpdateChoiceEngine(
        BuildConfig.VERSION_CODE.toString(), KvAgentPrefs(stores.prefs, stores.lanes.app), host,
    )
    override val offer = engine.offer
    override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>) = command { engine.decide(choices) }
    override suspend fun review() = command { engine.review() }
    override suspend fun notNow() = command { engine.notNow() }
    override fun removalFacts(id: AgentId) = engine.removalFacts(id)

    internal suspend fun refresh() = withContext(dispatchers.io) { engine.refresh() }
    internal fun allowed(id: AgentId): Boolean = engine.allowed(id)
    internal suspend fun startIfChosen(id: AgentId, start: suspend () -> Boolean): Boolean =
        withContext(dispatchers.io) { engine.startIfChosen(id, start) }

    internal suspend fun updateShared(meteredAllowed: Boolean): Boolean = withContext(dispatchers.io) {
        engine.startShared(meteredAllowed) {
            disk.sharedAnchor?.let { phones.startSharedUpdate(it) } == true
        }
    }

    private suspend fun <T> command(run: suspend () -> Outcome<T>): Outcome<T> = withContext(dispatchers.io) {
        when (val result = attempt({ Failure.StorageRefused() }) { run() }) {
            is Outcome.Failed -> result
            is Outcome.Ok -> result.value
        }
    }

    private fun refusal(issue: PhoneAgentRemovalIssue): RemovalRefusal = when (issue) {
        PhoneAgentRemovalIssue.Busy -> RemovalRefusal.Busy
        PhoneAgentRemovalIssue.Unsafe -> RemovalRefusal.Unsafe
        PhoneAgentRemovalIssue.Unconfirmed -> RemovalRefusal.Unconfirmed
        PhoneAgentRemovalIssue.Unavailable -> RemovalRefusal.Failed
    }
}
