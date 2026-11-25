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
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateInventory
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
    @Volatile private var lastInventory: AgentUpdateInventory? = null
    private val disk = PhoneChoiceInventory(app, phones) { runtime.serverFacts.value.installedVersion }
    private val host = object : AgentChoiceHost {
        // The ONE point every read of versions and sizes passes (the loop, the open sheet's 30 s and on-resume reads,
        // Home's and the Agents door's Review, decide): while an install job runs or the gate is held the scripts do
        // not run (they share the one installer slot with the install); the last picture stays, with only the room
        // re-read, a plain file-system figure that needs no slot.
        override suspend fun inventory(): AgentUpdateInventory {
            val last = lastInventory
            if (last != null && phones.choiceInstallBusy()) return last.copy(freeBytes = app.filesDir.usableSpace)
            return disk.read().also { lastInventory = it }
        }
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
    override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>, waitForWifi: Boolean) =
        command { engine.decide(choices, waitForWifi) }
    override suspend fun review(raise: Boolean) = command { engine.review(raise) }
    override suspend fun notNow() = command { engine.notNow() }
    override fun removalFacts(id: AgentId) = engine.removalFacts(id)

    /** True once one inventory has been read: before it, nothing is known to be unasked. */
    @Volatile private var inventoried = false

    internal suspend fun refresh() = withContext(dispatchers.io) {
        // The inventory runs scripts in the phone's Linux, which share ONE installer slot with every install: never
        // while an install job runs (the picture it holds stays), or a read at a component's start fails the job.
        if (!phones.choiceInstallBusy()) engine.refresh().also { inventoried = true }
    }
    private val holds = UnaskedHolds { offer.value }

    /** OpenCode's version at the launch path as the last inventory read it; null when not read or none. */
    internal val openCodeOnDisk: String? get() = disk.openCodeOnDisk

    internal fun allowed(id: AgentId): Boolean = engine.allowed(id).also { if (!it) holds.unasked(id, inventoried) }
    internal suspend fun startIfChosen(id: AgentId, start: suspend () -> Boolean): Boolean =
        withContext(dispatchers.io) {
            // An agent with no offer row is neither held nor updated: its inventory is read again (the row, and so
            // the question, follows) and nothing downloads without a decision.
            if (holds.unasked(id, inventoried)) refresh()
            engine.startIfChosen(id, start)
        }

    /** The yes on a sheet that said mobile data covers this agent's install (M1-C); spent when it starts. */
    internal val mobile get() = engine.mobile

    /** OpenCode's update waits for the shared parts (Node.js, the helper) the offer still lists. */
    internal val openCodeNeedsShared: Boolean get() = engine.openCodeNeedsShared

    internal suspend fun updateShared(meteredAllowed: Boolean, alongsideAgents: Boolean = false): Boolean =
        withContext(dispatchers.io) {
        engine.startShared(meteredAllowed, alongsideAgents) {
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
