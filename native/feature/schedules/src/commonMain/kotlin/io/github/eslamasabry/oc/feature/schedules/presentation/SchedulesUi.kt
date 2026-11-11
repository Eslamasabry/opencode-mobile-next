package io.github.eslamasabry.oc.feature.schedules.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.schedules.MissedRunPolicy
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.engine.schedules.ScheduleCode
import io.github.eslamasabry.oc.engine.schedules.ScheduledRun
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the app knows about one server for schedules (Flutter `canSchedulePrompts` and the seeds).
 */
public data class ScheduleTarget(
    val serverName: String?,
    /**
     * ServerCapabilities.scheduledPrompts of the live connection (false when it cannot be opened).
     */
    val available: Boolean,
    /**
     * The project of the newest conversation: the default for a new schedule opened outside a
     * conversation.
     */
    val recentProject: String?,
)

public data class ModelChoice(val providerId: String, val modelId: String, val label: String)

/**
 * The conversation a schedule was opened from: a new schedule keeps it, its project and its model
 * (B-NAV-055).
 */
public data class ConversationSeed(
    val directory: String?,
    val title: String?,
    val providerId: String?,
    val modelId: String?,
    val agent: String?,
)

/** App-provided server facts (the wiring reads the live connection; never credentials). */
public interface ScheduleServers {
    public suspend fun target(profileId: ProfileId): ScheduleTarget

    public suspend fun models(profileId: ProfileId): List<ModelChoice>

    public suspend fun conversation(ref: ConversationRef): ConversationSeed?

    /** What the phone lets timed runs do right now (Flutter `platformStatus`); read again on return from settings. */
    public suspend fun timing(): ScheduleTiming = ScheduleTiming()

    /** Opens the phone's own page for exact alarms (Flutter `requestExactAlarm`); a no-op where none is needed. */
    public fun allowExactTiming() {}
}

/** Whether runs can start on the minute, and whether the phone would show the result notice. */
public data class ScheduleTiming(val exactAllowed: Boolean = true, val notificationsAllowed: Boolean = true)

// ---- K01 list

public enum class SchedulesPhase {
    Loading,
    Unavailable,
    Empty,
    List,
}

public enum class Unavailable {
    Server,
    Phone,
    Storage,
}

public data class LastRunUi(
    val status: RunStatus,
    val errorCode: String?,
    val atMs: Long,
    val sessionId: String?,
    val late: Boolean,
)

/** A run as its row and the editor's last-run line read it. */
internal fun ScheduledRun.toLastRunUi(): LastRunUi =
    LastRunUi(status, errorCode, finishedAtMs ?: startedAtMs, sessionId, late)

public data class ScheduleRowUi(
    val id: String,
    /** The prompt's first line. */
    val prompt: String,
    val recurrence: Recurrence,
    val nextRunAtMs: Long?,
    val enabled: Boolean,
    /** True when it continues one conversation instead of starting a new one each time. */
    val continuesConversation: Boolean,
    val last: LastRunUi?,
)

public data class SchedulesUi(
    val phase: SchedulesPhase = SchedulesPhase.Loading,
    val unavailable: Unavailable? = null,
    val serverName: String? = null,
    val rows: ImmutableList<ScheduleRowUi> = persistentListOf(),
    val highlight: String? = null,
    val digestOn: Boolean = false,
    val error: ScheduleCode? = null,
    val confirmDelete: String? = null,
    /** The schedule whose "Run now" was just started (a plain acknowledgement line). */
    val started: String? = null,
    val busy: Boolean = false,
    /** The clock reading the words are relative to ("Today 09:00"). */
    val nowMs: Long = 0,
    /** Cache-first: the rows are the last ones seen and the live read is still on its way (a quiet bar, no skeleton). */
    val updating: Boolean = false,
    /** False when the phone holds timed runs back: the list says they may start late (G2). */
    val exactAllowed: Boolean = true,
    /** False when the phone hides notifications: the list says a run will not announce itself. */
    val notificationsAllowed: Boolean = true,
)

public interface SchedulesActions {
    public fun back()

    public fun create()

    public fun edit(id: String)

    public fun setEnabled(id: String, enabled: Boolean)

    public fun runNow(id: String)

    public fun askDelete(id: String)

    public fun confirmDelete()

    public fun cancelDelete()

    public fun openConversation(sessionId: String)

    public fun setDigest(enabled: Boolean)

    public fun retry()

    /** Sends the person to the phone's page for exact timing. */
    public fun allowExactTiming() {}

    /** The person came back from the phone's settings: read what is allowed again. */
    public fun refreshTiming() {}
}

// ---- K02 editor

public enum class EditPhase {
    Loading,
    Unavailable,
    Ready,
}

public enum class EditIssue {
    PromptRequired,
    ProjectRequired,
    TimeInvalid,
    DateInvalid,
    HoursInvalid,
    FutureRequired,
}

/** The editor's starting values; [seed] changes when they are (re)loaded. */
public data class EditorFields(
    val prompt: String = "",
    val project: String = "",
    val kind: RecurrenceKind = RecurrenceKind.Once,
    val time: String = "09:00",
    val date: String = "",
    val hours: String = "3",
    val thisConversation: Boolean = true,
    val missed: MissedRunPolicy = MissedRunPolicy.CatchUp,
    val model: ModelChoice? = null,
)

public data class ScheduleEditUi(
    val phase: EditPhase = EditPhase.Loading,
    val editing: Boolean = false,
    val fields: EditorFields = EditorFields(),
    val seed: Int = 0,
    val models: ImmutableList<ModelChoice> = persistentListOf(),
    /** Set when the schedule continues a conversation. */
    val conversationTitle: String? = null,
    val continuesConversation: Boolean = false,
    val canChooseConversation: Boolean = false,
    val issue: EditIssue? = null,
    val error: ScheduleCode? = null,
    val saving: Boolean = false,
    val nowMs: Long = 0,
    /** B-NAV-055: the schedule's last run, with what happened (Flutter schedule_sheet's last-run row). */
    val lastRun: LastRunUi? = null,
    /** Cache-first: the form shows the last-seen facts while the live ones load (a quiet bar, no skeleton). */
    val updating: Boolean = false,
)

public interface ScheduleEditActions {
    public fun back()

    public fun save(input: EditorFields)

    /**
     * The typed fields changed: kept by the view model (rotation) and the prompt as a draft
     * (B-STATE-024).
     */
    public fun edited(input: EditorFields) {}
}
