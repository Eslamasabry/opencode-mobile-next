package io.github.eslamasabry.oc.engine.transcript.state

/*
 * P3 (slice 0) throwaway copies of the transcript vocabulary. Their homes are fixed by the plan and owned by others:
 * MessageInfo / Part / ToolStatus belong in :core:model (plan 09 §2.2), GatewayEvent in :core:domain (plan 04 §11.2).
 * They live here, trimmed to what the reducer proof needs, so slice 0 does not edit shared core modules. Field names
 * follow the plan so slice 2 can swap the import and keep the reducer.
 */

public enum class Role { User, Assistant }

public data class MessageInfo(
    val id: String,
    val sessionId: String,
    val role: Role,
    val createdMs: Long? = null,
    val completedMs: Long? = null,
    val finish: String? = null,
    val errorText: String? = null,
)

/** Switch/Notice/Compaction = the Dart "v2:*" tags (plan 09 §2.2, trap T9). */
public enum class PartType { Text, Reasoning, Tool, File, StepStart, StepFinish, Switch, Notice, Compaction, Unknown }

public enum class ToolStatus {
    Pending, Running, Completed, Error, Cancelled;

    public companion object {
        /** Unknown → Running (trap T3). */
        public fun parse(raw: String?): ToolStatus =
            when (raw) {
                "pending" -> Pending
                "completed" -> Completed
                "error" -> Error
                "cancelled" -> Cancelled
                else -> Running
            }
    }
}

public data class Part(
    val id: String,
    val messageId: String,
    val sessionId: String,
    val type: PartType,
    val text: String = "",
    val toolName: String? = null,
    val toolStatus: ToolStatus? = null,
    val synthetic: Boolean = false,
    val ended: Boolean = false,
    val searchText: String = "",
) {
    /** Dart Part.isRenderable (plan 09 §2.2). */
    public val isRenderable: Boolean
        get() =
            !synthetic &&
                when (type) {
                    PartType.Text, PartType.Reasoning -> text.isNotBlank()
                    PartType.Tool, PartType.File -> true
                    else -> false
                }
}

public data class MessageWithParts(val info: MessageInfo, val parts: List<Part>)

public enum class DeltaField { Text, Reasoning, ToolInputRaw }

public enum class NoticeKind { InstructionsUpdated, SkillChanged, ShellChanged, Compaction, Other }

public sealed interface RunOutcome {
    public data object Succeeded : RunOutcome

    public data class Interrupted(val reason: String?) : RunOutcome

    public data class Failed(val kind: String?) : RunOutcome
}

/** The subset of plan 04 §11.2 that the transcript reducer consumes (§3.3 apply table). */
public sealed interface GatewayEvent {
    public data object Connected : GatewayEvent

    public data class RunStarted(val sessionId: String) : GatewayEvent

    public data class RunEnded(val sessionId: String, val outcome: RunOutcome) : GatewayEvent

    public data class MessageUpserted(val message: MessageInfo) : GatewayEvent

    public data class PartUpserted(val part: Part) : GatewayEvent

    public data class PartDelta(
        val sessionId: String,
        val messageId: String,
        val partId: String,
        val field: DeltaField,
        val delta: String,
    ) : GatewayEvent

    public data class TranscriptStale(val sessionId: String) : GatewayEvent

    /** Server instruction / skill / shell text is never shown (trap T10); the notice carries only its kind. */
    public data class Notice(val sessionId: String, val messageId: String, val noticeId: String, val kind: NoticeKind) :
        GatewayEvent
}

/** The session an event belongs to; null = not session-scoped (Connected). */
public val GatewayEvent.sessionIdOrNull: String?
    get() =
        when (this) {
            GatewayEvent.Connected -> null
            is GatewayEvent.RunStarted -> sessionId
            is GatewayEvent.RunEnded -> sessionId
            is GatewayEvent.MessageUpserted -> message.sessionId
            is GatewayEvent.PartUpserted -> part.sessionId
            is GatewayEvent.PartDelta -> sessionId
            is GatewayEvent.TranscriptStale -> sessionId
            is GatewayEvent.Notice -> sessionId
        }
