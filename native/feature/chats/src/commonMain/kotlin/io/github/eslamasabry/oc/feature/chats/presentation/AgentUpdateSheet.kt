package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentKeepBlock
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
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
    val network: AgentUpdateNetwork = AgentUpdateNetwork.Unmetered,
    val updating: Int = 0,
    val removing: Int = 0,
    /** The removal confirm that is open now; nothing is decided until every removal is confirmed. */
    val asking: AgentRemovalAskUi? = null,
    val working: Boolean = false,
    val failure: Failure? = null,
)

/** Everything Home shows about the agents' update choice. */
public data class AgentUpdateHomeUi(
    val sheet: AgentUpdateSheetUi = AgentUpdateSheetUi(),
    /** Home's downloads line says "Agent updates ready · Review": an offer waits for its answer. */
    val pending: Boolean = false,
    /** Agents the person chose to remove that were not removed, by name (first one is told). */
    val notRemoved: ImmutableList<String> = persistentListOf(),
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

    /** Home's line shows while an offer waits for its answer. */
    public fun pending(offer: AgentUpdateOffer?): Boolean = offer != null && !offer.answered

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
            network = offer.network,
            updating = updates.size,
            removing = removes.size,
        )
    }

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
        /** The offer key answered here: the sheet stays closed for it until Review, whatever the engine says first. */
        val closedKey: String? = null,
        val notRemoved: List<String> = emptyList(),
    )

    private val local = MutableStateFlow(Local())
    private val offers: StateFlow<AgentUpdateOffer?> = choices?.offer ?: MutableStateFlow(null)

    public val ui: Flow<AgentUpdateHomeUi> = combine(offers, local) { offer, now -> build(offer, now) }

    public fun current(): AgentUpdateHomeUi = build(offers.value, local.value)

    private fun build(offer: AgentUpdateOffer?, now: Local): AgentUpdateHomeUi {
        val visible = offer != null && offer.sheetDue && offer.appUpdateKey != now.closedKey
        return AgentUpdateHomeUi(
            sheet =
                AgentUpdateSheetRules.sheet(offer, now.picked, visible)
                    .copy(asking = now.toConfirm.firstOrNull(), working = now.working, failure = now.failure),
            pending = AgentUpdateSheetRules.pending(offer),
            notRemoved = now.notRemoved.toImmutableList(),
        )
    }

    public fun choose(id: AgentId, choice: AgentUpdateChoice) {
        local.update { it.copy(picked = it.picked + (id to choice), failure = null) }
    }

    /** The sheet's button: every removal is confirmed first, one by one; then the decision starts. */
    public fun proceed() {
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
        local.update { it.copy(working = true, failure = null) }
        scope.launch {
            when (val outcome = engine.decide(decision)) {
                is Outcome.Ok -> {
                    val asked = decision.filterValues { it == AgentUpdateChoice.Remove }.keys
                    val refused =
                        (asked - outcome.value.removing.toSet()).mapNotNull { id ->
                            offer.items.firstOrNull { it.id == id }?.name
                        }
                    local.update {
                        it.copy(working = false, closedKey = offer.appUpdateKey, notRemoved = it.notRemoved + refused)
                    }
                }
                is Outcome.Failed ->
                    // Busy is waiting, not failing: the choice is kept and applied when the phone is free.
                    if ((outcome.failure as? Failure.Runtime)?.problem == RuntimeProblem.SlotBusy) {
                        local.update { it.copy(working = false, closedKey = offer.appUpdateKey) }
                    } else {
                        local.update { it.copy(working = false, failure = outcome.failure) }
                    }
            }
        }
    }

    /** Not now (and closing the sheet): nothing downloads until Review. */
    public fun notNow() {
        val offer = offers.value
        if (choices == null || offer == null || local.value.working) return
        local.update { it.copy(closedKey = offer.appUpdateKey, toConfirm = emptyList(), failure = null) }
        scope.launch { choices.notNow() }
    }

    /** Home's Review: the sheet shows again for the current offer. */
    public fun review() {
        val engine = choices ?: return
        local.update { it.copy(closedKey = null, failure = null) }
        scope.launch { engine.review() }
    }

    /** The "wasn't removed" line was read (or its way to Agents taken). */
    public fun dismissNotRemoved() {
        local.update { it.copy(notRemoved = it.notRemoved.drop(1)) }
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
}

/** No sheet: every answer does nothing. */
public object NoAgentUpdateActions : AgentUpdateActions
