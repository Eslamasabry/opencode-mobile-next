package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentChoiceHost
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoiceEngine
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemoval
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRemovalIssue
import io.github.eslamasabry.opencode_mobile.BuildConfig
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import io.github.eslamasabry.opencode_mobile.SetupDiskSpace
import kotlinx.coroutines.withContext

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<AgentUpdateChoices>())
class PhoneUpdateChoices(
    private val app: Application,
    stores: PreviewStores,
    private val phones: AppPhoneAgents,
    private val dispatchers: AppDispatchers,
    private val runtime: BuiltinRuntime,
) : AgentUpdateChoices {
    private val disk = PhoneChoiceInventory(app, phones) { runtime.serverFacts.value.installedVersion }
    private val host = object : AgentChoiceHost {
        override suspend fun inventory() = disk.read()
        override suspend fun hasRoom(id: AgentId): Boolean = if (id == AgentId.OPENCODE) {
            val size = HomeDownloadSizes.OPEN_CODE_MB * BYTES_PER_MB
            app.filesDir.usableSpace >= SetupDiskSpace.requiredInstallBytes(size)
        } else phones.installSpace(id.raw)?.let { it.freeBytes >= it.neededBytes } != false

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

    /** True once one inventory has been read: before it, nothing is known to be unasked. */
    @Volatile private var inventoried = false

    internal suspend fun refresh() = withContext(dispatchers.io) { engine.refresh().also { inventoried = true } }
    private val holds = UnaskedHolds { offer.value }

    internal fun allowed(id: AgentId): Boolean = engine.allowed(id).also { if (!it) holds.unasked(id, inventoried) }
    internal suspend fun startIfChosen(id: AgentId, start: suspend () -> Boolean): Boolean =
        withContext(dispatchers.io) {
            // An agent with no offer row is neither held nor updated: its inventory is read again (the row, and so
            // the question, follows) and nothing downloads without a decision.
            if (holds.unasked(id, inventoried)) refresh()
            engine.startIfChosen(id, start)
        }

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

private const val BYTES_PER_MB = 1_000_000L

/** Says (once) that an agent has no row in the current offer: it is not updated, the caller reads again. */
private class UnaskedHolds(private val current: () -> AgentUpdateOffer?) {
    private val log = AppLog.tagged("UpdateChoices")
    private val logged = mutableSetOf<AgentId>()

    fun unasked(id: AgentId, inventoried: Boolean): Boolean {
        val missing = inventoried && current()?.items?.none { it.id == id } != false
        if (missing && synchronized(logged) { logged.add(id) }) {
            log.i { "hold has no offer row: ${id.raw}; the inventory is read again, nothing is updated unasked" }
        }
        return missing
    }
}
