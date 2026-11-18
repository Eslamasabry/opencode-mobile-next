package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class AgentUpdateInventory(
    val items: List<AgentUpdateItem>,
    val network: AgentUpdateNetwork,
    val freeBytes: Long?,
)

public interface AgentChoiceHost {
    public suspend fun inventory(): AgentUpdateInventory
    public suspend fun busy(): Boolean
    public suspend fun remove(id: AgentId): Outcome<RemovalResult>
}

/** One command lane also serializes consent changes with admission to the existing installer. */
public class AgentUpdateChoiceEngine(
    private val appKey: String,
    prefs: AgentPrefs,
    private val host: AgentChoiceHost,
) : AgentUpdateChoices {
    private val decisions = ChoiceDecisions(appKey, prefs)
    private var inventory = AgentUpdateInventory(emptyList(), AgentUpdateNetwork.Unknown, null)
    private val lane = Mutex()
    private val state = MutableStateFlow<AgentUpdateOffer?>(null)
    override val offer: StateFlow<AgentUpdateOffer?> = state.asStateFlow()
    private var reviewing = false

    public suspend fun refresh(): Unit = lane.withLock {
        val snapshot = read()
        val pending = snapshot?.items.orEmpty().filter { it.choice == AgentUpdateChoice.Remove }
        if (pending.isNotEmpty() && !host.busy()) {
            // Only a confirmed decide can write Remove. Revalidate the disk row and consume before dispatch.
            host.removeChosen(pending, decisions)
            read()
        }
    }

    private suspend fun read(): AgentUpdateOffer? {
        inventory = host.inventory()
        return publish(inventory)
    }

    private fun publish(inventory: AgentUpdateInventory): AgentUpdateOffer? {
        val answered = decisions.answered()
        val items = decisions.apply(inventory.items)
        state.value = inventory.offer(appKey, items, reviewing, answered)
        return state.value
    }

    override suspend fun review(): Outcome<Unit> = lane.withLock {
        reviewing = true
        read()
        Unit.ok()
    }

    override suspend fun notNow(): Outcome<Unit> = lane.withLock { save(emptyList()) }

    override fun removalFacts(id: AgentId): AgentRemovalFacts =
        AgentRemovalFacts(state.value?.items?.firstOrNull { it.id == id }?.installedBytes, true, true)

    override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>): Outcome<AgentUpdateStarted> = lane.withLock {
        val presented = state.value?.items.orEmpty().associateBy { it.id }
        val snapshot = read() ?: return@withLock stale()
        val selected = snapshot.items.filter { it.id in choices }.map { it.copy(choice = choices[it.id]) }
        if (selected.size != choices.size || selected.any { !valid(it) || presented[it.id]?.fromVersion != it.fromVersion }) return@withLock stale()
        if (!fits(snapshot, selected)) return@withLock Failure.Media(MediaProblem.DownloadNoSpace).failed()
        val saved = save(selected)
        if (saved is Outcome.Failed) return@withLock saved
        if (selected.any { it.choice != AgentUpdateChoice.Keep } && host.busy()) return@withLock busy()
        val removals = selected.filter { it.choice == AgentUpdateChoice.Remove }
        val refused = host.removeChosen(removals, decisions)
        if (removals.isNotEmpty()) read()
        AgentUpdateStarted(
            selected.ids(AgentUpdateChoice.Update), removals.map { it.id }.filterNot { it in refused },
            selected.ids(AgentUpdateChoice.Keep), refused,
        ).ok()
    }

    private suspend fun save(items: List<AgentUpdateItem>): Outcome<Unit> = decisions.save(items).also {
        if (it is Outcome.Ok) {
            reviewing = false
            publish(inventory)
        }
    }

    public fun allowed(id: AgentId): Boolean = state.value?.let { snapshot ->
        snapshot.answered && snapshot.items.any { it.id == id && it.choice == AgentUpdateChoice.Update }
    } == true

    public suspend fun startIfChosen(id: AgentId, start: suspend () -> Boolean): Boolean = lane.withLock {
        if (allowed(id) && !host.busy()) start() else false
    }

    /** A shared-only job can run without touching an agent payload. Agent jobs already bring these parts. */
    public suspend fun startShared(meteredAllowed: Boolean, start: suspend () -> Boolean): Boolean = lane.withLock {
        val current = state.value ?: return@withLock false
        val updates = current.items.filter { it.choice == AgentUpdateChoice.Update }
        val sharedOnly = current.neededGoesAhead || (updates.any { it.id == AgentId.OPENCODE } &&
            current.items.any { it.neededWhen == AgentNeedRule.WithAnyUpdate })
        val canDownload = current.network == AgentUpdateNetwork.Unmetered || meteredAllowed
        sharedOnly && updates.none { it.id != AgentId.OPENCODE } && canDownload && !host.busy() && start()
    }
}

private class ChoiceDecisions(private val appKey: String, private val prefs: AgentPrefs) {
    fun answered() = prefs.string(ANSWERED) == appKey

    fun apply(rows: List<AgentUpdateItem>): List<AgentUpdateItem> {
        val answered = answered()
        return rows.map { item ->
            val choice = AgentUpdateChoice.entries.firstOrNull {
                (answered || it == AgentUpdateChoice.Keep) && prefs.string(key(item.id)) == "${item.toVersion}:$it"
            }
            item.copy(choice = choice)
        }
    }

    suspend fun consume(id: AgentId) = prefs.commit { remove(key(id)) }

    suspend fun save(items: List<AgentUpdateItem>): Outcome<Unit> {
        val transient = prefs.keys(PREFIX).filterNot { prefs.string(it)?.endsWith(":Keep") == true }
        return prefs.commit {
            transient.forEach { remove(it) }
            items.forEach { putString(key(it.id), "${it.toVersion}:${it.choice}") }
            putString(ANSWERED, appKey)
        }
    }
}

private fun AgentUpdateInventory.offer(
    key: String, rows: List<AgentUpdateItem>, reviewing: Boolean, answered: Boolean,
): AgentUpdateOffer? {
    val always = rows.any { it.neededWhen == AgentNeedRule.Always }
    val optional = rows.filter { it.group == AgentUpdateGroup.Optional }
    val due = reviewing || (!answered && (always || optional.any { it.choice == null }))
    return if (rows.isEmpty() || (optional.isEmpty() && !always)) null else
        AgentUpdateOffer(key, rows, network, freeBytes, always, due, answered)
}

private fun valid(item: AgentUpdateItem): Boolean = item.group == AgentUpdateGroup.Optional && when (item.choice) {
    AgentUpdateChoice.Remove -> item.removeAllowed
    AgentUpdateChoice.Keep -> item.keepAllowed
    AgentUpdateChoice.Update -> true
    null -> false
}

private fun fits(offer: AgentUpdateOffer, selected: List<AgentUpdateItem>): Boolean {
    val free = offer.freeBytes ?: return true
    val updates = selected.filter { it.choice == AgentUpdateChoice.Update }
    val needed = offer.items.filter { it.neededWhen == AgentNeedRule.Always ||
        (it.neededWhen == AgentNeedRule.WithAnyUpdate && updates.isNotEmpty()) }
    return (updates + needed).sumOf { it.downloadBytes ?: 0L } <= free
}

private fun List<AgentUpdateItem>.ids(choice: AgentUpdateChoice) = filter { it.choice == choice }.map { it.id }
private fun key(id: AgentId): String = PREFIX + id.raw
private fun busy() = Failure.Runtime(RuntimeProblem.SlotBusy).failed()
private fun stale() = Failure.Runtime(RuntimeProblem.ConfirmationRequired).failed()

private const val ANSWERED = "oc.agentUpdateAnswered"
private const val PREFIX = "oc.agentUpdateChoice."

private suspend fun AgentChoiceHost.removeChosen(
    items: List<AgentUpdateItem>, decisions: ChoiceDecisions,
): Map<AgentId, RemovalRefusal> = buildMap {
    for (item in items) {
        val consumed = decisions.consume(item.id)
        val result = if (consumed is Outcome.Ok) remove(item.id) else consumed
        val why = when (val value = (result as? Outcome.Ok)?.value) {
            is RemovalResult.Removed -> null
            is RemovalResult.Refused -> value.reason
            else -> RemovalRefusal.Failed
        }
        if (why != null) put(item.id, why)
    }
}
