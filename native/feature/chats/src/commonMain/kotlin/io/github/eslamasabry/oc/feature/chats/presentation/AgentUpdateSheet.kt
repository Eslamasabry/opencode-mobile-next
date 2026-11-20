package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.label
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentKeepBlock
import io.github.eslamasabry.oc.engine.agents.install.AgentRemovalFacts
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStarted
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One agent the person decides about: its versions, the choices it allows and the one chosen now. */
public data class AgentUpdateRowUi(
    val id: AgentId,
    val name: String,
    val from: String?,
    val to: String,
    val choice: AgentUpdateChoice,
    /** Update first; Keep only when the old version still runs; Remove only where the app can remove it. */
    val choices: ImmutableList<AgentUpdateChoice>,
    /** Why Keep is not offered (its own words); null when Keep is offered. */
    val keepBlock: AgentKeepBlock?,
    val downloadBytes: Long?,
    val installedBytes: Long?,
)

/** A part the updates need (the agent helper, Node.js): no choice, only what it is and its size. */
public data class AgentNeededRowUi(
    val id: AgentId,
    val name: String,
    val from: String?,
    val to: String,
    val downloadBytes: Long?,
)

/** The confirm before a removal: the agent, what it frees, and whether its accounts and conversations stay. */
public data class AgentRemovalAskUi(
    val id: AgentId,
    val name: String,
    val freesBytes: Long?,
    val keepsAccountsAndConversations: Boolean,
)

/** An agent the person chose to remove that was not removed, and why (the Agents page's own words say it). */
public data class AgentRefusalUi(val name: String, val why: RemovalRefusal)

/** The room the chosen updates need and the room the phone has, when the first is larger. */
public data class AgentUpdateShortSpace(val neededBytes: Long, val freeBytes: Long)

/** "Updates for your agents": what the sheet shows (no words: the screen owns them). */
public data class AgentUpdateSheetUi(
    val visible: Boolean = false,
    val optional: ImmutableList<AgentUpdateRowUi> = persistentListOf(),
    /** The Needed parts shown now: Always ones, and WithAnyUpdate ones while at least one agent is set to Update. */
    val needed: ImmutableList<AgentNeededRowUi> = persistentListOf(),
    /** Which sentence says why the Needed parts come: they come with the updates, or they must move regardless. */
    val neededWhy: AgentNeedRule = AgentNeedRule.WithAnyUpdate,
    val downloadBytes: Long = 0,
    /** False when a chosen part's size is not known: the total is then "at least". */
    val downloadKnown: Boolean = true,
    val freesBytes: Long = 0,
    val freesKnown: Boolean = true,
    val short: AgentUpdateShortSpace? = null,
    /** Free space where agents install, when known: a refusal for room says it with the download's size. */
    val freeBytes: Long? = null,
    val network: AgentUpdateNetwork = AgentUpdateNetwork.Unmetered,
    val updating: Int = 0,
    val removing: Int = 0,
    /** The removal confirm that is open now; nothing is decided until every removal is confirmed. */
    val asking: AgentRemovalAskUi? = null,
    val working: Boolean = false,
    val failure: Failure? = null,
    /** After the answer: the removals that did not happen, each with its reason; the sheet shows only these then. */
    val refused: ImmutableList<AgentRefusalUi> = persistentListOf(),
)

/** Everything Home shows about the agents' update choice. */
public data class AgentUpdateHomeUi(
    val sheet: AgentUpdateSheetUi = AgentUpdateSheetUi(),
    /** Home's downloads line says "Agent updates ready · Review": an offer waits for its answer. */
    val pending: Boolean = false,
)

/** The sheet's pure rules: which choices an agent offers, what is chosen, what shows, and the totals. */
public object AgentUpdateSheetRules {
    /** Update always; Keep when the old version still runs; Remove where the app can remove it. */
    public fun choicesFor(item: AgentUpdateItem): List<AgentUpdateChoice> = buildList {
        add(AgentUpdateChoice.Update)
        if (item.keepAllowed) add(AgentUpdateChoice.Keep)
        if (item.removeAllowed && item.group == AgentUpdateGroup.Optional) add(AgentUpdateChoice.Remove)
    }

    /** The person's pick, else the engine's remembered decision, else Update; a choice not allowed is Update. */
    public fun effective(item: AgentUpdateItem, picked: AgentUpdateChoice?): AgentUpdateChoice {
        val choice = picked ?: item.choice ?: AgentUpdateChoice.Update
        return if (choice in choicesFor(item)) choice else AgentUpdateChoice.Update
    }

    /** The answer for decide(): every Optional item with its choice. Needed items carry no choice. */
    public fun decision(
        offer: AgentUpdateOffer,
        picked: Map<AgentId, AgentUpdateChoice>,
    ): Map<AgentId, AgentUpdateChoice> =
        offer.items
            .filter { it.group == AgentUpdateGroup.Optional }
            .associate { it.id to effective(it, picked[it.id]) }

    /** The agents set to Remove, in the sheet's order: each is confirmed before anything is decided. */
    public fun removals(offer: AgentUpdateOffer, picked: Map<AgentId, AgentUpdateChoice>): List<AgentId> =
        decision(offer, picked).filterValues { it == AgentUpdateChoice.Remove }.keys.toList()

    /**
     * Home's Review line shows while an offer waits for a real decision: before any answer, and after Not now (the
     * engine counts Not now as answered, with no choice kept for an Optional agent). On 10110 the line went with
     * `answered`, so after Not now it vanished and nothing led back to the sheet.
     */
    public fun pending(offer: AgentUpdateOffer?): Boolean =
        offer != null &&
            (!offer.answered || offer.items.any { it.group == AgentUpdateGroup.Optional && it.choice == null })

    /** What the sheet shows for [offer] with the person's [picked] answers (the confirm and progress come after). */
    public fun sheet(
        offer: AgentUpdateOffer?,
        picked: Map<AgentId, AgentUpdateChoice>,
        visible: Boolean,
    ): AgentUpdateSheetUi {
        offer ?: return AgentUpdateSheetUi()
        val optional =
            offer.items
                .filter { it.group == AgentUpdateGroup.Optional }
                .map { item ->
                    AgentUpdateRowUi(
                        id = item.id,
                        name = item.name,
                        from = item.fromVersion,
                        to = item.toVersion,
                        choice = effective(item, picked[item.id]),
                        choices = choicesFor(item).toImmutableList(),
                        keepBlock = keepBlock(item),
                        downloadBytes = item.downloadBytes,
                        installedBytes = item.installedBytes,
                    )
                }
        val anyUpdate = optional.any { it.choice == AgentUpdateChoice.Update }
        val neededItems =
            offer.items.filter { it.group == AgentUpdateGroup.Needed }.filter {
                // A Needed part without a rule comes with the updates, as every one does today (contract §4).
                it.neededWhen == AgentNeedRule.Always || anyUpdate
            }
        val needed =
            neededItems.map { AgentNeededRowUi(it.id, it.name, it.fromVersion, it.toVersion, it.downloadBytes) }
        val updates = optional.filter { it.choice == AgentUpdateChoice.Update }
        val removes = optional.filter { it.choice == AgentUpdateChoice.Remove }
        val downloads = updates.map { it.downloadBytes } + needed.map { it.downloadBytes }
        val download = downloads.sumOf { it ?: 0L }
        val free = offer.freeBytes
        return AgentUpdateSheetUi(
            visible = visible,
            optional = optional.toImmutableList(),
            needed = needed.toImmutableList(),
            neededWhy =
                if (neededItems.any { it.neededWhen != AgentNeedRule.Always }) AgentNeedRule.WithAnyUpdate
                else AgentNeedRule.Always,
            downloadBytes = download,
            downloadKnown = downloads.all { it != null },
            freesBytes = removes.sumOf { it.installedBytes ?: 0L },
            freesKnown = removes.all { it.installedBytes != null },
            short = if (free != null && download > free) AgentUpdateShortSpace(download, free) else null,
            freeBytes = free,
            network = offer.network,
            updating = updates.size,
            removing = removes.size,
        )
    }

    /**
     * The removals asked for that did not happen, each with the engine's reason; one that is neither removed nor
     * refused is told as Failed, so a removal never goes silently missing.
     */
    public fun refused(
        offer: AgentUpdateOffer,
        decision: Map<AgentId, AgentUpdateChoice>,
        started: AgentUpdateStarted,
    ): List<AgentRefusalUi> =
        decision
            .filterValues { it == AgentUpdateChoice.Remove }
            .keys
            .filter { it !in started.removing }
            .map { id ->
                val name = offer.items.firstOrNull { it.id == id }?.name ?: id.raw
                AgentRefusalUi(name, started.refused[id] ?: RemovalRefusal.Failed)
            }

    /** A refusal for room is old once the engine reads a different free space (the person freed some, or used some). */
    public fun roomRefusalOld(failure: Failure?, offer: AgentUpdateOffer?, failedAtFree: Long?): Boolean =
        (failure as? Failure.Media)?.problem == io.github.eslamasabry.oc.core.common.MediaProblem.DownloadNoSpace &&
            offer != null &&
            offer.freeBytes != failedAtFree

    /** Why Keep is not offered; null when it is. */
    private fun keepBlock(item: AgentUpdateItem): AgentKeepBlock? =
        if (item.keepAllowed) null else item.notKeptReason ?: AgentKeepBlock.NotSupportedAnyMore
}

/**
 * The sheet's state and its answers, for Home's ViewModel: the person's picks stay here until the answer; the engine
 * owns the offer, the decision and whether the sheet is due. [choices] null: no offer ever (tests, previews, desktop).
 */
public class AgentUpdateSheetModel(private val choices: AgentUpdateChoices?, private val scope: CoroutineScope) {
    private data class Local(
        val picked: Map<AgentId, AgentUpdateChoice> = emptyMap(),
        /** Removals still to confirm, the open one first. */
        val toConfirm: List<AgentRemovalAskUi> = emptyList(),
        val working: Boolean = false,
        val failure: Failure? = null,
        /** The free space the engine reported when it refused for room: a fresh reading makes that refusal old. */
        val failedAtFree: Long? = null,
        /** The offer answered here: the sheet stays closed for it until a new offer (a Review) comes. */
        val closedAt: AgentUpdateOffer? = null,
        /** The removals the engine refused in the last answer: told on the sheet until the person closes it. */
        val refused: List<AgentRefusalUi> = emptyList(),
    )

    private val local = MutableStateFlow(Local())
    private val offers: StateFlow<AgentUpdateOffer?> = choices?.offer ?: MutableStateFlow(null)

    private val chain = AgentUpdateChainLog()
    private val bound = choices != null && choices !== NoAgentUpdateChoices

    public val ui: Flow<AgentUpdateHomeUi> =
        combine(offers, local) { offer, now -> build(offer, now).also { chain.told(offer, it, bound) } }

    public fun current(): AgentUpdateHomeUi = build(offers.value, local.value)

    private fun build(offer: AgentUpdateOffer?, now: Local): AgentUpdateHomeUi {
        // Closed for exactly the offer it was answered on: a Review (here, or from the Agents page) brings a new offer
        // and the sheet opens again, even when this page outlived the answer.
        val due = offer != null && offer.sheetDue && !sameQuestion(offer, now.closedAt)
        val sheet =
            AgentUpdateSheetRules.sheet(offer, now.picked, due || now.refused.isNotEmpty())
                .copy(
                    asking = now.toConfirm.firstOrNull(),
                    working = now.working,
                    failure =
                        now.failure.takeUnless { AgentUpdateSheetRules.roomRefusalOld(it, offer, now.failedAtFree) },
                )
        return AgentUpdateHomeUi(
            sheet = sheet.copy(visible = due || now.refused.isNotEmpty(), refused = now.refused.toImmutableList()),
            pending = AgentUpdateSheetRules.pending(offer),
        )
    }

    public fun choose(id: AgentId, choice: AgentUpdateChoice) {
        local.update { it.copy(picked = it.picked + (id to choice), failure = null) }
    }

    /** The sheet's button: every removal is confirmed first, one by one; then the decision starts. */
    public fun applyChoices() {
        val offer = offers.value ?: return
        val now = local.value
        if (now.working || choices == null) return
        val asks =
            AgentUpdateSheetRules.removals(offer, now.picked).map { id ->
                val item = offer.items.first { it.id == id }
                val facts = choices.removalFacts(id)
                AgentRemovalAskUi(
                    id = id,
                    name = item.name,
                    freesBytes = facts.freesBytes ?: item.installedBytes,
                    keepsAccountsAndConversations = facts.keepsSignIn && facts.keepsConversations,
                )
            }
        if (asks.isEmpty()) start() else local.update { it.copy(toConfirm = asks, failure = null) }
    }

    /** The open removal is confirmed: the next one is asked, or, when none is left, the decision starts. */
    public fun confirmRemoval() {
        val rest = local.value.toConfirm.drop(1)
        local.update { it.copy(toConfirm = rest) }
        if (rest.isEmpty()) start()
    }

    /** A removal not confirmed: nothing is decided; the sheet stays as it was. */
    public fun cancelRemoval() {
        local.update { it.copy(toConfirm = emptyList()) }
    }

    private fun start() {
        val engine = choices ?: return
        val offer = offers.value ?: return
        val decision = AgentUpdateSheetRules.decision(offer, local.value.picked)
        log.i { "update sheet: decided " + decision.entries.joinToString { "${it.key.raw}=${it.value}" } }
        local.update { it.copy(working = true, failure = null) }
        scope.launch {
            val outcome = engine.decide(decision)
            AgentUpdateChainLog.log.i { "update sheet: " + AgentUpdateChainLog.outcome(outcome) }
            when (outcome) {
                is Outcome.Ok -> {
                    val refused = AgentUpdateSheetRules.refused(offer, decision, outcome.value)
                    local.update { it.copy(working = false, closedAt = offer, refused = refused) }
                }
                is Outcome.Failed ->
                    // Busy is waiting, not failing: the choice is kept and applied when the phone is free.
                    if ((outcome.failure as? Failure.Runtime)?.problem == RuntimeProblem.SlotBusy) {
                        local.update { it.copy(working = false, closedAt = offer) }
                    } else {
                        local.update {
                            it.copy(working = false, failure = outcome.failure, failedAtFree = offer.freeBytes)
                        }
                    }
            }
        }
    }

    /** Not now (and closing the sheet): nothing downloads until Review. */
    public fun notNow() {
        val offer = offers.value
        if (choices == null || offer == null || local.value.working) return
        local.update { it.copy(closedAt = offer, toConfirm = emptyList(), failure = null) }
        log.i { "update sheet: not now" }
        scope.launch { choices.notNow() }
    }

    /** Home's Review: the sheet shows again for the current offer. */
    /**
     * Home's Review: the sheet shows again for the current offer. [reread] (the open sheet's timer): only the engine's
     * reading is asked again (free space, versions), so the room line and the button follow by themselves; a refusal
     * on screen stays until the reading changes.
     */
    public fun review(reread: Boolean = false) {
        val engine = choices ?: return
        if (!reread) local.update { it.copy(closedAt = null, failure = null) }
        // The timer's reading never raises the sheet; only a person's Review does.
        scope.launch { engine.review(raise = !reread) }
    }

    private companion object {
        val log = AgentUpdateChainLog.log
    }

    /** The refused removals were read: the sheet closes. */
    public fun closeResult() {
        local.update { it.copy(refused = emptyList()) }
    }
}

/** What the person can do on the sheet and on Home's line about it (intents; no-ops where nothing is bound). */
public interface AgentUpdateActions {
    /** [choice] for the agent [id] (Update, Keep or Remove). */
    public fun chooseAgentUpdate(id: AgentId, choice: AgentUpdateChoice) {}

    /** The sheet's button ("Update 2 and remove 1"): asks before each removal, then starts the chosen work. */
    public fun proceedAgentUpdates() {}

    /** The removal confirm's Remove. */
    public fun confirmAgentRemoval() {}

    /** The removal confirm's Cancel: nothing is decided. */
    public fun cancelAgentRemoval() {}

    /** The sheet's Not now (and closing it): nothing downloads until Review. */
    public fun agentUpdatesNotNow() {}

    /** Home's "Agent updates ready · Review": the sheet shows again. */
    public fun reviewAgentUpdates() {}

    /** "Remove it in Agents" (on the sheet, and after a removal that did not happen): the Agents page. */
    public fun openAgentsFromUpdates() {}

    /** "Update them in Linux › Packages": the Linux Packages page. */
    public fun openLinuxPackages() {}

    /** Done on the sheet's result (the removals that did not happen): the sheet closes. */
    public fun closeAgentUpdateResult() {}
}

/** No sheet: every answer does nothing. */
public object NoAgentUpdateActions : AgentUpdateActions

/** No engine bound (desktop, previews): never an offer, so no sheet and no Home line; answers do nothing. */
public object NoAgentUpdateChoices : AgentUpdateChoices {
    override val offer: StateFlow<AgentUpdateOffer?> = MutableStateFlow(null)

    override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>): Outcome<AgentUpdateStarted> =
        Outcome.Failed(Failure.Unavailable(FeatureId("agentUpdateChoices")))

    override suspend fun notNow(): Outcome<Unit> = Outcome.Ok(Unit)

    override fun removalFacts(id: AgentId): AgentRemovalFacts =
        AgentRemovalFacts(null, keepsSignIn = true, keepsConversations = true)

    override suspend fun review(raise: Boolean): Outcome<Unit> = Outcome.Ok(Unit)
}

/**
 * The chain from the engine's offer to the sheet, one AppLog line per step and per change, so a look on a phone says
 * where it stops: the offer Home reads, the Review row, the sheet opening. Ids, counts and flags only; no secrets.
 */
internal class AgentUpdateChainLog {
    private var offerLine: String? = null
    private var rowLine: String? = null
    private var open = false

    fun told(offer: AgentUpdateOffer?, home: AgentUpdateHomeUi, bound: Boolean) {
        val line =
            if (offer == null) "home: update offer none (engine bound=$bound)"
            else "home: update offer rows=${offer.items.size} due=${offer.sheetDue} answered=${offer.answered}"
        if (line != offerLine) {
            offerLine = line
            log.i { line }
        }
        // The Review line's decision, said on every launch and on every change.
        val row =
            when {
                home.pending && !home.sheet.visible -> "home: review row shown"
                home.sheet.visible -> "home: review row hidden (sheet open)"
                offer == null -> "home: review row hidden (no offer)"
                else -> "home: review row hidden (every agent decided)"
            }
        if (row != rowLine) {
            rowLine = row
            log.i { row }
        }
        if (home.sheet.visible && !open) log.i { "update sheet: opened" }
        open = home.sheet.visible
    }

    companion object {
        val log = AppLog.tagged("AgentUpdates")

        /** What decide() answered, in one line: what started, or the kind of refusal (no text from the failure). */
        fun outcome(outcome: Outcome<AgentUpdateStarted>): String =
            when (outcome) {
                is Outcome.Ok ->
                    "started updating=${outcome.value.updating.size} removing=${outcome.value.removing.size} " +
                        "kept=${outcome.value.kept.size} refused=${outcome.value.refused.size}"
                is Outcome.Failed ->
                    "refused " + outcome.failure.label()
            }
    }
}

/**
 * Closed for the question answered: the same build, agents, versions and answer state; only room and network may
 * move (a Review or an answer still makes a different offer and opens it again).
 */
private fun sameQuestion(offer: AgentUpdateOffer, closed: AgentUpdateOffer?): Boolean =
    closed != null && closed.appUpdateKey == offer.appUpdateKey && closed.sheetDue == offer.sheetDue &&
        closed.answered == offer.answered &&
        closed.items.map { Triple(it.id, it.fromVersion, it.toVersion) } ==
        offer.items.map { Triple(it.id, it.fromVersion, it.toVersion) }
