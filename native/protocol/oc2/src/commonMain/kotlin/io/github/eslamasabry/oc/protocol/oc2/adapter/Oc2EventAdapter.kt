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
// The adapter's own vocabulary (internal). These shapes carry exactly what the OC2 wire gives, so the rule port below
// stays reviewable against Dart `Api2EventAdapter` line by line. Nothing outside this module sees them: the gateway
// publishes core's `GatewayEvent` through `Oc2CoreProjection` (INTEGRATE-W1), which is the one place where OC2 detail
// that core does not carry is dropped or reshaped (each case is listed there).
// ================================================================================================================

internal enum class Oc2MessageRole { User, Assistant }

/** Error words for Details. [message] is null when the server sent none: the UI words it from [type] and [status]. */
internal data class Oc2StepError(public val message: String?, public val type: String?, public val status: Int?)

internal data class Oc2AdaptedMessage(
    public val id: String,
    public val sessionId: String,
    public val role: Oc2MessageRole,
    public val createdMs: Long,
    public val completedMs: Long? = null,
    public val agent: String? = null,
    public val providerId: String? = null,
    public val modelId: String? = null,
    public val cost: Double? = null,
    public val tokens: Oc2Tokens? = null,
    public val finish: String? = null,
    public val error: Oc2StepError? = null,
)

internal enum class Oc2ToolStatus { Pending, Running, Completed, Error }

internal data class Oc2ToolAttachment(public val url: String, public val mime: String?, public val name: String?)

internal data class Oc2AdaptedTool(
    public val callId: String,
    public val name: String?,
    public val status: Oc2ToolStatus,
    /** Raw JSON argument text while the call is still being written (pending). */
    public val raw: String? = null,
    public val input: JsonObject? = null,
    public val output: String? = null,
    /** Server error text; null with [Oc2ToolStatus.Error] means "failed" without words from the server. */
    public val error: String? = null,
    /** Set when the adapter settled the call itself because its result was lost: the UI words it. */
    public val settledBy: Oc2SettleReason? = null,
    public val attachments: List<Oc2ToolAttachment> = emptyList(),
    public val metadata: JsonObject? = null,
    public val executed: Boolean? = null,
)

internal enum class Oc2SettleReason { StepMovedOn, StepFailed, RunFailed, RunInterrupted }

internal enum class Oc2PartKind { Text, Reasoning, Tool, Notice }

internal enum class Oc2NoticeKind { InstructionsUpdated, SkillChanged, ShellChanged }

/** Oc2AdaptedPart ids match the refetch mapper: `text-<n>`, `reasoning-<n>`, the call id for tools, `v2-0` for notices. */
internal data class Oc2AdaptedPart(
    public val sessionId: String,
    public val messageId: String,
    public val partId: String,
    public val kind: Oc2PartKind,
    public val text: String? = null,
    public val endedAtMs: Long? = null,
    public val tool: Oc2AdaptedTool? = null,
    public val notice: Oc2NoticeKind? = null,
)

internal enum class Oc2DeltaField { Text, Reasoning, ToolInputRaw }

internal sealed interface Oc2RunOutcome {
    public data object Succeeded : Oc2RunOutcome

    /** [reason] is the server's (`user|inactivity|shutdown|superseded`), or `unknown`. */
    public data class Interrupted(public val reason: String) : Oc2RunOutcome

    /** [message] is null when blank: the UI words it from [type]. */
    public data class Failed(public val type: String?, public val message: String?) : Oc2RunOutcome
}

internal enum class Oc2RevertKind { Staged, Cleared, Committed }

internal enum class Oc2InboxChange { Enqueued, Delivered, Cancelled, DeliveryChanged }

/** Refresh bursts share one reload downstream. */
internal enum class Oc2HintKind {
    Agents, Commands, Config, Skills, Catalog, Integrations, Plugins, References, WebSearch, Projects, Worktrees, Lsp,
    Mcp, Vcs, Files,
}

internal enum class Oc2InstallationKind { Available, Installed }

internal enum class Oc2TerminalChange { Created, Updated, Exited, Deleted }

internal sealed interface Oc2Adapted {
    public data object Connected : Oc2Adapted

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
    ) : Oc2Adapted

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
    ) : Oc2Adapted

    public data class SessionDeleted(public val sessionId: String) : Oc2Adapted

    public data class SessionSelection(public val sessionId: String, public val model: Oc2ModelRef?, public val agent: String?) :
        Oc2Adapted

    /** Merged into the stored session in place, never a full replace. */
    public data class SessionUsage(public val sessionId: String, public val cost: Double, public val tokens: Oc2Tokens) :
        Oc2Adapted

    public data class SessionViewed(public val sessionId: String, public val idleMs: Long?) : Oc2Adapted

    public data class RunStarted(public val sessionId: String) : Oc2Adapted

    /** `session.idle` / status idle; stable servers send it after the run end (plan 04 §7.6: ignored downstream). */
    public data class RunIdle(public val sessionId: String) : Oc2Adapted

    public data class RunRetrying(
        public val sessionId: String,
        public val attempt: Int,
        public val nextAtMs: Long?,
        /** Shown only under Details. */
        public val message: String?,
    ) : Oc2Adapted

    public data class RunEnded(public val sessionId: String, public val outcome: Oc2RunOutcome) : Oc2Adapted

    public data class MessageUpserted(public val message: Oc2AdaptedMessage) : Oc2Adapted

    public data class PartUpserted(public val part: Oc2AdaptedPart) : Oc2Adapted

    public data class PartDelta(
        public val sessionId: String,
        public val messageId: String,
        public val partId: String,
        public val field: Oc2DeltaField,
        public val delta: String,
    ) : Oc2Adapted

    /** "Read the transcript again for the real result." */
    public data class TranscriptStale(public val sessionId: String) : Oc2Adapted

    public data class Notice(public val sessionId: String, public val kind: Oc2NoticeKind, public val messageId: String?) :
        Oc2Adapted

    public data class RevertChanged(
        public val sessionId: String,
        public val kind: Oc2RevertKind,
        public val revert: JsonObject?,
        public val toMessageId: String?,
    ) : Oc2Adapted

    public data class SessionCompacted(public val sessionId: String) : Oc2Adapted

    /** [callId] is the request's `source.id`. Asks are never replayed: refetch on reconnect. */
    public data class PermissionAsked(public val request: Oc2PermissionRequest, public val callId: String?) : Oc2Adapted

    public data class PermissionSettled(public val sessionId: String, public val requestId: String, public val reply: String?) :
        Oc2Adapted

    public data class FormCreated(public val form: Oc2FormInfo) : Oc2Adapted

    public data class FormSettled(public val sessionId: String, public val formId: String, public val answered: Boolean) :
        Oc2Adapted

    public data class InboxChanged(
        public val sessionId: String,
        public val inboxId: String,
        public val change: Oc2InboxChange,
        public val delivery: Oc2Delivery?,
    ) : Oc2Adapted

    public data class RefreshHint(public val kind: Oc2HintKind) : Oc2Adapted

    public data class CredentialSwitched(public val integrationId: String, public val credentialId: String?) : Oc2Adapted

    public data class Installation(public val kind: Oc2InstallationKind, public val version: String) : Oc2Adapted

    public data class TerminalChanged(public val ptyId: String?, public val kind: Oc2TerminalChange) : Oc2Adapted

    public data class ShellChanged(public val shellId: String?) : Oc2Adapted

    /** `worktree.ready|failed` and `workspace.ready|failed`: freshness hints only. */
    public data class WorktreeOutcome(public val ready: Boolean, public val workspace: Boolean) : Oc2Adapted
}

// ================================================================================================================
// The adapter: lib/api2/gateway_events.dart Api2EventAdapter, ported rule for rule onto Oc2Adapted.
// ================================================================================================================

/**
 * Stateful per connection graph (one instance per event stream, plan 04 §6.5). Remembers, for at most
 * [MAX_TRACKED] entries each (oldest evicted, B-CHAT-005): open tool calls, step start times and the agent and model
 * a step started with (`step.ended|failed` do not repeat them).
 *
 * [nowMs] stands in for an envelope without `created` and stamps ended parts (inject the app Clock).
 */
internal class Oc2EventAdapter(private val nowMs: () -> Long) {
    private class ToolCall(val sessionId: String, val name: String, val input: JsonObject?)

    private class StepModel(val agent: String?, val model: Oc2ModelRef?)

    private val toolCalls = LinkedHashMap<String, ToolCall>()
    private val stepCreated = LinkedHashMap<String, Long>()
    private val stepModels = LinkedHashMap<String, StepModel>()

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    public fun adapt(envelope: Oc2Envelope): List<Oc2Adapted> {
        val created = envelope.created ?: nowMs()
        return when (val e = envelope.event) {
            is Oc2Event.ServerConnected -> listOf(Oc2Adapted.Connected)
            // Running-work surfaces reconcile from the scoped shell API; never synthetic messages or retained jobs.
            is Oc2Event.ManagedShell -> listOf(Oc2Adapted.ShellChanged(e.data.idOf()))
            is Oc2Event.SessionShell -> listOf(Oc2Adapted.Notice(e.sessionID, Oc2NoticeKind.ShellChanged, null))
            // Rehydrate the canonical, redacted skill row; never expose event text.
            is Oc2Event.SessionSkillActivated -> listOf(Oc2Adapted.Notice(e.sessionID, Oc2NoticeKind.SkillChanged, null))
            is Oc2Event.SessionCreated ->
                listOf(
                    Oc2Adapted.SessionUpserted(
                        e.sessionID, e.title, e.projectID, e.parentID, e.location, e.subpath, e.model, e.agent, created,
                    ),
                )
            is Oc2Event.SessionRenamed -> listOf(Oc2Adapted.SessionChanged(e.sessionID, title = e.title))
            is Oc2Event.SessionDeleted -> listOf(Oc2Adapted.SessionDeleted(e.sessionID))
            is Oc2Event.SessionMoved ->
                listOf(
                    Oc2Adapted.SessionChanged(
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
                e.model?.let { listOf(Oc2Adapted.SessionSelection(e.sessionID, it, null)) } ?: emptyList()
            is Oc2Event.SessionAgentSelected ->
                if (e.agent.isEmpty()) emptyList() else listOf(Oc2Adapted.SessionSelection(e.sessionID, null, e.agent))
            is Oc2Event.SessionRevert ->
                listOf(Oc2Adapted.RevertChanged(e.sessionID, e.phase.revertKind(), e.revert, e.toMessageID))
            is Oc2Event.SessionInstructionsUpdated -> instructions(envelope, e, created)
            is Oc2Event.SessionViewed -> listOf(Oc2Adapted.SessionViewed(e.sessionID, e.idle))
            is Oc2Event.SessionStatus ->
                when (e.status) {
                    Oc2SessionRunStatus.Idle -> listOf(Oc2Adapted.RunIdle(e.sessionID))
                    Oc2SessionRunStatus.Busy -> listOf(Oc2Adapted.RunStarted(e.sessionID))
                    Oc2SessionRunStatus.Retry ->
                        listOf(Oc2Adapted.RunRetrying(e.sessionID, e.attempt ?: 0, e.next, e.message))
                    Oc2SessionRunStatus.Unknown -> emptyList()
                }
            // No full-session replace: merged into the stored session in place.
            is Oc2Event.SessionUsageUpdated -> listOf(Oc2Adapted.SessionUsage(e.sessionID, e.cost, e.tokens))
            is Oc2Event.SessionRetryScheduled ->
                listOf(Oc2Adapted.RunRetrying(e.sessionID, e.attempt ?: 0, e.at, e.error?.message ?: e.error?.type))
            is Oc2Event.SessionExecution -> execution(e)
            is Oc2Event.SessionCompacted -> listOf(Oc2Adapted.SessionCompacted(e.sessionID))
            is Oc2Event.SessionInbox -> inbox(e, created)
            is Oc2Event.SessionStep -> step(e, created)
            is Oc2Event.SessionText ->
                ordinalPart(e.phase, e.sessionID, e.assistantMessageID, "text-${e.ordinal}", Oc2PartKind.Text, e.delta, e.text)
            is Oc2Event.SessionReasoning ->
                ordinalPart(
                    e.phase, e.sessionID, e.assistantMessageID, "reasoning-${e.ordinal}", Oc2PartKind.Reasoning, e.delta, e.text,
                )
            is Oc2Event.SessionToolInput -> toolInput(e)
            is Oc2Event.SessionToolCalled -> {
                val key = callKey(e.assistantMessageID, e.callID)
                remember(toolCalls, key, ToolCall(e.sessionID, toolCalls[key]?.name ?: "", e.input))
                listOf(toolPart(e.sessionID, e.assistantMessageID, Oc2AdaptedTool(e.callID, null, Oc2ToolStatus.Running, input = e.input)))
            }
            is Oc2Event.SessionToolProgress -> {
                val call = toolCalls[callKey(e.assistantMessageID, e.callID)]
                val state = Oc2AdaptedTool(e.callID, null, Oc2ToolStatus.Running, input = call?.input ?: EmptyJson, metadata = e.metadata)
                listOf(toolPart(e.sessionID, e.assistantMessageID, state))
            }
            is Oc2Event.SessionToolResult -> toolResult(e)
            is Oc2Event.SessionMessageContentUpdated -> contentUpdated(e)
            is Oc2Event.PermissionAsked -> listOf(Oc2Adapted.PermissionAsked(e.request, e.request.source?.id))
            is Oc2Event.PermissionReplied -> listOf(Oc2Adapted.PermissionSettled(e.sessionID, e.requestID, e.reply))
            is Oc2Event.FormCreated -> listOf(Oc2Adapted.FormCreated(e.form))
            is Oc2Event.FormReplied -> listOf(Oc2Adapted.FormSettled(e.sessionID, e.id, answered = true))
            is Oc2Event.FormCancelled -> listOf(Oc2Adapted.FormSettled(e.sessionID, e.id, answered = false))
            is Oc2Event.RefreshHint -> refresh(e.topic, envelope.data)
            // Plan 04 §7.6 widens Flutter here: these pings refresh their lists too (Flutter dropped them).
            is Oc2Event.McpChanged -> listOf(Oc2Adapted.RefreshHint(Oc2HintKind.Mcp))
            is Oc2Event.VcsBranchUpdated -> listOf(Oc2Adapted.RefreshHint(Oc2HintKind.Vcs))
            is Oc2Event.FilesystemChanged -> listOf(Oc2Adapted.RefreshHint(Oc2HintKind.Files))
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

    private fun instructions(envelope: Oc2Envelope, e: Oc2Event.SessionInstructionsUpdated, created: Long): List<Oc2Adapted> {
        // The server's text/delta may hold server-owned entries: only a neutral notice, whose durable id matches the
        // later REST transcript (`evt_X` → `msg_X`). The instruction text is never shown.
        val eventId = envelope.id
        val messageId = if (eventId != null && eventId.startsWith("evt_") && eventId.length > 4) "msg_${eventId.substring(4)}" else null
        val out = mutableListOf<Oc2Adapted>(Oc2Adapted.Notice(e.sessionID, Oc2NoticeKind.InstructionsUpdated, messageId))
        if (e.text != null && messageId != null) {
            out += Oc2Adapted.MessageUpserted(Oc2AdaptedMessage(messageId, e.sessionID, Oc2MessageRole.User, created))
            out += Oc2Adapted.PartUpserted(
                Oc2AdaptedPart(e.sessionID, messageId, "v2-0", Oc2PartKind.Notice, notice = Oc2NoticeKind.InstructionsUpdated),
            )
        }
        return out
    }

    private fun execution(e: Oc2Event.SessionExecution): List<Oc2Adapted> =
        when (e.phase) {
            Oc2Phase.Started -> listOf(Oc2Adapted.RunStarted(e.sessionID))
            // RunEnded(Failed) alone: an idle after it would overwrite the error settle.
            Oc2Phase.Failed ->
                settleOpenCalls(e.sessionID, failure = Oc2SettleReason.RunFailed) +
                    Oc2Adapted.RunEnded(e.sessionID, Oc2RunOutcome.Failed(e.error?.type, e.error?.message?.trim()?.ifEmpty { null }))
            else -> {
                val interrupted = e.phase == Oc2Phase.Interrupted
                val outcome =
                    if (interrupted) Oc2RunOutcome.Interrupted(e.reason?.trim()?.ifEmpty { null } ?: "unknown") else Oc2RunOutcome.Succeeded
                settleOpenCalls(e.sessionID, failure = if (interrupted) Oc2SettleReason.RunInterrupted else null) +
                    Oc2Adapted.RunEnded(e.sessionID, outcome)
            }
        }

    private fun inbox(e: Oc2Event.SessionInbox, created: Long): List<Oc2Adapted> {
        val change =
            when (e.phase) {
                Oc2Phase.Delivered -> Oc2InboxChange.Delivered
                Oc2Phase.Cancelled -> Oc2InboxChange.Cancelled
                Oc2Phase.DeliveryChanged -> Oc2InboxChange.DeliveryChanged
                else -> Oc2InboxChange.Enqueued
            }
        val changed = Oc2Adapted.InboxChanged(e.sessionID, e.inboxID, change, e.delivery)
        val item = e.item
        if (e.phase != Oc2Phase.Enqueued || item == null || item.type != "user") return listOf(changed)
        // A waiting prompt joins the transcript under its inbox id (the server echoes it later with that id).
        val text = item.promptText ?: ""
        return buildList {
            add(changed)
            add(Oc2Adapted.MessageUpserted(Oc2AdaptedMessage(e.inboxID, e.sessionID, Oc2MessageRole.User, item.timeCreated ?: created)))
            if (text.isNotEmpty()) add(Oc2Adapted.PartUpserted(Oc2AdaptedPart(e.sessionID, e.inboxID, "text-0", Oc2PartKind.Text, text = text)))
        }
    }

    private fun step(e: Oc2Event.SessionStep, created: Long): List<Oc2Adapted> =
        when (e.phase) {
            Oc2Phase.Started -> {
                remember(stepCreated, e.assistantMessageID, created)
                remember(stepModels, e.assistantMessageID, StepModel(e.agent, e.model))
                // A step starts only inside a running execution: this restores a busy state the app lost. Steps run
                // one after another, so calls of earlier steps have ended.
                listOf(Oc2Adapted.RunStarted(e.sessionID)) +
                    settleOpenCalls(e.sessionID, exceptMessageId = e.assistantMessageID) +
                    Oc2Adapted.MessageUpserted(assistantInfo(e, created))
            }
            // A step end never ends the run (B-PROTO-040).
            Oc2Phase.Ended ->
                settleOpenCalls(e.sessionID, onlyMessageId = e.assistantMessageID) +
                    Oc2Adapted.MessageUpserted(assistantInfo(e, created, completed = created))
            Oc2Phase.Failed ->
                settleOpenCalls(e.sessionID, onlyMessageId = e.assistantMessageID, failure = Oc2SettleReason.StepFailed,
                    failureText = e.error?.message) +
                    Oc2Adapted.MessageUpserted(
                        assistantInfo(e, created, completed = created, error = Oc2StepError(e.error?.message, e.error?.type, e.error?.status)),
                    )
            else -> emptyList() // `streamed` (provider body finished) changes nothing visible.
        }

    private fun assistantInfo(e: Oc2Event.SessionStep, created: Long, completed: Long? = null, error: Oc2StepError? = null): Oc2AdaptedMessage {
        val known = stepModels[e.assistantMessageID]
        val model = e.model ?: known?.model
        return Oc2AdaptedMessage(
            id = e.assistantMessageID,
            sessionId = e.sessionID,
            role = Oc2MessageRole.Assistant,
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
        kind: Oc2PartKind,
        delta: String?,
        text: String?,
    ): List<Oc2Adapted> =
        when (phase) {
            Oc2Phase.Started -> listOf(Oc2Adapted.PartUpserted(Oc2AdaptedPart(sessionId, messageId, partId, kind, text = "")))
            Oc2Phase.Delta -> {
                val field = if (kind == Oc2PartKind.Reasoning) Oc2DeltaField.Reasoning else Oc2DeltaField.Text
                listOf(Oc2Adapted.PartDelta(sessionId, messageId, partId, field, delta ?: ""))
            }
            // A finished part (the reply's caret goes).
            Oc2Phase.Ended ->
                listOf(Oc2Adapted.PartUpserted(Oc2AdaptedPart(sessionId, messageId, partId, kind, text = text ?: "", endedAtMs = nowMs())))
            else -> emptyList()
        }

    private fun toolInput(e: Oc2Event.SessionToolInput): List<Oc2Adapted> =
        when (e.phase) {
            Oc2Phase.Started -> {
                remember(toolCalls, callKey(e.assistantMessageID, e.callID), ToolCall(e.sessionID, e.name ?: "", null))
                listOf(toolPart(e.sessionID, e.assistantMessageID, Oc2AdaptedTool(e.callID, null, Oc2ToolStatus.Pending, raw = "")))
            }
            Oc2Phase.Delta ->
                listOf(Oc2Adapted.PartDelta(e.sessionID, e.assistantMessageID, e.callID, Oc2DeltaField.ToolInputRaw, e.delta ?: ""))
            Oc2Phase.Ended ->
                listOf(toolPart(e.sessionID, e.assistantMessageID, Oc2AdaptedTool(e.callID, null, Oc2ToolStatus.Pending, raw = e.text ?: "")))
            else -> emptyList()
        }

    private fun toolResult(e: Oc2Event.SessionToolResult): List<Oc2Adapted> {
        val call = toolCalls.remove(callKey(e.assistantMessageID, e.callID))
        val text = e.content.filterIsInstance<Oc2ToolResultItem.Text>().map { it.text }.filter { it.isNotEmpty() }.joinToString("\n")
        val attachments = e.content.filterIsInstance<Oc2ToolResultItem.File>().map { Oc2ToolAttachment(it.uri, it.mime, it.name) }
        val state =
            Oc2AdaptedTool(
                callId = e.callID,
                name = call?.name,
                status = if (e.succeeded) Oc2ToolStatus.Completed else Oc2ToolStatus.Error,
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
            if (call == null) Oc2Adapted.TranscriptStale(e.sessionID) else null,
        )
    }

    /** A server-side message edit: every content item again, with the ids the message mapper assigns. */
    private fun contentUpdated(e: Oc2Event.SessionMessageContentUpdated): List<Oc2Adapted> {
        var texts = 0
        var reasonings = 0
        return e.content.mapNotNull { item ->
            when (item) {
                is Oc2AssistantContent.Text ->
                    Oc2Adapted.PartUpserted(Oc2AdaptedPart(e.sessionID, e.messageID, "text-${texts++}", Oc2PartKind.Text, text = item.text))
                is Oc2AssistantContent.Reasoning ->
                    Oc2Adapted.PartUpserted(
                        Oc2AdaptedPart(e.sessionID, e.messageID, "reasoning-${reasonings++}", Oc2PartKind.Reasoning, text = item.text),
                    )
                is Oc2AssistantContent.Tool -> toolPart(e.sessionID, e.messageID, rawToolState(item))
                is Oc2AssistantContent.Unknown -> null
            }
        }
    }

    private fun rawToolState(item: Oc2AssistantContent.Tool): Oc2AdaptedTool =
        when (val s = item.state) {
            is Oc2ToolState.Streaming -> Oc2AdaptedTool(item.id, item.name, Oc2ToolStatus.Pending, raw = s.rawInput)
            is Oc2ToolState.Running -> Oc2AdaptedTool(item.id, item.name, Oc2ToolStatus.Running, input = s.input, metadata = s.metadata)
            is Oc2ToolState.Completed ->
                Oc2AdaptedTool(item.id, item.name, Oc2ToolStatus.Completed, input = s.input, output = s.textOutput, metadata = s.metadata)
            is Oc2ToolState.Error ->
                Oc2AdaptedTool(item.id, item.name, Oc2ToolStatus.Error, input = s.input, error = s.error?.message, metadata = s.metadata)
            is Oc2ToolState.Unknown -> Oc2AdaptedTool(item.id, item.name, Oc2ToolStatus.Pending)
        }

    private fun refresh(topic: String, data: JsonObject): List<Oc2Adapted> {
        val kind =
            when (topic) {
                "agent.updated" -> Oc2HintKind.Agents
                "command.updated" -> Oc2HintKind.Commands
                "config.updated" -> Oc2HintKind.Config
                "skill.updated" -> Oc2HintKind.Skills
                "catalog.updated", "models-dev.refreshed" -> Oc2HintKind.Catalog
                "integration.updated", "credential.updated", "credential.switched" -> Oc2HintKind.Integrations
                "plugin.added", "plugin.updated" -> Oc2HintKind.Plugins
                "reference.updated" -> Oc2HintKind.References
                "websearch.updated" -> Oc2HintKind.WebSearch
                "project.updated" -> Oc2HintKind.Projects
                "worktree.updated" -> Oc2HintKind.Worktrees
                "lsp.updated" -> Oc2HintKind.Lsp
                else -> return emptyList()
            }
        if (topic != "credential.switched") return listOf(Oc2Adapted.RefreshHint(kind))
        // Only an explicitly present null means "no active credential". Never forward credential payloads, and never
        // infer state from malformed data.
        val integration = (data["integrationID"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val credential = data["credentialID"]
        val credentialId = (credential as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val valid = integration != null && "credentialID" in data && (credential == JsonNull || credentialId != null)
        return listOfNotNull(
            if (valid) Oc2Adapted.CredentialSwitched(integration, credentialId) else null,
            Oc2Adapted.RefreshHint(Oc2HintKind.Integrations),
        )
    }

    private fun passThrough(e: Oc2Event.PassThrough): List<Oc2Adapted> {
        val raw = e.raw
        return when (e.type) {
            "installation.updated", "installation.update-available" -> {
                // Exact versions kept (B-PROTO-047): no trimming, no parsing.
                val version = (raw["version"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return emptyList()
                val kind = if (e.type == "installation.updated") Oc2InstallationKind.Installed else Oc2InstallationKind.Available
                listOf(Oc2Adapted.Installation(kind, version))
            }
            "pty.created" -> listOf(Oc2Adapted.TerminalChanged(raw.infoIdOf(), Oc2TerminalChange.Created))
            "pty.updated" -> listOf(Oc2Adapted.TerminalChanged(raw.infoIdOf(), Oc2TerminalChange.Updated))
            "pty.exited" -> listOf(Oc2Adapted.TerminalChanged(raw.idOf(), Oc2TerminalChange.Exited))
            "pty.deleted" -> listOf(Oc2Adapted.TerminalChanged(raw.idOf(), Oc2TerminalChange.Deleted))
            "worktree.ready" -> listOf(Oc2Adapted.WorktreeOutcome(ready = true, workspace = false))
            "worktree.failed" -> listOf(Oc2Adapted.WorktreeOutcome(ready = false, workspace = false))
            "workspace.ready" -> listOf(Oc2Adapted.WorktreeOutcome(ready = true, workspace = true))
            "workspace.failed" -> listOf(Oc2Adapted.WorktreeOutcome(ready = false, workspace = true))
            else -> emptyList()
        }
    }

    private fun toolPart(sessionId: String, messageId: String, state: Oc2AdaptedTool): Oc2Adapted.PartUpserted {
        val name = state.name ?: toolCalls[callKey(messageId, state.callId)]?.name
        return Oc2Adapted.PartUpserted(
            Oc2AdaptedPart(sessionId, messageId, state.callId, Oc2PartKind.Tool, tool = state.copy(name = name?.ifEmpty { null })),
        )
    }

    /**
     * Calls of [sessionId] whose result never arrived, settled once the server has moved past them: a step ends only
     * after its tools settle, the next step starts only after the one before ended, and nothing runs after the
     * execution ends. Without this a lost `tool.success` left the row running for good. The outcome is not known
     * here, so the chat is told to reread ([Oc2Adapted.TranscriptStale]).
     */
    private fun settleOpenCalls(
        sessionId: String,
        onlyMessageId: String? = null,
        exceptMessageId: String? = null,
        failure: Oc2SettleReason? = null,
        failureText: String? = null,
    ): List<Oc2Adapted> {
        val out = mutableListOf<Oc2Adapted>()
        for ((key, call) in toolCalls.entries.toList()) {
            if (call.sessionId != sessionId) continue
            val messageId = key.substringBefore('/')
            if (onlyMessageId != null && messageId != onlyMessageId) continue
            if (exceptMessageId != null && messageId == exceptMessageId) continue
            toolCalls.remove(key)
            val state =
                Oc2AdaptedTool(
                    callId = key.substringAfter('/'),
                    name = call.name,
                    status = if (failure != null) Oc2ToolStatus.Error else Oc2ToolStatus.Completed,
                    input = call.input ?: EmptyJson,
                    error = failureText?.takeIf { failure != null },
                    settledBy = failure ?: Oc2SettleReason.StepMovedOn,
                )
            out += toolPart(sessionId, messageId, state)
        }
        if (out.isNotEmpty()) out += Oc2Adapted.TranscriptStale(sessionId)
        return out
    }

    private fun callKey(messageId: String, callId: String) = "$messageId/$callId"

    private fun <T> remember(store: LinkedHashMap<String, T>, key: String, value: T) {
        store.remove(key)
        store[key] = value
        while (store.size > MAX_TRACKED) store.remove(store.keys.first())
    }

    private fun Oc2Phase.revertKind(): Oc2RevertKind =
        when (this) {
            Oc2Phase.Cleared -> Oc2RevertKind.Cleared
            Oc2Phase.Committed -> Oc2RevertKind.Committed
            else -> Oc2RevertKind.Staged
        }

    private fun JsonObject.idOf(): String? = (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: infoIdOf()

    private fun JsonObject.infoIdOf(): String? =
        ((this["info"] as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content

    public companion object {
        public const val MAX_TRACKED: Int = 256
        private val EmptyJson = JsonObject(emptyMap())
    }
}
