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
            val r = access.reminderNode()?.reminders() ?: away
            val caps = access.engine.state.value.capabilities
            mutable.update {
                val base = it.copy(
                    loading = false,
                    cannotRing = caps.isDisabled("phone_alarm_delivery") || !caps.supports("phone_alarm_delivery"),
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
)

public interface ReminderEditActions {
    public fun back()

    public fun edit(draft: ReminderDraft)

    public fun save()
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
) : ViewModel(), ReminderEditActions {
    private var saved: Reminder? = null
    private val mutable = MutableStateFlow(ReminderEditUi(editing = route.id != null))
    public val state: StateFlow<ReminderEditUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val node = access.reminderNode()
            val caps = access.engine.state.value.capabilities
            val ring = !(caps.isDisabled("phone_alarm_delivery") || !caps.supports("phone_alarm_delivery"))
            if (route.id == null) {
                // Tomorrow at 09:00, as Flutter starts a new one.
                val tomorrow = (clock.now() + 1.days).toLocalDateTime(TimeZone.currentSystemDefault())
                val at = LocalDateTime(tomorrow.year, tomorrow.month, tomorrow.day, 9, 0)
                mutable.update { it.copy(draft = ReminderDraft(timing = ReminderTiming(ReminderRepeat.Never, at), locale = route.locale), cannotRing = !ring) }
                return@launch
            }
            val found = (node?.reminders() as? FbResult.Ok)?.value?.firstOrNull { it.id == route.id }
            val timing = found?.let { ReminderTiming.fromWhen(it.schedule.`when`) }
            if (found == null || timing == null) {
                mutable.update { it.copy(error = FactsBookError("not_found"), cannotRing = !ring) }
                return@launch
            }
            saved = found
            mutable.update {
                it.copy(
                    draft = ReminderDraft(found.what, timing, found.schedule.zone, if (found.locale == "ar") "ar" else "en"),
                    cannotRing = !ring,
                )
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun edit(draft: ReminderDraft): Unit = mutable.update { it.copy(draft = draft, error = null, mismatch = false, needsTitle = false) }

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
            val preview =
                when (
                    val r = node.previewReminder(draft.what, draft.timing, draft.zone, draft.locale, old?.schedule?.id, old?.area ?: "general")
                ) {
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
            val floating = preview.zone == "floating"
            val result = if (old == null) node.createReminder(preview, floatingConfirmed = floating) else node.updateReminder(old, preview, floating)
            when (result) {
                is FbResult.Ok -> {
                    mutable.update { it.copy(working = false) }
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
