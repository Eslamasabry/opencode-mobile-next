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
import io.github.eslamasabry.oc.engine.factsbook.FactsBookCapabilities
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FactsBookReminders
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.engine.factsbook.Reminder
import io.github.eslamasabry.oc.engine.factsbook.ReminderGuess
import io.github.eslamasabry.oc.engine.factsbook.ReminderPreview
import io.github.eslamasabry.oc.engine.factsbook.ReminderProposal
import io.github.eslamasabry.oc.engine.factsbook.ReminderRepeat
import io.github.eslamasabry.oc.engine.factsbook.ReminderTiming
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val away = FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))

private suspend fun FactsBookAccess.reminderNode(): FactsBookReminders? {
    engine.open(activeProfileId.value)
    return engine.node as? FactsBookReminders
}

// ---- Reminders list -----------------------------------------------------------------------------------------------

public data class RemindersUi(
    val reminders: ImmutableList<Reminder>? = null,
    val error: FactsBookError? = null,
    val loading: Boolean = true,
    val working: ImmutableSet<String> = persistentSetOf(),
    val notice: FactsBookError? = null,
    /** The node lists phone alarms as disabled: an active reminder says it does not ring here. */
    val cannotRing: Boolean = true,
    val canAdd: Boolean = true,
)

public interface RemindersActions {
    public fun back()

    public fun reload()

    public fun add()

    public fun edit(reminder: Reminder)

    public fun togglePause(reminder: Reminder)

    public fun dismiss()
}

/** Reminders (F: reminders_screen.dart): Active, Paused and Done, each with pause or resume; tap to change. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class RemindersViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), RemindersActions {
    private val mutable = MutableStateFlow(RemindersUi())
    public val state: StateFlow<RemindersUi> = mutable.asStateFlow()

    init {
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun reload() {
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            access.reminderNode()
            if (mutable.value.reminders == null) {
                // Cache-first: the reminders as the node last listed them, at once; the live list replaces them below.
                access.engine.keptRead { (this as? FactsBookReminders)?.reminders() ?: away }
                    ?.let { kept -> mutable.update { if (it.reminders == null) it.copy(
                    reminders = kept.toImmutableList(),
                    cannotRing = access.engine.state.value.capabilities.isDisabled("phone_alarm_delivery"),
                    canAdd = access.engine.state.value.capabilities.supports(FactsBookCapabilities.REMINDERS),
                ) else it } }
            }
            val r = access.reminderNode()?.reminders() ?: away
            val caps = access.engine.state.value.capabilities
            mutable.update {
                val base = it.copy(
                    loading = false,
                    cannotRing = caps.isDisabled("phone_alarm_delivery"),
                    canAdd = caps.supports(FactsBookCapabilities.REMINDERS),
                )
                when (r) {
                    is FbResult.Ok -> base.copy(reminders = r.value.toImmutableList())
                    is FbResult.Err -> base.copy(error = r.error)
                }
            }
        }
    }

    override fun add(): Unit = nav.open(FactsBookRoute.ReminderEdit(null))

    override fun edit(reminder: Reminder) {
        if (reminder.done || ReminderTiming.fromWhen(reminder.schedule.`when`) == null) return
        nav.open(FactsBookRoute.ReminderEdit(reminder.id))
    }

    override fun togglePause(reminder: Reminder) {
        if (reminder.id in mutable.value.working) return
        mutable.update { it.copy(working = (it.working + reminder.id).toImmutableSet()) }
        viewModelScope.launch {
            val r = access.reminderNode()?.setReminderPaused(reminder, !reminder.paused) ?: away
            mutable.update { it.copy(working = (it.working - reminder.id).toImmutableSet(), notice = (r as? FbResult.Err)?.error) }
            reload()
        }
    }

    override fun dismiss(): Unit = mutable.update { it.copy(notice = null) }
}

// ---- Check this reminder ------------------------------------------------------------------------------------------

/** The person's draft: exactly what is sent to the node to read back. */
public data class ReminderDraft(
    val what: String = "",
    val timing: ReminderTiming,
    /** `vault` (home zone) or `floating` (this phone's time). */
    val zone: String = "vault",
    val locale: String = "en",
)

public data class ReminderEditUi(
    val editing: Boolean = false,
    /** Null while the saved reminder is being read. */
    val draft: ReminderDraft? = null,
    val working: Boolean = false,
    val error: FactsBookError? = null,
    /** The node read the draft differently: nothing was saved. */
    val mismatch: Boolean = false,
    val needsTitle: Boolean = false,
    val cannotRing: Boolean = true,
    /** Add something's words this check started from ("You wrote: …"), or null. */
    val wrote: String? = null,
    /** `date` and/or `time` the words did not say: each asks to be checked. */
    val ambiguities: ImmutableSet<String> = persistentSetOf(),
) {
    /** Opened from Add something: "Change what I wrote" goes back to the words. */
    val fromCapture: Boolean get() = wrote != null
}

public interface ReminderEditActions {
    public fun back()

    /** "Change what I wrote": back to Add something with nothing saved. */
    public fun changeWritten(): Unit = back()

    public fun edit(draft: ReminderDraft)

    public fun save()

    /** The app's language tag: a new reminder's words are read and spoken in it (F: `Localizations.localeOf`). */
    public fun appLanguage(tag: String) {}
}

/**
 * Check this reminder (F: reminder_check_sheet.dart): the person sets what, when and which zone; Save asks the node to
 * read it back and saves only when the node's reading agrees exactly (title, `when`, zone and locale), so the reminder
 * rings when the person saw.
 */
@AssistedInject
public class ReminderEditViewModel(
    @Assisted private val route: FactsBookRoute.ReminderEdit,
    private val access: FactsBookAccess,
    private val nav: AppNavigator,
    private val clock: Clock,
    private val checks: ReminderChecks,
) : ViewModel(), ReminderEditActions {
    private var saved: Reminder? = null
    private var proposal: ReminderProposal? = null
    /** Add something waits for one answer: checked, or nothing. */
    private var answered = route.fromText == null
    /** The app's language, once the screen has said it; a new reminder starts in it. */
    private var appLocale: String? = null
    private val fresh = route.id == null && route.proposalId == null
    private var edited = false
    private fun startLocale(): String = appLocale ?: route.locale
    private val mutable =
        MutableStateFlow(ReminderEditUi(editing = route.id != null || route.proposalId != null, wrote = route.fromText))
    public val state: StateFlow<ReminderEditUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val node = access.reminderNode()
            val caps = access.engine.state.value.capabilities
            val ring = !caps.isDisabled("phone_alarm_delivery")
            val words = route.fromText
            if (words != null) {
                // A first guess from the words; the person checks every field (F: capture_screen.dart `_save`).
                if (route.exact) {
                    // "Remind me about Sam": the title as it is, a one-off tomorrow at 09:00 (F: thing_screen.dart `_remind`).
                    val tomorrow = (clock.now() + 1.days).toLocalDateTime(TimeZone.currentSystemDefault())
                    val at = LocalDateTime(tomorrow.year, tomorrow.month, tomorrow.day, 9, 0)
                    mutable.update {
                        it.copy(draft = ReminderDraft(words, ReminderTiming(ReminderRepeat.Never, at), locale = startLocale()), cannotRing = !ring)
                    }
                    return@launch
                }
                val guess = ReminderGuess.from(words, clock.now().toLocalDateTime(TimeZone.currentSystemDefault()))
                mutable.update {
                    it.copy(
                        draft = ReminderDraft(guess.what, guess.timing, locale = startLocale()),
                        ambiguities = guess.ambiguities.toImmutableSet(),
                        cannotRing = !ring,
                    )
                }
                return@launch
            }
            if (route.proposalId != null) {
                val triggers = access.engine.triggers
                if (triggers.state.value.proposals.none { it.id == route.proposalId }) triggers.refresh(quiet = true)
                val found = triggers.state.value.proposals.firstOrNull { it.id == route.proposalId }
                val schedule = found?.preview?.schedule
                val timing = schedule?.let { ReminderTiming.fromWhen(it.`when`) }
                if (found == null || schedule == null || timing == null) {
                    mutable.update { it.copy(error = FactsBookError("not_found"), cannotRing = !ring) }
                    return@launch
                }
                proposal = found
                mutable.update {
                    it.copy(
                        draft = ReminderDraft(found.preview.what, timing, schedule.zone, if (found.preview.locale == "ar") "ar" else "en"),
                        cannotRing = !ring,
                    )
                }
                return@launch
            }
            if (route.id == null) {
                // Tomorrow at 09:00, as Flutter starts a new one.
                val tomorrow = (clock.now() + 1.days).toLocalDateTime(TimeZone.currentSystemDefault())
                val at = LocalDateTime(tomorrow.year, tomorrow.month, tomorrow.day, 9, 0)
                mutable.update { it.copy(draft = ReminderDraft(timing = ReminderTiming(ReminderRepeat.Never, at), locale = startLocale()), cannotRing = !ring) }
                return@launch
            }
            // Cache-first: the reminder as the node last listed it opens at once; the live read replaces it below.
            access.engine.keptRead { (this as? FactsBookReminders)?.reminders() ?: away }?.firstOrNull { it.id == route.id }?.let { kept ->
                val keptTiming = ReminderTiming.fromWhen(kept.schedule.`when`) ?: return@let
                saved = kept
                mutable.update {
                    it.copy(draft = ReminderDraft(kept.what, keptTiming, kept.schedule.zone, if (kept.locale == "ar") "ar" else "en"), cannotRing = !ring)
                }
            }
            val live = node?.reminders()
            val found = (live as? FbResult.Ok)?.value?.firstOrNull { it.id == route.id }
            val timing = found?.let { ReminderTiming.fromWhen(it.schedule.`when`) }
            // Nothing answered: the kept copy stays on screen (saving will say if the node is still away).
            if (live !is FbResult.Ok && saved != null && mutable.value.draft != null) return@launch
            if (found == null || timing == null) {
                mutable.update { it.copy(error = FactsBookError("not_found"), cannotRing = !ring) }
                return@launch
            }
            saved = found
            mutable.update {
                // What the person already changed on the kept copy stays.
                if (edited && it.draft != null) it.copy(cannotRing = !ring)
                else it.copy(
                    draft = ReminderDraft(found.what, timing, found.schedule.zone, if (found.locale == "ar") "ar" else "en"),
                    cannotRing = !ring,
                )
            }
        }
    }

    override fun back() {
        answer(ReminderCheckResult.Cancelled)
        nav.back()
    }

    private fun answer(result: ReminderCheckResult) {
        if (answered) return
        answered = true
        checks.tell(result)
    }

    override fun onCleared() {
        answer(ReminderCheckResult.Cancelled)
    }

    override fun edit(draft: ReminderDraft) {
        edited = true
        mutable.update { it.copy(draft = draft, error = null, mismatch = false, needsTitle = false) }
    }

    override fun appLanguage(tag: String) {
        val locale = if (tag.lowercase().startsWith("ar")) "ar" else "en"
        appLocale = locale
        if (fresh && !edited) mutable.update { s -> s.draft?.let { s.copy(draft = it.copy(locale = locale)) } ?: s }
    }

    override fun save() {
        val draft = mutable.value.draft ?: return
        if (mutable.value.working) return
        if (draft.what.isBlank()) {
            mutable.update { it.copy(needsTitle = true) }
            return
        }
        mutable.update { it.copy(working = true, error = null, mismatch = false) }
        viewModelScope.launch {
            val node = access.reminderNode()
            if (node == null) {
                mutable.update { it.copy(working = false, error = away.error) }
                return@launch
            }
            val old = saved
            val proposed = proposal
            val scheduleId = old?.schedule?.id ?: proposed?.preview?.schedule?.id
            val area = old?.area ?: proposed?.area ?: route.area ?: "general"
            val preview =
                when (val r = node.previewReminder(draft.what, draft.timing, draft.zone, draft.locale, scheduleId, area)) {
                    is FbResult.Err -> {
                        mutable.update { it.copy(working = false, error = r.error) }
                        return@launch
                    }
                    is FbResult.Ok -> r.value
                }
            val agrees =
                preview.confirmable &&
                    preview.schedule?.`when`?.raw == draft.timing.toWhen() &&
                    preview.zone == draft.zone &&
                    preview.locale == draft.locale &&
                    preview.what == draft.what.trim()
            if (!agrees) {
                mutable.update { it.copy(working = false, mismatch = true) }
                return@launch
            }
            if (route.fromText != null) {
                // Add something saves the note first, then this reminder (F: capture_screen.dart).
                mutable.update { it.copy(working = false) }
                answer(ReminderCheckResult.Checked(preview))
                nav.back()
                return@launch
            }
            val floating = preview.zone == "floating"
            val result =
                when {
                    proposed != null -> access.engine.triggers.editProposal(proposed, preview)
                    old == null -> node.createReminder(preview, floatingConfirmed = floating)
                    else -> node.updateReminder(old, preview, floating)
                }
            when (result) {
                is FbResult.Ok -> {
                    mutable.update { it.copy(working = false) }
                    if (proposed != null) checks.tell(ReminderCheckResult.ProposalChanged)
                    nav.back()
                }
                is FbResult.Err -> mutable.update { it.copy(working = false, error = result.error) }
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FactsBookRoute.ReminderEdit): ReminderEditViewModel
    }
}
