package io.github.eslamasabry.oc.feature.schedules.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the scheduled prompts open on the rows they last showed, then the live read
 * replaces them in place. The app binds this to its last-known store; without a binding the list loads as before.
 */
public interface SchedulesLastKnown {
    public fun peek(profile: ProfileId): KeptSchedules?

    public suspend fun read(profile: ProfileId): KeptSchedules?

    public suspend fun keep(profile: ProfileId, kept: KeptSchedules)
}

/** The list's display facts: the first line of each prompt, its time and its last run. Never a server address. */
@Serializable
public data class KeptSchedules(
    val serverName: String? = null,
    val digestOn: Boolean = false,
    val rows: List<Row> = emptyList(),
) {
    @Serializable
    public data class Row(
        val id: String,
        val prompt: String,
        val kind: String,
        val atMs: Long? = null,
        val hour: Int = 8,
        val minute: Int = 0,
        val hours: Int = 1,
        val nextRunAtMs: Long? = null,
        val enabled: Boolean,
        val continues: Boolean,
        val lastStatus: String? = null,
        val lastErrorCode: String? = null,
        val lastAtMs: Long? = null,
        val lastSessionId: String? = null,
        val lastLate: Boolean = false,
    )

    internal fun toRows(): List<ScheduleRowUi> =
        rows.mapNotNull { r ->
            val kind = RecurrenceKind.of(r.kind) ?: return@mapNotNull null
            val status = r.lastStatus?.let(RunStatus::of)
            ScheduleRowUi(
                id = r.id,
                prompt = r.prompt,
                recurrence = Recurrence(kind, r.atMs, r.hour, r.minute, r.hours),
                nextRunAtMs = r.nextRunAtMs,
                enabled = r.enabled,
                continuesConversation = r.continues,
                last = status?.let { LastRunUi(it, r.lastErrorCode, r.lastAtMs ?: 0L, r.lastSessionId, r.lastLate) },
            )
        }

    public companion object {
        public const val MAX_ROWS: Int = 100
        private const val MAX_PROMPT: Int = 200

        internal fun of(ui: SchedulesUi): KeptSchedules =
            KeptSchedules(
                serverName = ui.serverName,
                digestOn = ui.digestOn,
                rows =
                    ui.rows.take(MAX_ROWS).map { r ->
                        Row(
                            id = r.id,
                            prompt = r.prompt.take(MAX_PROMPT),
                            kind = r.recurrence.kind.wire,
                            atMs = r.recurrence.atMs,
                            hour = r.recurrence.hour,
                            minute = r.recurrence.minute,
                            hours = r.recurrence.hours,
                            nextRunAtMs = r.nextRunAtMs,
                            enabled = r.enabled,
                            continues = r.continuesConversation,
                            lastStatus = r.last?.status?.wire,
                            lastErrorCode = r.last?.errorCode,
                            lastAtMs = r.last?.atMs,
                            lastSessionId = r.last?.sessionId,
                            lastLate = r.last?.late ?: false,
                        )
                    },
            )

        /** The first frame: the kept rows as a list (or the empty state), updating quietly. */
        internal fun frame(kept: KeptSchedules, highlight: String?): SchedulesUi {
            val rows = kept.toRows().toImmutableList()
            return SchedulesUi(
                phase = if (rows.isEmpty()) SchedulesPhase.Empty else SchedulesPhase.List,
                serverName = kept.serverName,
                rows = rows,
                highlight = highlight,
                digestOn = kept.digestOn,
                updating = true,
            )
        }
    }
}
