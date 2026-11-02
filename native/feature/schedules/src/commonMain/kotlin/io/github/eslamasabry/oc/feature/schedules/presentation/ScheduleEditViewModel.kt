package io.github.eslamasabry.oc.feature.schedules.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.ScheduleDraft
import io.github.eslamasabry.oc.engine.schedules.ScheduleEngine
import io.github.eslamasabry.oc.engine.schedules.ScheduledPrompt
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Pure editor rules: typed fields → a recurrence, or the one plain issue to fix. */
public object EditorRules {
    private val timePattern = Regex("^(\\d{1,2})[:.](\\d{2})$")

    public fun time(text: String): Pair<Int, Int>? {
        val m = timePattern.matchEntire(text.trim()) ?: return null
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    public fun date(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim())
        } catch (e: IllegalArgumentException) {
            null
        }

    public fun recurrence(fields: EditorFields, nowMs: Long, zone: TimeZone): Pair<Recurrence?, EditIssue?> {
        if (fields.prompt.isBlank()) return null to EditIssue.PromptRequired
        if (fields.project.isBlank()) return null to EditIssue.ProjectRequired
        val (hour, minute) = time(fields.time) ?: return null to EditIssue.TimeInvalid
        return when (fields.kind) {
            RecurrenceKind.Daily -> Recurrence.daily(hour, minute) to null
            RecurrenceKind.Weekdays -> Recurrence.weekdays(hour, minute) to null
            RecurrenceKind.Once -> {
                val day = date(fields.date) ?: return null to EditIssue.DateInvalid
                Recurrence.once(LocalDateTime(day.year, day.month, day.day, hour, minute).toInstant(zone).toEpochMilliseconds()) to null
            }
            RecurrenceKind.Interval -> {
                val hours = fields.hours.trim().toIntOrNull()?.takeIf { it in 1..168 } ?: return null to EditIssue.HoursInvalid
                val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date
                val anchor = LocalDateTime(today.year, today.month, today.day, hour, minute).toInstant(zone).toEpochMilliseconds()
                Recurrence.interval(hours, anchor) to null
            }
        }
    }

    /** The editor fields of a saved schedule. */
    public fun fields(schedule: ScheduledPrompt, models: List<ModelChoice>, zone: TimeZone): EditorFields {
        val rec = schedule.recurrence
        val at = rec.atMs?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone) }
        val hour = at?.hour ?: rec.hour
        val minute = at?.minute ?: rec.minute
        return EditorFields(
            prompt = schedule.prompt,
            project = schedule.projectPath,
            kind = rec.kind,
            time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
            date = if (rec.kind == RecurrenceKind.Once && at != null) at.date.toString() else "",
            hours = rec.hours.toString(),
            missed = schedule.missedPolicy,
            model =
                schedule.modelProviderId?.let { provider ->
                    models.firstOrNull { it.providerId == provider && it.modelId == schedule.modelId }
                        ?: ModelChoice(provider, schedule.modelId!!, "$provider/${schedule.modelId}")
                },
        )
    }
}

/** K02: create or edit one scheduled prompt. Saving re-arms the OS schedule; a failed save keeps the typed fields. */
@AssistedInject
public class ScheduleEditViewModel(
    @Assisted private val route: SchedulesRoute.Edit,
    private val engine: ScheduleEngine,
    private val servers: ScheduleServers,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), ScheduleEditActions {
    private val profile = route.profileId.raw
    private val zone get() = TimeZone.currentSystemDefault()
    private val mutable = MutableStateFlow(ScheduleEditUi(editing = route.scheduleId != null))
    public val state: StateFlow<ScheduleEditUi> = mutable.asStateFlow()
    private var editing: ScheduledPrompt? = null
    private var sessionId: String? = null
    private var agent: String? = null
    private var workspace: String? = null

    init {
        viewModelScope.launch { seed() }
    }

    private suspend fun seed() {
        val target = servers.target(route.profileId)
        if (!target.available) {
            mutable.update { it.copy(phase = EditPhase.Unavailable) }
            return
        }
        val models = servers.models(route.profileId)
        engine.load(profile)
        val existing = route.scheduleId?.let { id -> engine.state(profile).value.schedules.firstOrNull { it.id == id } }
        val fields =
            if (existing != null) {
                editing = existing
                sessionId = existing.sessionId
                agent = existing.agent
                workspace = existing.workspace
                EditorRules.fields(existing, models, zone)
            } else {
                val from = route.from?.let { servers.conversation(it) }
                if (from != null) sessionId = route.from?.sessionId?.raw
                agent = from?.agent
                val model =
                    if (from?.providerId != null && from.modelId != null) {
                        models.firstOrNull { it.providerId == from.providerId && it.modelId == from.modelId }
                            ?: ModelChoice(from.providerId, from.modelId, "${from.providerId}/${from.modelId}")
                    } else {
                        null
                    }
                EditorFields(
                    project = from?.directory ?: route.projectDir ?: target.recentProject.orEmpty(),
                    date = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds()).toLocalDateTime(zone).date.toString(),
                    model = model,
                )
            }
        val title = if (sessionId != null) route.from?.let { servers.conversation(it)?.title } else null
        mutable.update {
            it.copy(
                phase = if (route.scheduleId != null && existing == null) EditPhase.Unavailable else EditPhase.Ready,
                fields = fields,
                seed = it.seed + 1,
                models = models.toImmutableList(),
                continuesConversation = sessionId != null,
                conversationTitle = title,
            )
        }
    }

    override fun back(): Unit = nav.back()

    override fun save(input: EditorFields) {
        if (mutable.value.saving || mutable.value.phase != EditPhase.Ready) return
        val (recurrence, issue) = EditorRules.recurrence(input, clock.now().toEpochMilliseconds(), zone)
        if (recurrence == null) {
            mutable.update { it.copy(issue = issue, error = null) }
            return
        }
        mutable.update { it.copy(saving = true, issue = null, error = null) }
        viewModelScope.launch {
            val draft =
                ScheduleDraft(
                    prompt = input.prompt,
                    projectPath = input.project,
                    recurrence = recurrence,
                    workspace = workspace,
                    sessionId = sessionId,
                    agent = agent,
                    modelProviderId = input.model?.providerId,
                    modelId = input.model?.modelId,
                    missedPolicy = input.missed,
                )
            when (engine.save(profile, draft, editing)) {
                is Outcome.Ok -> {
                    mutable.update { it.copy(saving = false) }
                    nav.back()
                }
                is Outcome.Failed ->
                    mutable.update { it.copy(saving = false, error = engine.state(profile).value.error, fields = input) }
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SchedulesRoute.Edit): ScheduleEditViewModel
    }
}
