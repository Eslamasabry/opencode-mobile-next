package io.github.eslamasabry.oc.protocol.oc2.events

import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AssistantContent
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Delivery
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ForkBoundary
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Location
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelRef
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2PermissionRequest
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2StructuredError
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Tokens
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolResultItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/** The lifecycle step a multi-type event stands for (`session.text.delta` → [Delta]). */
public enum class Oc2Phase {
    Started, Delta, Ended, Streamed, Succeeded, Failed, Interrupted, Success, Enqueued, Delivered, Cancelled,
    DeliveryChanged, Staged, Cleared, Committed,
}

public enum class Oc2SessionRunStatus {
    Idle, Busy, Retry, Unknown;

    public companion object {
        public fun parse(type: String?): Oc2SessionRunStatus =
            when (type) {
                "idle" -> Idle
                "busy" -> Busy
                "retry" -> Retry
                else -> Unknown
            }
    }
}

private val NoData: JsonObject = JsonObject(emptyMap())

/**
 * The typed OpenCode 2 event union of `GET /api/event` and the per-session durable log, ported one to one from
 * `lib/api2/events.dart`. The beta adds event types between builds, so decoding never throws: anything unrecognised
 * or malformed is [Unknown] with its raw JSON. Phase-carrying variants (`session.text.*` …) hold the [Oc2Phase] the
 * type string names; it is not on the wire.
 */
public sealed interface Oc2Event {
    public data object ServerConnected : Oc2Event

    /** `shell.created|exited|deleted`: freshness only, not a durable job log. */
    public data class ManagedShell(public val type: String, public val data: JsonObject) : Oc2Event

    /** The replay/live boundary marker on the durable session log (sent bare, without an envelope). */
    @Serializable
    public data class LogSynced(public val aggregateID: String? = null, public val seq: Long? = null) : Oc2Event

    @Serializable
    public data class SessionCreated(
        public val sessionID: String = "",
        public val projectID: String? = null,
        public val parentID: String? = null,
        public val slug: String? = null,
        public val title: String? = null,
        public val agent: String? = null,
        public val model: Oc2ModelRef? = null,
        public val location: Oc2Location? = null,
        public val subpath: String? = null,
        public val version: String? = null,
    ) : Oc2Event

    @Serializable
    public data class SessionRenamed(public val sessionID: String = "", public val title: String = "") : Oc2Event

    @Serializable
    public data class SessionDeleted(public val sessionID: String = "") : Oc2Event

    @Serializable
    public data class SessionMoved(
        public val sessionID: String = "",
        public val location: Oc2Location? = null,
        public val projectID: String? = null,
        public val subpath: String? = null,
    ) : Oc2Event

    @Serializable
    public data class SessionForked(
        public val sessionID: String = "",
        public val parentID: String? = null,
        public val boundary: Oc2ForkBoundary? = null,
    ) : Oc2Event

    /** [idle] is the epoch-ms idle watermark marked as seen. */
    @Serializable
    public data class SessionViewed(public val sessionID: String = "", public val idle: Long? = null) : Oc2Event

    @Serializable
    public data class SessionAgentSelected(
        public val sessionID: String = "",
        public val agent: String = "",
        public val previous: String? = null,
    ) : Oc2Event

    @Serializable
    public data class SessionModelSelected(public val sessionID: String = "", public val model: Oc2ModelRef? = null) :
        Oc2Event

    /** Running totals (ephemeral). */
    @Serializable
    public data class SessionUsageUpdated(
        public val sessionID: String = "",
        public val cost: Double = 0.0,
        public val tokens: Oc2Tokens = Oc2Tokens(),
    ) : Oc2Event

    /** `session.execution.started|succeeded|failed|interrupted`; [reason] is `user|inactivity|shutdown|superseded`. */
    @Serializable
    public data class SessionExecution(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val error: Oc2StructuredError? = null,
        public val reason: String? = null,
    ) : Oc2Event {
        public val finished: Boolean get() = phase != Oc2Phase.Started
    }

    /**
     * `session.inbox.enqueued|delivered|cancelled|delivery.changed`. The item gets the event's `inboxID` and
     * `sessionID`; [delivery] is read from the event, or from the item.
     */
    public data class SessionInbox(
        public val phase: Oc2Phase,
        public val sessionID: String,
        public val inboxID: String,
        public val item: Oc2InboxItem?,
        public val delivery: Oc2Delivery?,
    ) : Oc2Event

    /** [delta] maps instruction keys to a hash or `"removed"`; the instruction text is never shown. */
    @Serializable
    public data class SessionInstructionsUpdated(
        public val sessionID: String = "",
        public val delta: JsonObject = NoData,
        public val text: String? = null,
    ) : Oc2Event

    @Serializable
    public data class SessionSynthetic(
        public val sessionID: String = "",
        public val text: String = "",
        public val description: String? = null,
    ) : Oc2Event

    @Serializable
    public data class SessionSkillActivated(
        public val sessionID: String = "",
        public val id: String? = null,
        public val name: String? = null,
        public val text: String? = null,
    ) : Oc2Event

    /** `session.shell.started|ended`; shell and output kept raw. */
    @Serializable
    public data class SessionShell(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val shell: JsonObject? = null,
        public val output: JsonObject? = null,
    ) : Oc2Event

    /** `session.step.started|streamed|ended|failed`. */
    @Serializable
    public data class SessionStep(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        public val agent: String? = null,
        public val model: Oc2ModelRef? = null,
        public val finish: String? = null,
        public val error: Oc2StructuredError? = null,
        public val cost: Double? = null,
        public val tokens: Oc2Tokens? = null,
        public val snapshot: String? = null,
    ) : Oc2Event

    /** `session.text.started|delta|ended`; `ended` carries the full [text], the replayable boundary. */
    @Serializable
    public data class SessionText(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        public val ordinal: Int = 0,
        public val delta: String? = null,
        public val text: String? = null,
    ) : Oc2Event

    /** `session.reasoning.started|delta|ended`. */
    @Serializable
    public data class SessionReasoning(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        public val ordinal: Int = 0,
        public val delta: String? = null,
        public val text: String? = null,
    ) : Oc2Event

    /** `session.tool.input.started|delta|ended`: raw JSON-argument text. [callID] is the wire `id`. */
    @Serializable
    public data class SessionToolInput(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        @SerialName("id") public val callID: String = "",
        public val name: String? = null,
        public val delta: String? = null,
        public val text: String? = null,
    ) : Oc2Event

    @Serializable
    public data class SessionToolCalled(
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        @SerialName("id") public val callID: String = "",
        public val input: JsonObject = NoData,
        public val executed: Boolean? = null,
    ) : Oc2Event

    /** Live replacement metadata (ephemeral). */
    @Serializable
    public data class SessionToolProgress(
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        @SerialName("id") public val callID: String = "",
        public val metadata: JsonObject = NoData,
    ) : Oc2Event

    /** `session.tool.success|failed` with the tool-result content array. */
    @Serializable
    public data class SessionToolResult(
        @Transient public val phase: Oc2Phase = Oc2Phase.Success,
        public val sessionID: String = "",
        public val assistantMessageID: String = "",
        @SerialName("id") public val callID: String = "",
        public val content: List<Oc2ToolResultItem> = emptyList(),
        public val error: Oc2StructuredError? = null,
        public val metadata: JsonObject? = null,
        public val executed: Boolean? = null,
    ) : Oc2Event {
        public val succeeded: Boolean get() = phase == Oc2Phase.Success
    }

    @Serializable
    public data class SessionRetryScheduled(
        public val sessionID: String = "",
        public val assistantMessageID: String? = null,
        public val attempt: Int? = null,
        public val at: Long? = null,
        public val error: Oc2StructuredError? = null,
    ) : Oc2Event

    /** `session.compaction.started|delta|ended|failed`. */
    @Serializable
    public data class SessionCompaction(
        @Transient public val phase: Oc2Phase = Oc2Phase.Started,
        public val sessionID: String = "",
        public val reason: String? = null,
        public val text: String? = null,
        public val error: Oc2StructuredError? = null,
    ) : Oc2Event

    /** `session.revert.staged|cleared|committed`; [toMessageID] is the wire `to`. */
    @Serializable
    public data class SessionRevert(
        @Transient public val phase: Oc2Phase = Oc2Phase.Staged,
        public val sessionID: String = "",
        public val revert: JsonObject? = null,
        @SerialName("to") public val toMessageID: String? = null,
    ) : Oc2Event

    /** A message edited via PATCH. */
    @Serializable
    public data class SessionMessageContentUpdated(
        public val sessionID: String = "",
        public val messageID: String = "",
        public val content: List<Oc2AssistantContent> = emptyList(),
    ) : Oc2Event

    /** `session.status` (and the deprecated `session.idle`); [raw] is the `status` object. */
    public data class SessionStatus(
        public val sessionID: String,
        public val status: Oc2SessionRunStatus,
        public val attempt: Int? = null,
        public val message: String? = null,
        public val next: Long? = null,
        public val raw: JsonObject = NoData,
    ) : Oc2Event

    /** Declared through 2.0.x, never emitted on 2.0.29, removed in 2.1. */
    @Serializable
    public data class SessionCompacted(public val sessionID: String = "") : Oc2Event

    public data class PermissionAsked(public val request: Oc2PermissionRequest) : Oc2Event

    @Serializable
    public data class PermissionReplied(
        public val sessionID: String = "",
        public val requestID: String = "",
        public val reply: String? = null,
    ) : Oc2Event

    public data class FormCreated(public val form: Oc2FormInfo) : Oc2Event

    @Serializable
    public data class FormReplied(
        public val id: String = "",
        public val sessionID: String = "",
        public val answer: JsonObject = NoData,
    ) : Oc2Event

    @Serializable
    public data class FormCancelled(public val id: String = "", public val sessionID: String = "") : Oc2Event

    /** `mcp.status|resources|tools.changed`; [kind] is the middle word. */
    public data class McpChanged(public val kind: String, public val server: String?) : Oc2Event

    @Serializable
    public data class VcsBranchUpdated(public val branch: String? = null) : Oc2Event

    /** [change] is the wire `event`: `add|change|unlink`. */
    @Serializable
    public data class FilesystemChanged(
        public val file: String? = null,
        @SerialName("event") public val change: String? = null,
    ) : Oc2Event

    @Serializable
    public data class TuiToast(
        public val title: String? = null,
        public val message: String = "",
        public val variant: String? = null,
        public val duration: Long? = null,
    ) : Oc2Event

    /** A catalog or config ping meaning "refetch [topic]" (the event type). */
    public data class RefreshHint(public val topic: String) : Oc2Event

    /**
     * Plan 04 §7.5 "pass-through by name": `installation.*`, `pty.*`, `worktree.ready|failed`, `workspace.ready|failed`.
     * The payload is kept raw for the adapter (exact installation versions, B-PROTO-047). The Dart client reads these
     * as unknown events.
     */
    public data class PassThrough(public val type: String, public val raw: JsonObject) : Oc2Event

    /**
     * A type this client does not read, or a typed event whose payload is unusable. [ignored] marks the types plan
     * 04 §7.5 lists as known-and-ignored (counted, not logged as new).
     */
    public data class Unknown(public val type: String, public val raw: JsonObject, public val ignored: Boolean = false) :
        Oc2Event
}
