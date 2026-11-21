package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.AppLog
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
    public suspend fun hasRoom(id: AgentId): Boolean
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
    private val log = AppLog.tagged("AgentUpdateChoice")
    private val state = MutableStateFlow<AgentUpdateOffer?>(null)
    override val offer: StateFlow<AgentUpdateOffer?> = state.asStateFlow()
    private var sharedConsent = emptySet<Pair<AgentId, String>>()
    private var reviewing = false
    private var sharedAttempt = emptyList<Pair<AgentId, String?>>()

    /** What the last answered sheet said about the network, for exactly the agents it updates: id to version. */
    @kotlin.concurrent.Volatile private var mobileYes = emptyMap<AgentId, String>()

    /**
     * The inventory runs scripts in the phone's Linux and can take seconds. It is read under its own lock, never the
     * command lane's, so a person's answer is not queued behind a timer's read; only the result is applied in the lane.
     */
    private val reads = Mutex()

    private fun apply(fresh: AgentUpdateInventory): AgentUpdateOffer? {
        inventory = fresh
        state.value = decisions.offer(inventory, reviewing)
        val offered = state.value
        sharedConsent =
            if (allowed(AgentId.OPENCODE)) sharedConsent.intersect(offered?.sharedPins.orEmpty()) else emptySet()
        return offered
    }

    public suspend fun refresh() {
        val fresh = reads.withLock { host.inventory() }
        lane.withLock {
            val snapshot = apply(fresh)
            val pending = snapshot?.items.orEmpty().filter { it.choice == AgentUpdateChoice.Remove }
            if (pending.isNotEmpty() && !host.busy()) {
                // Only a confirmed decide can write Remove. Revalidate the disk row and consume before dispatch.
                host.removeChosen(pending, decisions)
                apply(reads.withLock { host.inventory() })
            }
        }
    }

    override suspend fun review(raise: Boolean): Outcome<Unit> {
        val fresh = reads.withLock { host.inventory() }
        return lane.withLock {
            if (raise) reviewing = true
            apply(fresh)
            Unit.ok()
        }
    }

    override suspend fun notNow(): Outcome<Unit> = lane.withLock { save(emptyList()) }

    override fun removalFacts(id: AgentId): AgentRemovalFacts =
        AgentRemovalFacts(state.value?.items?.firstOrNull { it.id == id }?.installedBytes, true, true)

    override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>): Outcome<AgentUpdateStarted> = lane.withLock {
        val presented = state.value?.items.orEmpty().associateBy { it.id }
        // The answer is checked against the picture the sheet shows (the open sheet re-reads it by itself every 30 s):
        // reading the phone's versions again here held the "Update 4 agents" button spinning for 6.4 s (F14).
        val snapshot = apply(inventory) ?: return@withLock stale()
        val selected = snapshot.items.filter { it.id in choices }.map { it.copy(choice = choices[it.id]) }
        val staleRow = selected.any { !valid(it) || presented[it.id]?.fromVersion != it.fromVersion }
        if (selected.size != choices.size || staleRow) return@withLock stale()
        if (!host.fits(snapshot, selected)) return@withLock Failure.Media(MediaProblem.DownloadNoSpace).failed()
        // The yes on a sheet that said mobile data and showed the size is the consent for exactly these updates.
        val yes =
            if (snapshot.network == AgentUpdateNetwork.Metered) {
                selected.filter { it.choice == AgentUpdateChoice.Update }.associate { it.id to it.toVersion }
            } else {
                emptyMap()
            }
        val saved = save(selected, yes)
        if (saved is Outcome.Failed) return@withLock saved
        if (selected.any { it.choice != AgentUpdateChoice.Keep } && host.busy()) return@withLock busy()
        val removals = selected.filter { it.choice == AgentUpdateChoice.Remove }
        val refused = host.removeChosen(removals, decisions)
        // Removals change the disk: that one picture is read again (it is the only read an answer makes).
        if (removals.isNotEmpty()) apply(reads.withLock { host.inventory() })
        AgentUpdateStarted(
            selected.ids(AgentUpdateChoice.Update), removals.map { it.id }.filterNot { it in refused },
            selected.ids(AgentUpdateChoice.Keep), refused,
        ).ok()
    }

    private suspend fun save(
        items: List<AgentUpdateItem>,
        yes: Map<AgentId, String> = emptyMap(),
    ): Outcome<Unit> = decisions.save(items).also {
        if (it is Outcome.Ok) {
            // Set before the offer changes, so the look that the change wakes already sees the yes.
            mobileYes = yes
            reviewing = false
            sharedConsent = emptySet()
            sharedAttempt = emptyList()
            state.value = decisions.offer(inventory, reviewing)
        }
    }

    public fun allowed(id: AgentId): Boolean = state.value?.let { snapshot ->
        snapshot.answered && snapshot.items.any { it.id == id && it.choice == AgentUpdateChoice.Update }
    } == true

    /** True while [id]'s update was chosen on a sheet that said mobile data and has not started yet (M1-C). */
    public fun mobileYes(id: AgentId): Boolean {
        val row = state.value?.items?.firstOrNull { it.id == id } ?: return false
        return row.choice == AgentUpdateChoice.Update && mobileYes[id] == row.toVersion
    }

    /** [id]'s install started: its sheet yes is spent. */
    public fun spendMobileYes(id: AgentId) {
        mobileYes = mobileYes - id
    }

    /** OpenCode waits for the shared parts (Node.js, the helper) that the sheet's offer still lists. */
    public val openCodeNeedsShared: Boolean
        get() = state.value?.items.orEmpty().any { it.neededWhen != null }

    public suspend fun startIfChosen(id: AgentId, start: suspend () -> Boolean): Boolean = lane.withLock {
        val refusal = startRefusal(allowed(id), id == AgentId.OPENCODE && openCodeNeedsShared) { host.busy() }
        val started = refusal == null && start()
        // Says which condition refused ("busy" alone hid it on a device): ids and the reason, nothing else.
        val said = refusal ?: StartRefusal.StartRefused.takeIf { !started }
        if (said != null) log.i { "start of ${id.raw} not made: $said" }
        started
    }

    /** A shared-only job can run without touching an agent payload. Agent jobs already bring these parts. */
    public suspend fun startShared(
        meteredAllowed: Boolean,
        alongsideAgents: Boolean = false,
        start: suspend () -> Boolean,
    ): Boolean = lane.withLock {
        val current = state.value ?: return@withLock false
        val targets = current.items.filter { it.neededWhen != null }.map { it.id to it.fromVersion }
        if (targets.isEmpty() || targets == sharedAttempt) return@withLock false
        val pins = current.sharedPins
        if (alongsideAgents && meteredAllowed && allowed(AgentId.OPENCODE)) sharedConsent = pins
        val approved = sharedConsent.containsAll(pins)
        val eligible = current.sharedEligible(approved, meteredAllowed && !alongsideAgents)
        (eligible && !host.busy() && start()).also { started ->
            if (started) {
                sharedAttempt = targets
                sharedConsent = emptySet()
            }
        }
    }
}

private fun AgentUpdateOffer.sharedEligible(approved: Boolean, helperConsent: Boolean): Boolean {
    if (network != AgentUpdateNetwork.Unmetered && !approved && !helperConsent) return false
    val updates = items.filter { it.choice == AgentUpdateChoice.Update }
    val needed = neededGoesAhead || (updates.any { it.id == AgentId.OPENCODE } &&
        items.any { it.neededWhen == AgentNeedRule.WithAnyUpdate })
    return needed && (approved || updates.none { it.id != AgentId.OPENCODE })
}

private val AgentUpdateOffer.sharedPins: Set<Pair<AgentId, String>>
    get() = items.filter { it.neededWhen != null }.map { it.id to it.toVersion }.toSet()

/** Why an update did not start when it was asked to; the first condition that says no. */
internal enum class StartRefusal { NotChosen, NeedsSharedParts, WorkRunning, StartRefused }

internal suspend fun startRefusal(allowed: Boolean, needsShared: Boolean, busy: suspend () -> Boolean): StartRefusal? =
    when {
        !allowed -> StartRefusal.NotChosen
        needsShared -> StartRefusal.NeedsSharedParts
        busy() -> StartRefusal.WorkRunning
        else -> null
    }

private class ChoiceDecisions(private val appKey: String, private val prefs: AgentPrefs) {
    fun offer(inventory: AgentUpdateInventory, reviewing: Boolean): AgentUpdateOffer? {
        val answered = prefs.string(ANSWERED) == appKey
        return inventory.offer(appKey, apply(inventory.items, answered), reviewing, answered)
    }

    private fun apply(rows: List<AgentUpdateItem>, answered: Boolean): List<AgentUpdateItem> {
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

private suspend fun AgentChoiceHost.fits(offer: AgentUpdateOffer, selected: List<AgentUpdateItem>): Boolean {
    val free = offer.freeBytes ?: return true
    val updates = selected.filter { it.choice == AgentUpdateChoice.Update }
    val needed = offer.items.filter { it.neededWhen == AgentNeedRule.Always ||
        (it.neededWhen == AgentNeedRule.WithAnyUpdate && updates.isNotEmpty()) }
    return updates.isEmpty() || ((updates + needed).sumOf { it.downloadBytes ?: 0L } <= free &&
        updates.all { hasRoom(it.id) })
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
        val result = when (consumed) {
            is Outcome.Ok -> this@removeChosen.remove(item.id)
            is Outcome.Failed -> consumed
        }
        val why = when (val value = (result as? Outcome.Ok)?.value) {
            is RemovalResult.Removed -> null
            is RemovalResult.Refused -> value.reason
            else -> RemovalRefusal.Failed
        }
        if (why != null) put(item.id, why)
    }
}
