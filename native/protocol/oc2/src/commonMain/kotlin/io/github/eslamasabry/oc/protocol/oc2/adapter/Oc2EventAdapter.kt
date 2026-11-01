package io.github.eslamasabry.oc.protocol.oc2.adapter

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Event
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Phase
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2SessionRunStatus
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AssistantContent
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Delivery
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Location
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelRef
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2PermissionRequest
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Tokens
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolResultItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ================================================================================================================
// TODO(move-to-core): plan 04 §11.2 puts `GatewayEvent` in :core:domain, and the chat section puts `MessageInfo`,
// `Part` and `ToolState` in :core:model. S0-CORE has not landed them, so this file declares the minimal local shapes
// the adapter needs. When core lands: delete everything above `Oc2EventAdapter`, import the core types, and keep the
// mapping rules below unchanged (Opus reviews the adapter, plan 15 S1-OC2). The names follow §11.2 where it has one.
// ================================================================================================================

public enum class MessageRole { User, Assistant }

/** Error words for Details. [message] is null when the server sent none: the UI words it from [type] and [status]. */
public data class StepError(public val message: String?, public val type: String?, public val status: Int?)

public data class MessageInfo(
    public val id: String,
    public val sessionId: String,
    public val role: MessageRole,
    public val createdMs: Long,
    public val completedMs: Long? = null,
    public val agent: String? = null,
    public val providerId: String? = null,
    public val modelId: String? = null,
    public val cost: Double? = null,
    public val tokens: Oc2Tokens? = null,
    public val finish: String? = null,
    public val error: StepError? = null,
)

public enum class ToolStatus { Pending, Running, Completed, Error }

public data class ToolAttachment(public val url: String, public val mime: String?, public val name: String?)

public data class ToolPart(
    public val callId: String,
    public val name: String?,
    public val status: ToolStatus,
    /** Raw JSON argument text while the call is still being written (pending). */
    public val raw: String? = null,
    public val input: JsonObject? = null,
    public val output: String? = null,
    /** Server error text; null with [ToolStatus.Error] means "failed" without words from the server. */
    public val error: String? = null,
    /** Set when the adapter settled the call itself because its result was lost: the UI words it. */
    public val settledBy: SettleReason? = null,
    public val attachments: List<ToolAttachment> = emptyList(),
    public val metadata: JsonObject? = null,
    public val executed: Boolean? = null,
)

public enum class SettleReason { StepMovedOn, StepFailed, RunFailed, RunInterrupted }

public enum class PartKind { Text, Reasoning, Tool, Notice }

public enum class NoticeKind { InstructionsUpdated, SkillChanged, ShellChanged }

/** Part ids match the refetch mapper: `text-<n>`, `reasoning-<n>`, the call id for tools, `v2-0` for notices. */
public data class Part(
    public val sessionId: String,
    public val messageId: String,
    public val partId: String,
    public val kind: PartKind,
    public val text: String? = null,
    public val endedAtMs: Long? = null,
    public val tool: ToolPart? = null,
    public val notice: NoticeKind? = null,
)

public enum class DeltaField { Text, Reasoning, ToolInputRaw }

public sealed interface RunOutcome {
    public data object Succeeded : RunOutcome

    /** [reason] is the server's (`user|inactivity|shutdown|superseded`), or `unknown`. */
    public data class Interrupted(public val reason: String) : RunOutcome

    /** [message] is null when blank: the UI words it from [type]. */
    public data class Failed(public val type: String?, public val message: String?) : RunOutcome
}

public enum class RevertKind { Staged, Cleared, Committed }

public enum class InboxChange { Enqueued, Delivered, Cancelled, DeliveryChanged }

/** Refresh bursts share one reload downstream. */
public enum class HintKind {
    Agents, Commands, Config, Skills, Catalog, Integrations, Plugins, References, WebSearch, Projects, Worktrees, Lsp,
    Mcp, Vcs, Files,
}

public enum class InstallationKind { Available, Installed }

public enum class TerminalChange { Created, Updated, Exited, Deleted }

public sealed interface GatewayEvent {
    public data object Connected : GatewayEvent

    /** `session.created`: everything the event carries; never a full fetched session. */
    public data class SessionUpserted(
        public val sessionId: String,
        public val title: String?,
        public val projectId: String?,
        public val parentId: String?,
        public val location: Oc2Location?,
        public val subpath: String?,
        public val model: Oc2ModelRef?,
        public val agent: String?,
        public val createdMs: Long,
    ) : GatewayEvent

    /** A rename or move. A move replaces both [directory] and [workspaceId] ([replacesLocation]). */
    public data class SessionChanged(
        public val sessionId: String,
        public val title: String? = null,
        public val directory: String? = null,
        public val workspaceId: String? = null,
        public val replacesLocation: Boolean = false,
        public val projectId: String? = null,
        public val subpath: String? = null,
        public val replacesSubpath: Boolean = false,
    ) : GatewayEvent

    public data class SessionDeleted(public val sessionId: String) : GatewayEvent

    public data class SessionSelection(public val sessionId: String, public val model: Oc2ModelRef?, public val agent: String?) :
        GatewayEvent

    /** Merged into the stored session in place, never a full replace. */
    public data class SessionUsage(public val sessionId: String, public val cost: Double, public val tokens: Oc2Tokens) :
        GatewayEvent

    public data class SessionViewed(public val sessionId: String, public val idleMs: Long?) : GatewayEvent

    public data class RunStarted(public val sessionId: String) : GatewayEvent

    /** `session.idle` / status idle; stable servers send it after the run end (plan 04 §7.6: ignored downstream). */
    public data class RunIdle(public val sessionId: String) : GatewayEvent

    public data class RunRetrying(
        public val sessionId: String,
        public val attempt: Int,
        public val nextAtMs: Long?,
        /** Shown only under Details. */
        public val message: String?,
    ) : GatewayEvent

    public data class RunEnded(public val sessionId: String, public val outcome: RunOutcome) : GatewayEvent

    public data class MessageUpserted(public val message: MessageInfo) : GatewayEvent

    public data class PartUpserted(public val part: Part) : GatewayEvent

    public data class PartDelta(
        public val sessionId: String,
        public val messageId: String,
        public val partId: String,
        public val field: DeltaField,
        public val delta: String,
    ) : GatewayEvent

    /** "Read the transcript again for the real result." */
    public data class TranscriptStale(public val sessionId: String) : GatewayEvent

    public data class Notice(public val sessionId: String, public val kind: NoticeKind, public val messageId: String?) :
        GatewayEvent

    public data class RevertChanged(
        public val sessionId: String,
        public val kind: RevertKind,
        public val revert: JsonObject?,
        public val toMessageId: String?,
    ) : GatewayEvent

    public data class SessionCompacted(public val sessionId: String) : GatewayEvent

    /** [callId] is the request's `source.id`. Asks are never replayed: refetch on reconnect. */
    public data class PermissionAsked(public val request: Oc2PermissionRequest, public val callId: String?) : GatewayEvent

    public data class PermissionSettled(public val sessionId: String, public val requestId: String, public val reply: String?) :
        GatewayEvent

    public data class FormCreated(public val form: Oc2FormInfo) : GatewayEvent

    public data class FormSettled(public val sessionId: String, public val formId: String, public val answered: Boolean) :
        GatewayEvent

    public data class InboxChanged(
        public val sessionId: String,
        public val inboxId: String,
        public val change: InboxChange,
        public val delivery: Oc2Delivery?,
    ) : GatewayEvent

    public data class RefreshHint(public val kind: HintKind) : GatewayEvent

    public data class CredentialSwitched(public val integrationId: String, public val credentialId: String?) : GatewayEvent

    public data class Installation(public val kind: InstallationKind, public val version: String) : GatewayEvent

    public data class TerminalChanged(public val ptyId: String?, public val kind: TerminalChange) : GatewayEvent

    public data class ShellChanged(public val shellId: String?) : GatewayEvent

    /** `worktree.ready|failed` and `workspace.ready|failed`: freshness hints only. */
    public data class WorktreeOutcome(public val ready: Boolean, public val workspace: Boolean) : GatewayEvent
}

// ================================================================================================================
// The adapter: lib/api2/gateway_events.dart Api2EventAdapter, ported rule for rule onto GatewayEvent.
// ================================================================================================================

/**
 * Stateful per connection graph (one instance per event stream, plan 04 §6.5). Remembers, for at most
 * [MAX_TRACKED] entries each (oldest evicted, B-CHAT-005): open tool calls, step start times and the agent and model
 * a step started with (`step.ended|failed` do not repeat them).
 *
 * [nowMs] stands in for an envelope without `created` and stamps ended parts (inject the app Clock).
 */
public class Oc2EventAdapter(private val nowMs: () -> Long) {
    private class ToolCall(val sessionId: String, val name: String, val input: JsonObject?)

    private class StepModel(val agent: String?, val model: Oc2ModelRef?)

    private val toolCalls = LinkedHashMap<String, ToolCall>()
    private val stepCreated = LinkedHashMap<String, Long>()
    private val stepModels = LinkedHashMap<String, StepModel>()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    public fun adapt(envelope: Oc2Envelope): List<GatewayEvent> {
        val created = envelope.created ?: nowMs()
        return when (val e = envelope.event) {
            is Oc2Event.ServerConnected -> listOf(GatewayEvent.Connected)
            // Running-work surfaces reconcile from the scoped shell API; never synthetic messages or retained jobs.
            is Oc2Event.ManagedShell -> listOf(GatewayEvent.ShellChanged(e.data.idOf()))
            is Oc2Event.SessionShell -> listOf(GatewayEvent.Notice(e.sessionID, NoticeKind.ShellChanged, null))
            // Rehydrate the canonical, redacted skill row; never expose event text.
            is Oc2Event.SessionSkillActivated -> listOf(GatewayEvent.Notice(e.sessionID, NoticeKind.SkillChanged, null))
            is Oc2Event.SessionCreated ->
                listOf(
                    GatewayEvent.SessionUpserted(
                        e.sessionID, e.title, e.projectID, e.parentID, e.location, e.subpath, e.model, e.agent, created,
                    ),
                )
            is Oc2Event.SessionRenamed -> listOf(GatewayEvent.SessionChanged(e.sessionID, title = e.title))
            is Oc2Event.SessionDeleted -> listOf(GatewayEvent.SessionDeleted(e.sessionID))
            is Oc2Event.SessionMoved ->
                listOf(
                    GatewayEvent.SessionChanged(
                        sessionId = e.sessionID,
                        // A supplied destination replaces both fields: moving back to a directory clears a workspace.
                        directory = e.location?.directory,
                        workspaceId = e.location?.workspaceID,
                        replacesLocation = e.location != null,
                        projectId = e.projectID,
                        subpath = e.subpath,
                        replacesSubpath = e.subpath != null || "subpath" in envelope.data,
                    ),
                )
            is Oc2Event.SessionModelSelected ->
                e.model?.let { listOf(GatewayEvent.SessionSelection(e.sessionID, it, null)) } ?: emptyList()
            is Oc2Event.SessionAgentSelected ->
                if (e.agent.isEmpty()) emptyList() else listOf(GatewayEvent.SessionSelection(e.sessionID, null, e.agent))
            is Oc2Event.SessionRevert ->
                listOf(GatewayEvent.RevertChanged(e.sessionID, e.phase.revertKind(), e.revert, e.toMessageID))
            is Oc2Event.SessionInstructionsUpdated -> instructions(envelope, e, created)
            is Oc2Event.SessionViewed -> listOf(GatewayEvent.SessionViewed(e.sessionID, e.idle))
            is Oc2Event.SessionStatus ->
                when (e.status) {
                    Oc2SessionRunStatus.Idle -> listOf(GatewayEvent.RunIdle(e.sessionID))
                    Oc2SessionRunStatus.Busy -> listOf(GatewayEvent.RunStarted(e.sessionID))
                    Oc2SessionRunStatus.Retry ->
                        listOf(GatewayEvent.RunRetrying(e.sessionID, e.attempt ?: 0, e.next, e.message))
                    Oc2SessionRunStatus.Unknown -> emptyList()
                }
            // No full-session replace: merged into the stored session in place.
            is Oc2Event.SessionUsageUpdated -> listOf(GatewayEvent.SessionUsage(e.sessionID, e.cost, e.tokens))
            is Oc2Event.SessionRetryScheduled ->
                listOf(GatewayEvent.RunRetrying(e.sessionID, e.attempt ?: 0, e.at, e.error?.message ?: e.error?.type))
            is Oc2Event.SessionExecution -> execution(e)
            is Oc2Event.SessionCompacted -> listOf(GatewayEvent.SessionCompacted(e.sessionID))
            is Oc2Event.SessionInbox -> inbox(e, created)
            is Oc2Event.SessionStep -> step(e, created)
            is Oc2Event.SessionText ->
                ordinalPart(e.phase, e.sessionID, e.assistantMessageID, "text-${e.ordinal}", PartKind.Text, e.delta, e.text)
            is Oc2Event.SessionReasoning ->
                ordinalPart(
                    e.phase, e.sessionID, e.assistantMessageID, "reasoning-${e.ordinal}", PartKind.Reasoning, e.delta, e.text,
                )
            is Oc2Event.SessionToolInput -> toolInput(e)
            is Oc2Event.SessionToolCalled -> {
                val key = callKey(e.assistantMessageID, e.callID)
                remember(toolCalls, key, ToolCall(e.sessionID, toolCalls[key]?.name ?: "", e.input))
                listOf(toolPart(e.sessionID, e.assistantMessageID, ToolPart(e.callID, null, ToolStatus.Running, input = e.input)))
            }
            is Oc2Event.SessionToolProgress -> {
                val call = toolCalls[callKey(e.assistantMessageID, e.callID)]
                val state = ToolPart(e.callID, null, ToolStatus.Running, input = call?.input ?: EmptyJson, metadata = e.metadata)
                listOf(toolPart(e.sessionID, e.assistantMessageID, state))
            }
            is Oc2Event.SessionToolResult -> toolResult(e)
            is Oc2Event.SessionMessageContentUpdated -> contentUpdated(e)
            is Oc2Event.PermissionAsked -> listOf(GatewayEvent.PermissionAsked(e.request, e.request.source?.id))
            is Oc2Event.PermissionReplied -> listOf(GatewayEvent.PermissionSettled(e.sessionID, e.requestID, e.reply))
            is Oc2Event.FormCreated -> listOf(GatewayEvent.FormCreated(e.form))
            is Oc2Event.FormReplied -> listOf(GatewayEvent.FormSettled(e.sessionID, e.id, answered = true))
            is Oc2Event.FormCancelled -> listOf(GatewayEvent.FormSettled(e.sessionID, e.id, answered = false))
            is Oc2Event.RefreshHint -> refresh(e.topic, envelope.data)
            // Plan 04 §7.6 widens Flutter here: these pings refresh their lists too (Flutter dropped them).
            is Oc2Event.McpChanged -> listOf(GatewayEvent.RefreshHint(HintKind.Mcp))
            is Oc2Event.VcsBranchUpdated -> listOf(GatewayEvent.RefreshHint(HintKind.Vcs))
            is Oc2Event.FilesystemChanged -> listOf(GatewayEvent.RefreshHint(HintKind.Files))
            is Oc2Event.PassThrough -> passThrough(e)
            // TODO(adapter review): plan 04 §7.6 wants `session.compaction.*` → a compaction notice part, but gives no
            // part shape; Flutter's adapter emits nothing and the refetch mapper shows the compaction message. Kept as
            // Flutter until the chat section freezes the notice part.
            is Oc2Event.SessionCompaction -> emptyList()
            // TUI control events and the durable-log marker are not for this client's transcript.
            is Oc2Event.TuiToast, is Oc2Event.LogSynced, is Oc2Event.SessionForked, is Oc2Event.SessionSynthetic,
            is Oc2Event.Unknown,
            -> emptyList()
        }
    }

    private fun instructions(envelope: Oc2Envelope, e: Oc2Event.SessionInstructionsUpdated, created: Long): List<GatewayEvent> {
        // The server's text/delta may hold server-owned entries: only a neutral notice, whose durable id matches the
        // later REST transcript (`evt_X` → `msg_X`). The instruction text is never shown.
        val eventId = envelope.id
        val messageId = if (eventId != null && eventId.startsWith("evt_") && eventId.length > 4) "msg_${eventId.substring(4)}" else null
        val out = mutableListOf<GatewayEvent>(GatewayEvent.Notice(e.sessionID, NoticeKind.InstructionsUpdated, messageId))
        if (e.text != null && messageId != null) {
            out += GatewayEvent.MessageUpserted(MessageInfo(messageId, e.sessionID, MessageRole.User, created))
            out += GatewayEvent.PartUpserted(
                Part(e.sessionID, messageId, "v2-0", PartKind.Notice, notice = NoticeKind.InstructionsUpdated),
            )
        }
        return out
    }

    private fun execution(e: Oc2Event.SessionExecution): List<GatewayEvent> =
        when (e.phase) {
            Oc2Phase.Started -> listOf(GatewayEvent.RunStarted(e.sessionID))
            // RunEnded(Failed) alone: an idle after it would overwrite the error settle.
            Oc2Phase.Failed ->
                settleOpenCalls(e.sessionID, failure = SettleReason.RunFailed) +
                    GatewayEvent.RunEnded(e.sessionID, RunOutcome.Failed(e.error?.type, e.error?.message?.trim()?.ifEmpty { null }))
            else -> {
                val interrupted = e.phase == Oc2Phase.Interrupted
                val outcome =
                    if (interrupted) RunOutcome.Interrupted(e.reason?.trim()?.ifEmpty { null } ?: "unknown") else RunOutcome.Succeeded
                settleOpenCalls(e.sessionID, failure = if (interrupted) SettleReason.RunInterrupted else null) +
                    GatewayEvent.RunEnded(e.sessionID, outcome)
            }
        }

    private fun inbox(e: Oc2Event.SessionInbox, created: Long): List<GatewayEvent> {
        val change =
            when (e.phase) {
                Oc2Phase.Delivered -> InboxChange.Delivered
                Oc2Phase.Cancelled -> InboxChange.Cancelled
                Oc2Phase.DeliveryChanged -> InboxChange.DeliveryChanged
                else -> InboxChange.Enqueued
            }
        val changed = GatewayEvent.InboxChanged(e.sessionID, e.inboxID, change, e.delivery)
        val item = e.item
        if (e.phase != Oc2Phase.Enqueued || item == null || item.type != "user") return listOf(changed)
        // A waiting prompt joins the transcript under its inbox id (the server echoes it later with that id).
        val text = item.promptText ?: ""
        return buildList {
            add(changed)
            add(GatewayEvent.MessageUpserted(MessageInfo(e.inboxID, e.sessionID, MessageRole.User, item.timeCreated ?: created)))
            if (text.isNotEmpty()) add(GatewayEvent.PartUpserted(Part(e.sessionID, e.inboxID, "text-0", PartKind.Text, text = text)))
        }
    }

    private fun step(e: Oc2Event.SessionStep, created: Long): List<GatewayEvent> =
        when (e.phase) {
            Oc2Phase.Started -> {
                remember(stepCreated, e.assistantMessageID, created)
                remember(stepModels, e.assistantMessageID, StepModel(e.agent, e.model))
                // A step starts only inside a running execution: this restores a busy state the app lost. Steps run
                // one after another, so calls of earlier steps have ended.
                listOf(GatewayEvent.RunStarted(e.sessionID)) +
                    settleOpenCalls(e.sessionID, exceptMessageId = e.assistantMessageID) +
                    GatewayEvent.MessageUpserted(assistantInfo(e, created))
            }
            // A step end never ends the run (B-PROTO-040).
            Oc2Phase.Ended ->
                settleOpenCalls(e.sessionID, onlyMessageId = e.assistantMessageID) +
                    GatewayEvent.MessageUpserted(assistantInfo(e, created, completed = created))
            Oc2Phase.Failed ->
                settleOpenCalls(e.sessionID, onlyMessageId = e.assistantMessageID, failure = SettleReason.StepFailed,
                    failureText = e.error?.message) +
                    GatewayEvent.MessageUpserted(
                        assistantInfo(e, created, completed = created, error = StepError(e.error?.message, e.error?.type, e.error?.status)),
                    )
            else -> emptyList() // `streamed` (provider body finished) changes nothing visible.
        }

    private fun assistantInfo(e: Oc2Event.SessionStep, created: Long, completed: Long? = null, error: StepError? = null): MessageInfo {
        val known = stepModels[e.assistantMessageID]
        val model = e.model ?: known?.model
        return MessageInfo(
            id = e.assistantMessageID,
            sessionId = e.sessionID,
            role = MessageRole.Assistant,
            createdMs = stepCreated[e.assistantMessageID] ?: created,
            completedMs = completed,
            agent = e.agent ?: known?.agent,
            providerId = model?.providerID,
            modelId = model?.id,
            cost = e.cost,
            tokens = e.tokens,
            finish = e.finish,
            error = error,
        )
    }

    private fun ordinalPart(
        phase: Oc2Phase,
        sessionId: String,
        messageId: String,
        partId: String,
        kind: PartKind,
        delta: String?,
        text: String?,
    ): List<GatewayEvent> =
        when (phase) {
            Oc2Phase.Started -> listOf(GatewayEvent.PartUpserted(Part(sessionId, messageId, partId, kind, text = "")))
            Oc2Phase.Delta -> {
                val field = if (kind == PartKind.Reasoning) DeltaField.Reasoning else DeltaField.Text
                listOf(GatewayEvent.PartDelta(sessionId, messageId, partId, field, delta ?: ""))
            }
            // A finished part (the reply's caret goes).
            Oc2Phase.Ended ->
                listOf(GatewayEvent.PartUpserted(Part(sessionId, messageId, partId, kind, text = text ?: "", endedAtMs = nowMs())))
            else -> emptyList()
        }

    private fun toolInput(e: Oc2Event.SessionToolInput): List<GatewayEvent> =
        when (e.phase) {
            Oc2Phase.Started -> {
                remember(toolCalls, callKey(e.assistantMessageID, e.callID), ToolCall(e.sessionID, e.name ?: "", null))
                listOf(toolPart(e.sessionID, e.assistantMessageID, ToolPart(e.callID, null, ToolStatus.Pending, raw = "")))
            }
            Oc2Phase.Delta ->
                listOf(GatewayEvent.PartDelta(e.sessionID, e.assistantMessageID, e.callID, DeltaField.ToolInputRaw, e.delta ?: ""))
            Oc2Phase.Ended ->
                listOf(toolPart(e.sessionID, e.assistantMessageID, ToolPart(e.callID, null, ToolStatus.Pending, raw = e.text ?: "")))
            else -> emptyList()
        }

    private fun toolResult(e: Oc2Event.SessionToolResult): List<GatewayEvent> {
        val call = toolCalls.remove(callKey(e.assistantMessageID, e.callID))
        val text = e.content.filterIsInstance<Oc2ToolResultItem.Text>().map { it.text }.filter { it.isNotEmpty() }.joinToString("\n")
        val attachments = e.content.filterIsInstance<Oc2ToolResultItem.File>().map { ToolAttachment(it.uri, it.mime, it.name) }
        val state =
            ToolPart(
                callId = e.callID,
                name = call?.name,
                status = if (e.succeeded) ToolStatus.Completed else ToolStatus.Error,
                input = call?.input ?: EmptyJson,
                output = if (e.succeeded) text else null,
                error = if (e.succeeded) null else e.error?.message ?: text.ifEmpty { null },
                attachments = attachments,
                metadata = e.metadata,
                executed = e.executed,
            )
        // A call that started before this stream (a reconnect): its name and input are not here, so reread.
        return listOfNotNull(
            toolPart(e.sessionID, e.assistantMessageID, state),
            if (call == null) GatewayEvent.TranscriptStale(e.sessionID) else null,
        )
    }

    /** A server-side message edit: every content item again, with the ids the message mapper assigns. */
    private fun contentUpdated(e: Oc2Event.SessionMessageContentUpdated): List<GatewayEvent> {
        var texts = 0
        var reasonings = 0
        return e.content.mapNotNull { item ->
            when (item) {
                is Oc2AssistantContent.Text ->
                    GatewayEvent.PartUpserted(Part(e.sessionID, e.messageID, "text-${texts++}", PartKind.Text, text = item.text))
                is Oc2AssistantContent.Reasoning ->
                    GatewayEvent.PartUpserted(
                        Part(e.sessionID, e.messageID, "reasoning-${reasonings++}", PartKind.Reasoning, text = item.text),
                    )
                is Oc2AssistantContent.Tool -> toolPart(e.sessionID, e.messageID, rawToolState(item))
                is Oc2AssistantContent.Unknown -> null
            }
        }
    }

    private fun rawToolState(item: Oc2AssistantContent.Tool): ToolPart =
        when (val s = item.state) {
            is Oc2ToolState.Streaming -> ToolPart(item.id, item.name, ToolStatus.Pending, raw = s.rawInput)
            is Oc2ToolState.Running -> ToolPart(item.id, item.name, ToolStatus.Running, input = s.input, metadata = s.metadata)
            is Oc2ToolState.Completed ->
                ToolPart(item.id, item.name, ToolStatus.Completed, input = s.input, output = s.textOutput, metadata = s.metadata)
            is Oc2ToolState.Error ->
                ToolPart(item.id, item.name, ToolStatus.Error, input = s.input, error = s.error?.message, metadata = s.metadata)
            is Oc2ToolState.Unknown -> ToolPart(item.id, item.name, ToolStatus.Pending)
        }

    private fun refresh(topic: String, data: JsonObject): List<GatewayEvent> {
        val kind =
            when (topic) {
                "agent.updated" -> HintKind.Agents
                "command.updated" -> HintKind.Commands
                "config.updated" -> HintKind.Config
                "skill.updated" -> HintKind.Skills
                "catalog.updated", "models-dev.refreshed" -> HintKind.Catalog
                "integration.updated", "credential.updated", "credential.switched" -> HintKind.Integrations
                "plugin.added", "plugin.updated" -> HintKind.Plugins
                "reference.updated" -> HintKind.References
                "websearch.updated" -> HintKind.WebSearch
                "project.updated" -> HintKind.Projects
                "worktree.updated" -> HintKind.Worktrees
                "lsp.updated" -> HintKind.Lsp
                else -> return emptyList()
            }
        if (topic != "credential.switched") return listOf(GatewayEvent.RefreshHint(kind))
        // Only an explicitly present null means "no active credential". Never forward credential payloads, and never
        // infer state from malformed data.
        val integration = (data["integrationID"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val credential = data["credentialID"]
        val credentialId = (credential as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val valid = integration != null && "credentialID" in data && (credential == JsonNull || credentialId != null)
        return listOfNotNull(
            if (valid) GatewayEvent.CredentialSwitched(integration, credentialId) else null,
            GatewayEvent.RefreshHint(HintKind.Integrations),
        )
    }

    private fun passThrough(e: Oc2Event.PassThrough): List<GatewayEvent> {
        val raw = e.raw
        return when (e.type) {
            "installation.updated", "installation.update-available" -> {
                // Exact versions kept (B-PROTO-047): no trimming, no parsing.
                val version = (raw["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return emptyList()
                val kind = if (e.type == "installation.updated") InstallationKind.Installed else InstallationKind.Available
                listOf(GatewayEvent.Installation(kind, version))
            }
            "pty.created" -> listOf(GatewayEvent.TerminalChanged(raw.infoIdOf(), TerminalChange.Created))
            "pty.updated" -> listOf(GatewayEvent.TerminalChanged(raw.infoIdOf(), TerminalChange.Updated))
            "pty.exited" -> listOf(GatewayEvent.TerminalChanged(raw.idOf(), TerminalChange.Exited))
            "pty.deleted" -> listOf(GatewayEvent.TerminalChanged(raw.idOf(), TerminalChange.Deleted))
            "worktree.ready" -> listOf(GatewayEvent.WorktreeOutcome(ready = true, workspace = false))
            "worktree.failed" -> listOf(GatewayEvent.WorktreeOutcome(ready = false, workspace = false))
            "workspace.ready" -> listOf(GatewayEvent.WorktreeOutcome(ready = true, workspace = true))
            "workspace.failed" -> listOf(GatewayEvent.WorktreeOutcome(ready = false, workspace = true))
            else -> emptyList()
        }
    }

    private fun toolPart(sessionId: String, messageId: String, state: ToolPart): GatewayEvent.PartUpserted {
        val name = state.name ?: toolCalls[callKey(messageId, state.callId)]?.name
        return GatewayEvent.PartUpserted(
            Part(sessionId, messageId, state.callId, PartKind.Tool, tool = state.copy(name = name?.ifEmpty { null })),
        )
    }

    /**
     * Calls of [sessionId] whose result never arrived, settled once the server has moved past them: a step ends only
     * after its tools settle, the next step starts only after the one before ended, and nothing runs after the
     * execution ends. Without this a lost `tool.success` left the row running for good. The outcome is not known
     * here, so the chat is told to reread ([GatewayEvent.TranscriptStale]).
     */
    private fun settleOpenCalls(
        sessionId: String,
        onlyMessageId: String? = null,
        exceptMessageId: String? = null,
        failure: SettleReason? = null,
        failureText: String? = null,
    ): List<GatewayEvent> {
        val out = mutableListOf<GatewayEvent>()
        for ((key, call) in toolCalls.entries.toList()) {
            if (call.sessionId != sessionId) continue
            val messageId = key.substringBefore('/')
            if (onlyMessageId != null && messageId != onlyMessageId) continue
            if (exceptMessageId != null && messageId == exceptMessageId) continue
            toolCalls.remove(key)
            val state =
                ToolPart(
                    callId = key.substringAfter('/'),
                    name = call.name,
                    status = if (failure != null) ToolStatus.Error else ToolStatus.Completed,
                    input = call.input ?: EmptyJson,
                    error = failureText?.takeIf { failure != null },
                    settledBy = failure ?: SettleReason.StepMovedOn,
                )
            out += toolPart(sessionId, messageId, state)
        }
        if (out.isNotEmpty()) out += GatewayEvent.TranscriptStale(sessionId)
        return out
    }

    private fun callKey(messageId: String, callId: String) = "$messageId/$callId"

    private fun <T> remember(store: LinkedHashMap<String, T>, key: String, value: T) {
        store.remove(key)
        store[key] = value
        while (store.size > MAX_TRACKED) store.remove(store.keys.first())
    }

    private fun Oc2Phase.revertKind(): RevertKind =
        when (this) {
            Oc2Phase.Cleared -> RevertKind.Cleared
            Oc2Phase.Committed -> RevertKind.Committed
            else -> RevertKind.Staged
        }

    private fun JsonObject.idOf(): String? = (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: infoIdOf()

    private fun JsonObject.infoIdOf(): String? =
        ((this["info"] as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content

    public companion object {
        public const val MAX_TRACKED: Int = 256
        private val EmptyJson = JsonObject(emptyMap())
    }
}
