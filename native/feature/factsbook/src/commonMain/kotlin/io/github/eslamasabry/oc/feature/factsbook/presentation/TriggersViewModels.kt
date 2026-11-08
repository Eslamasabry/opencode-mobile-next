package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.ConversationOffer
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.engine.factsbook.ReminderProposal
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.engine.factsbook.Rule
import io.github.eslamasabry.oc.engine.factsbook.RuleActivity
import io.github.eslamasabry.oc.engine.factsbook.RuleDefinition
import io.github.eslamasabry.oc.engine.factsbook.RuleProblem
import io.github.eslamasabry.oc.engine.factsbook.RuleRules
import io.github.eslamasabry.oc.engine.factsbook.RuleTestResult
import io.github.eslamasabry.oc.engine.factsbook.TriggerEvent
import io.github.eslamasabry.oc.engine.factsbook.TriggerFiring
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcome
import io.github.eslamasabry.oc.engine.factsbook.TriggerOutcomeKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerSample
import io.github.eslamasabry.oc.engine.factsbook.TriggerSource
import io.github.eslamasabry.oc.engine.factsbook.TriggerSourceKind
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplate
import io.github.eslamasabry.oc.engine.factsbook.TriggerTemplateId
import io.github.eslamasabry.oc.engine.factsbook.TriggersLoad
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ---- Triggers -----------------------------------------------------------------------------------------------------

public enum class TriggersNotice { Paused, Resumed, Stale }

public data class RuleGroup(val kind: TriggerSourceKind, val rules: ImmutableList<Rule>)

public data class TriggersUi(
    val load: TriggersLoad = TriggersLoad.Idle,
    val groups: ImmutableList<RuleGroup> = persistentListOf(),
    val activity: ImmutableMap<String, RuleActivity> = persistentMapOf(),
    /** The eight newest messages rules looked at. */
    val events: ImmutableList<TriggerEvent> = persistentListOf(),
    val canApprove: Boolean = false,
    val working: ImmutableSet<String> = persistentSetOf(),
    val notice: TriggersNotice? = null,
    val failure: FactsBookError? = null,
    val error: FactsBookError? = null,
)

public interface TriggersActions {
    public fun back()

    public fun refresh()

    public fun newRule()

    public fun openRule(rule: Rule)

    public fun openEvent(event: TriggerEvent)

    public fun togglePause(rule: Rule)

    public fun setUpApproval()

    public fun dismiss()
}

/**
 * Triggers (spec 44, F: triggers_screen.dart): the rules that watch for one kind of message and only suggest, grouped by
 * where the message comes from, each with what it did this week and a pause; the messages rules looked at; New rule.
 * A node without triggers is told so plainly.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class TriggersViewModel(private val access: FactsBookAccess, private val nav: AppNavigator, private val clock: Clock) :
    ViewModel(), TriggersActions {
    private data class Local(val working: Set<String> = emptySet(), val notice: TriggersNotice? = null, val failure: FactsBookError? = null)

    private val local = MutableStateFlow(Local())
    private val triggers get() = access.engine.triggers

    public val state: StateFlow<TriggersUi> =
        combine(access.engine.triggers.state, access.engine.state, local) { t, s, own ->
                val now = clock.now()
                TriggersUi(
                    load = t.load,
                    groups = t.rulesBySource.map { (kind, rules) -> RuleGroup(kind, rules.toImmutableList()) }.toImmutableList(),
                    activity = t.rules.associate { it.id to t.activity(it.id, now) }.toImmutableMap(),
                    events = t.events.sortedByDescending { it.capturedAt }.take(8).toImmutableList(),
                    canApprove = s.canApprove,
                    working = own.working.toImmutableSet(),
                    notice = own.notice,
                    failure = own.failure,
                    error = t.error,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TriggersUi())

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            triggers.refresh()
        }
    }

    override fun newRule(): Unit = nav.open(FactsBookRoute.Rule(null))

    override fun openRule(rule: Rule): Unit = nav.open(FactsBookRoute.Rule(rule.id))

    override fun openEvent(event: TriggerEvent): Unit = nav.open(FactsBookRoute.Event(event.id))

    override fun togglePause(rule: Rule) {
        if (rule.id in local.value.working || !state.value.canApprove) return
        val pausing = rule.definition.enabled
        local.update { it.copy(working = it.working + rule.id, notice = null, failure = null) }
        viewModelScope.launch {
            val r = triggers.setPaused(rule, paused = pausing)
            local.update {
                val done = it.copy(working = it.working - rule.id)
                when {
                    r is FbResult.Ok -> done.copy(notice = if (pausing) TriggersNotice.Paused else TriggersNotice.Resumed)
                    r.errorOrNullStale() -> done.copy(notice = TriggersNotice.Stale)
                    else -> done.copy(failure = (r as FbResult.Err).error)
                }
            }
        }
    }

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)

    override fun dismiss(): Unit = local.update { it.copy(notice = null, failure = null) }
}

internal fun FbResult<*>.errorOrNullStale(): Boolean = (this as? FbResult.Err)?.error?.stale == true

// ---- Rule editor --------------------------------------------------------------------------------------------------

/** The editor's fields, as the person sees them. */
public data class RuleForm(
    val name: String = "",
    val words: String = "",
    val sender: String = "",
    val kind: TriggerSourceKind = TriggerSourceKind.Share,
    val template: TriggerTemplateId = TriggerTemplateId.Appointment,
    val locale: String = "en",
    val reminder: Boolean = true,
    val conversation: Boolean = true,
    val enabled: Boolean = true,
    val limit: Long = 20,
    val area: String = "general",
    /** Fact outcomes set up on the node: kept exactly. */
    val kept: List<TriggerOutcome> = emptyList(),
    val source: TriggerSource? = null,
) {
    public fun definition(clientId: String): RuleDefinition =
        RuleDefinition(
            name = name.trim(),
            enabled = enabled,
            source = source?.takeIf { it.kind == kind } ?: TriggerSource(clientId, kind, kind.defaultInstance),
            template = TriggerTemplate(template, locale = locale),
            area = area,
            containsAll = RuleRules.splitWords(words),
            outcomes =
                buildList {
                    if (reminder) add(TriggerOutcome(TriggerOutcomeKind.Reminder))
                    addAll(kept)
                    if (conversation) add(TriggerOutcome(TriggerOutcomeKind.Conversation))
                },
            limitPerDay = limit,
            senderClaim = sender.trim().ifEmpty { null },
        )

    public companion object {
        public fun of(rule: Rule): RuleForm {
            val d = rule.definition
            return RuleForm(
                name = d.name,
                words = d.containsAll.joinToString(", "),
                sender = d.senderClaim.orEmpty(),
                kind = d.source.kind,
                template = d.template.id,
                locale = d.template.locale,
                reminder = d.hasOutcome(TriggerOutcomeKind.Reminder),
                conversation = d.hasOutcome(TriggerOutcomeKind.Conversation),
                enabled = d.enabled,
                limit = d.limitPerDay,
                area = d.area,
                kept = d.outcomes.filter { it.kind == TriggerOutcomeKind.Fact },
                source = d.source,
            )
        }
    }
}

public enum class RuleNotice { Saved, Stale }

public data class RuleEditorUi(
    val form: RuleForm = RuleForm(),
    /** Bumped when the fields must show [form] again (a rule reloaded after it changed in memory). */
    val formSeed: Int = 0,
    val saved: Rule? = null,
    val dirty: Boolean = true,
    val canApprove: Boolean = false,
    val offersSupported: Boolean = true,
    val templates: ImmutableList<TriggerTemplateId> = TriggerTemplateId.offered.toImmutableList(),
    val areas: ImmutableList<String> = persistentListOf("general"),
    val saving: Boolean = false,
    val problem: RuleProblem? = null,
    /** What stops a test, worded on the field. */
    val testProblem: RuleProblem? = null,
    val notice: RuleNotice? = null,
    val failure: FactsBookError? = null,
    val sample: String = "",
    val testing: Boolean = false,
    val result: RuleTestResult? = null,
    val testFailure: FactsBookError? = null,
    val confirmDelete: Boolean = false,
)

public interface RuleEditorActions {
    public fun back()

    public fun setUpApproval()

    public fun edit(change: (RuleForm) -> RuleForm)

    public fun sample(text: String)

    public fun useExample()

    public fun test()

    public fun save()

    public fun askDelete()

    public fun delete()

    public fun cancelDelete()

    public fun dismiss()
}

/**
 * The rule editor (F: rule_editor_screen.dart): when the message is a shared text, a saved chat or a note; says something
 * like one kind of message (and optionally some words); then it proposes a reminder and/or offers a chat. "Try it on a
 * sample" asks the node what the draft would do and saves nothing. A rule is signed by this phone's approval key; one
 * that changed in memory meanwhile is reloaded, never overwritten.
 */
@AssistedInject
public class RuleEditorViewModel(
    @Assisted private val route: FactsBookRoute.Rule,
    private val access: FactsBookAccess,
    private val nav: AppNavigator,
    private val clock: Clock,
) : ViewModel(), RuleEditorActions {
    private val mutable = MutableStateFlow(RuleEditorUi())
    public val state: StateFlow<RuleEditorUi> = mutable.asStateFlow()
    private val triggers get() = access.engine.triggers
    private val clientId: String get() = access.engine.state.value.link?.clientId ?: mutable.value.saved?.definition?.source?.clientId.orEmpty()

    init {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            if (triggers.state.value.load != TriggersLoad.Ready) triggers.refresh(quiet = true)
            val rule = route.id?.let { triggers.state.value.ruleById(it) }
            val s = access.engine.state.value
            mutable.update {
                val form = rule?.let(RuleForm::of) ?: it.form
                checked(it.copy(form = form, formSeed = it.formSeed + 1, saved = rule, canApprove = s.canApprove, offersSupported = triggers.offersSupported))
            }
            val areas = (access.engine.node?.areas() as? FbResult.Ok)?.value?.filter { !it.sensitiveStorage }?.map { it.slug }
            if (!areas.isNullOrEmpty()) mutable.update { it.copy(areas = (areas + it.form.area).distinct().toImmutableList()) }
        }
    }

    /** The dirty flag and the reason a test cannot run, from the fields as they are now. */
    private fun checked(ui: RuleEditorUi): RuleEditorUi {
        val draft = ui.form.definition(clientId)
        return ui.copy(
            dirty = ui.saved == null || draft.toJson() != ui.saved.definition.toJson(),
            testProblem = RuleRules.check(draft, ui.templates.toSet()).firstOrNull(),
        )
    }

    override fun back(): Unit = nav.back()

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)

    override fun edit(change: (RuleForm) -> RuleForm) {
        if (!mutable.value.canApprove) return
        mutable.update { checked(it.copy(form = change(it.form), problem = null, result = null, testFailure = null)) }
    }

    override fun sample(text: String): Unit = mutable.update { it.copy(sample = text, result = null, testFailure = null) }

    override fun useExample() {
        val form = mutable.value.form
        val at = (clock.now() + 4.days).toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        val stamp = "${at.year}-${two(at.month.ordinal + 1)}-${two(at.day)}T09:00"
        val ar = form.locale == "ar"
        val label =
            when (form.template) {
                TriggerTemplateId.Appointment -> if (ar) "موعد" else "Appointment"
                TriggerTemplateId.Delivery -> if (ar) "توصيل" else "Delivery"
                TriggerTemplateId.Booking -> if (ar) "حجز" else "Booking"
                TriggerTemplateId.Subscription -> if (ar) "تجديد اشتراك" else "Subscription renewal"
                TriggerTemplateId.BillDue -> if (ar) "استحقاق فاتورة" else "Bill due"
            }
        val title = if (ar) "زيارة المكتبة" else "Library visit"
        sample("$label: $title | $stamp")
    }

    override fun test() {
        val now = mutable.value
        val text = now.sample.trim()
        if (now.testing || text.isEmpty() || now.testProblem != null) return
        val draft = now.form.definition(clientId)
        mutable.update { it.copy(testing = true, result = null, testFailure = null) }
        viewModelScope.launch {
            val sample = TriggerSample(draft.source.kind, text, draft.area, draft.template.locale, draft.source.instance)
            val r = triggers.testDraft(draft, sample)
            mutable.update {
                when (r) {
                    is FbResult.Ok -> it.copy(testing = false, result = r.value)
                    is FbResult.Err -> it.copy(testing = false, testFailure = r.error)
                }
            }
        }
    }

    override fun save() {
        val now = mutable.value
        if (!now.canApprove || now.saving || !now.dirty) return
        val draft = now.form.definition(clientId)
        val problem = RuleRules.check(draft, now.templates.toSet()).firstOrNull()
        if (problem != null) {
            mutable.update { it.copy(problem = problem) }
            return
        }
        mutable.update { it.copy(saving = true, problem = null, notice = null, failure = null) }
        viewModelScope.launch {
            val r = triggers.saveRule(draft, replacing = now.saved)
            mutable.update {
                when {
                    r is FbResult.Ok -> checked(it.copy(saving = false, saved = r.value, form = it.form.copy(source = r.value.definition.source), notice = RuleNotice.Saved))
                    r.errorOrNullStale() -> {
                        // Show what the node holds now, and let the person decide again.
                        val current = it.saved?.let { saved -> triggers.state.value.ruleById(saved.id) }
                        checked(
                            it.copy(
                                saving = false,
                                saved = current ?: it.saved,
                                form = current?.let(RuleForm::of) ?: it.form,
                                formSeed = it.formSeed + 1,
                                notice = RuleNotice.Stale,
                            )
                        )
                    }
                    else -> it.copy(saving = false, failure = (r as FbResult.Err).error)
                }
            }
        }
    }

    override fun askDelete() {
        if (mutable.value.saved != null && mutable.value.canApprove) mutable.update { it.copy(confirmDelete = true) }
    }

    override fun cancelDelete(): Unit = mutable.update { it.copy(confirmDelete = false) }

    override fun delete() {
        val rule = mutable.value.saved ?: return
        mutable.update { it.copy(confirmDelete = false, saving = true, failure = null) }
        viewModelScope.launch {
            val r = triggers.deleteRule(rule)
            if (r is FbResult.Ok) {
                mutable.update { it.copy(saving = false) }
                nav.back()
            } else {
                mutable.update {
                    if (r.errorOrNullStale()) it.copy(saving = false, notice = RuleNotice.Stale)
                    else it.copy(saving = false, failure = (r as FbResult.Err).error)
                }
            }
        }
    }

    override fun dismiss(): Unit = mutable.update { it.copy(notice = null, failure = null) }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FactsBookRoute.Rule): RuleEditorViewModel
    }
}

// ---- What happened to this message --------------------------------------------------------------------------------

public data class EventUi(
    val event: TriggerEvent? = null,
    val firings: ImmutableList<TriggerFiring> = persistentListOf(),
    /** The rule name of each firing, where the rule is still listed. */
    val ruleNames: ImmutableMap<String, String> = persistentMapOf(),
    val proposals: ImmutableList<ReminderProposal> = persistentListOf(),
    val offers: ImmutableList<ConversationOffer> = persistentListOf(),
    /** Review cards a firing of this event made. */
    val facts: ImmutableList<ReviewItem> = persistentListOf(),
    val loading: Boolean = true,
    val error: FactsBookError? = null,
)

public interface EventActions {
    public fun back()

    public fun reload()
}

/**
 * "What happened to this message" (F: event_detail_screen.dart): the outcome first, then the rule that matched and
 * where the message came from. The node returns metadata only, so the original text is never shown, and the sender
 * and app are labelled as claims.
 */
@AssistedInject
public class EventViewModel(
    @Assisted private val route: FactsBookRoute.Event,
    private val access: FactsBookAccess,
    private val nav: AppNavigator,
) : ViewModel(), EventActions {
    private val mutable = MutableStateFlow(EventUi())
    public val state: StateFlow<EventUi> = mutable.asStateFlow()

    init {
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun reload() {
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            val triggers = access.engine.triggers
            // The lists carry the event; read them again so a fresh one shows.
            triggers.refresh(quiet = true)
            val t = triggers.state.value
            val event = t.eventById(route.id)
            val firings = t.firingsOfEvent(route.id)
            val made = firings.flatMap { it.outcomes }.toSet()
            mutable.value =
                EventUi(
                    event = event,
                    firings = firings.toImmutableList(),
                    ruleNames = firings.mapNotNull { f -> t.ruleById(f.ruleId)?.let { f.id to it.definition.name } }.toMap().toImmutableMap(),
                    proposals = t.proposals.filter { it.eventId == route.id }.toImmutableList(),
                    offers = t.offers.filter { it.eventId == route.id }.toImmutableList(),
                    facts = access.engine.state.value.review.filter { it.id in made && it.text != null }.toImmutableList(),
                    loading = false,
                    error = if (event == null) (t.error ?: FactsBookError("not_found", status = 404)) else null,
                )
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FactsBookRoute.Event): EventViewModel
    }
}
