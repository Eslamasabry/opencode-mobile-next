package io.github.eslamasabry.oc.engine.factsbook

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public enum class TriggersLoad {
    Idle,
    Loading,
    Ready,
    Failed,

    /** The node does not offer triggers (its matrix does not list them, or it answered not found): "not available yet". */
    Unavailable,
}

/** Everything the Triggers pages read (F: FactsBookTriggers). */
public data class TriggersState(
    val load: TriggersLoad = TriggersLoad.Idle,
    val rules: List<Rule> = emptyList(),
    val events: List<TriggerEvent> = emptyList(),
    val firings: List<TriggerFiring> = emptyList(),
    val proposals: List<ReminderProposal> = emptyList(),
    val offers: List<ConversationOffer> = emptyList(),
    val error: FactsBookError? = null,
) {
    public fun ruleById(id: String): Rule? = rules.firstOrNull { it.id == id }

    public fun eventById(id: String): TriggerEvent? = events.firstOrNull { it.id == id }

    /** The firings of one event, newest first. */
    public fun firingsOfEvent(eventId: String): List<TriggerFiring> = firings.filter { it.eventId == eventId }.sortedByDescending { it.at }

    /** The firing that made [outcomeId] (a review card, a proposal or an offer). */
    public fun firingOfOutcome(outcomeId: String): TriggerFiring? = firings.firstOrNull { outcomeId in it.outcomes }

    public fun firingRuleId(firingId: String): String? = firings.firstOrNull { it.id == firingId }?.ruleId

    /** How a rule did in the seven days before [now]. */
    public fun activity(ruleId: String, now: Instant): RuleActivity {
        val since = now - 7.days
        val mine = firings.filter { it.ruleId == ruleId && it.at >= since }
        val ids = mine.map { it.id }.toSet()
        return RuleActivity(
            proposed = mine.count { it.state == FiringState.Proposed },
            ignored = mine.count { it.state != FiringState.Proposed },
            offers = offers.count { it.firingId in ids },
        )
    }

    /** Rules grouped by where their events come from, in a fixed order. */
    public val rulesBySource: Map<TriggerSourceKind, List<Rule>>
        get() =
            TriggerSourceKind.entries.mapNotNull { kind -> rules.filter { it.definition.source.kind == kind }.takeIf { it.isNotEmpty() }?.let { kind to it } }
                .toMap()

    public fun openOffers(now: Instant): List<ConversationOffer> = offers.filter { it.isOpen(now) }

    override fun toString(): String = "TriggersState(load=$load, rules=${rules.size})"
}

/**
 * Rules, events, firings, reminder proposals and offers, and every write the Triggers pages make (spec 44). Every read
 * is gated on the node's capabilities; a node without them is [TriggersLoad.Unavailable], never an error. One serial
 * lane; a failed read keeps the last copy.
 */
public class FactsBookTriggers internal constructor(
    private val node: () -> FactsBookGateway?,
    private val capabilities: () -> FactsBookCapabilities,
    private val clock: Clock,
    /** Told after every change, so the Memory section counts proposals and offers once with the rest. */
    private val changed: (TriggersState) -> Unit = {},
) {
    private val lane = Mutex()
    private val mutable = MutableStateFlow(TriggersState())
    public val state: StateFlow<TriggersState> = mutable.asStateFlow()

    public val supported: Boolean get() = capabilities().supports(FactsBookCapabilities.TRIGGERS)

    /** "Talk about this with your agent?" cards. */
    public val offersSupported: Boolean get() = supported && capabilities().supports(CONVERSATION_OFFERS)

    /** A proposal's reminder can be changed before it is confirmed. */
    public val proposalEditSupported: Boolean get() = capabilities().supports(PROPOSAL_EDIT)

    public val templates: List<TriggerTemplateId> get() = TriggerTemplateId.offered

    private val calls: FactsBookTriggerCalls? get() = node() as? FactsBookTriggerCalls

    internal fun reset() {
        mutable.value = TriggersState()
        changed(mutable.value)
    }

    /** Reads everything the pages show. With [quiet] nothing flips to loading (a refresh after a write). */
    public suspend fun refresh(quiet: Boolean = false): Unit = lane.withLock { refreshLocked(quiet) }

    private suspend fun refreshLocked(quiet: Boolean) {
        val api = calls
        if (!supported || api == null) {
            mutable.update { it.copy(load = if (supported) TriggersLoad.Idle else TriggersLoad.Unavailable) }
            return
        }
        if (!quiet && mutable.value.load != TriggersLoad.Ready) mutable.update { it.copy(load = TriggersLoad.Loading) }
        val rules = when (val r = api.rules()) { is FbResult.Err -> return failed(r.error); is FbResult.Ok -> r.value }
        val events = when (val r = api.events()) { is FbResult.Err -> return failed(r.error); is FbResult.Ok -> r.value }
        val firings = when (val r = api.firings()) { is FbResult.Err -> return failed(r.error); is FbResult.Ok -> r.value }
        val proposals = when (val r = api.reminderProposals()) { is FbResult.Err -> return failed(r.error); is FbResult.Ok -> r.value }
        val offers =
            if (!offersSupported) emptyList()
            else when (val r = api.offers()) { is FbResult.Err -> return failed(r.error); is FbResult.Ok -> r.value }
        mutable.value = TriggersState(TriggersLoad.Ready, rules, events, firings, proposals, offers, null)
        changed(mutable.value)
    }

    private fun failed(error: FactsBookError) {
        mutable.update {
            if (error.code == "not_found" || error.notEnabled) it.copy(load = TriggersLoad.Unavailable, error = null)
            else it.copy(error = error, load = if (it.load == TriggersLoad.Ready) TriggersLoad.Ready else TriggersLoad.Failed)
        }
    }

    /** A write, then a quiet refresh; a stale one refreshes too, so the new state shows. */
    private suspend fun <T> write(call: suspend (FactsBookTriggerCalls) -> FbResult<T>): FbResult<T> =
        lane.withLock {
            val api = calls ?: return@withLock FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))
            val result = call(api)
            if (result is FbResult.Ok || result.errorOrNull()?.stale == true) refreshLocked(quiet = true)
            result
        }

    public suspend fun saveRule(definition: RuleDefinition, replacing: Rule? = null): FbResult<Rule> =
        write { it.saveRule(definition, replacing) }

    public suspend fun setPaused(rule: Rule, paused: Boolean): FbResult<Rule> =
        write { it.saveRule(rule.definition.copy(enabled = !paused), rule) }

    public suspend fun deleteRule(rule: Rule): FbResult<Rule> = write { it.deleteRule(rule) }

    public suspend fun decideProposal(proposal: ReminderProposal, decision: ProposalDecision): FbResult<MutationResult> =
        write { it.decideReminderProposal(proposal, decision) }

    public suspend fun editProposal(proposal: ReminderProposal, preview: ReminderPreview): FbResult<MutationResult> =
        write { it.editReminderProposal(proposal, preview) }

    public suspend fun cancelOffer(offer: ConversationOffer): FbResult<ConversationOffer> = write { it.cancelOffer(offer) }

    /** What a draft would do with [sample]. Nothing is written, so nothing is refreshed. */
    public suspend fun testDraft(draft: RuleDefinition, sample: TriggerSample): FbResult<RuleTestResult> =
        calls?.testDraft(draft, sample) ?: FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))

    public fun now(): Instant = clock.now()

    public companion object {
        public const val CONVERSATION_OFFERS: String = "explicit_conversation_offers"
        public const val PROPOSAL_EDIT: String = "reminder_proposal_edit"
    }
}
