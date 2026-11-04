package io.github.eslamasabry.oc.protocol.oc2.adapter

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.FormId
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.ProjectId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ids.WorkspaceId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.HintKind
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolResultItem
import io.github.eslamasabry.oc.core.domain.events.InboxChange
import io.github.eslamasabry.oc.core.domain.events.InstallationKind
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.domain.events.RevertKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.domain.events.TerminalChange
import io.github.eslamasabry.oc.core.model.FormField
import io.github.eslamasabry.oc.core.model.FormFieldType
import io.github.eslamasabry.oc.core.model.FormOption
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.transcript.EmptyJsonObject
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.ToolSegment
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolOutputFile
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormField
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelRef
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2PermissionRequest
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Tokens
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

/**
 * Projects the adapter's OC2-shaped output onto core's neutral [GatewayEvent] (plan 04 §11.2,
 * N4-D11): the only event type that leaves `:protocol:oc2`. Written by INTEGRATE-W1 so core stays
 * the single source of truth while the adapter's rules stay a line-for-line port of Dart
 * `Api2EventAdapter`.
 *
 * [redact] turns server prose into Details text (`Redactor::details`); nothing else here carries
 * server text.
 *
 * What core does not carry, and why that is safe (each is a refetch or a words-only detail):
 * - `RevertChanged.revert` / `toMessageId`: a revert resets history cursors and the chat refetches.
 * - `PermissionSettled.reply`, `InboxChanged.delivery`: the owners refetch the pending lists on
 *   settle.
 * - `WorktreeOutcome.workspace`: both kinds are the same freshness hint.
 * - `RunRetrying`'s server message goes to `details` only; a failed run's type becomes a [WireTag],
 *   its message `details`. `MessageErrorKind` stays null until S2-MODEL ports `fromName` (the words
 *   come from the tag).
 * - A tool the adapter settled itself (`settledBy`) becomes `Error` with no output: plan 09 words
 *   it as "failed".
 * - `SessionViewed` without an idle time reads as idle 0 ms (just viewed).
 */
internal class Oc2CoreProjection(private val redact: (String) -> Redacted) {
    fun toCore(event: Oc2Adapted): GatewayEvent =
        when (event) {
            Oc2Adapted.Connected -> GatewayEvent.Connected
            is Oc2Adapted.SessionUpserted ->
                GatewayEvent.SessionUpserted(
                    Session(
                        id = SessionId(event.sessionId),
                        title = event.title,
                        projectId = event.projectId?.let(::ProjectId),
                        workspaceId = event.location?.workspaceID?.let(::WorkspaceId),
                        parentId = event.parentId?.let(::SessionId),
                        directory = event.location?.directory,
                        path = event.subpath,
                        time = SessionTime(createdMs = event.createdMs),
                        agent = event.agent,
                        model = event.model?.let { "${it.providerID}/${it.id}" },
                    )
                )
            is Oc2Adapted.SessionChanged ->
                GatewayEvent.SessionChanged(
                    sessionId = SessionId(event.sessionId),
                    title = event.title,
                    directory = event.directory,
                    workspaceId = event.workspaceId,
                    replacesLocation = event.replacesLocation,
                    projectId = event.projectId?.let(::ProjectId),
                    subpath = event.subpath,
                    replacesSubpath = event.replacesSubpath,
                )
            is Oc2Adapted.SessionDeleted -> GatewayEvent.SessionDeleted(SessionId(event.sessionId))
            is Oc2Adapted.SessionSelection ->
                GatewayEvent.SessionSelection(
                    SessionId(event.sessionId),
                    event.model?.toCore(),
                    event.agent,
                    event.model?.variant?.takeUnless { it == "default" } ?: "",
                )
            is Oc2Adapted.SessionUsage ->
                GatewayEvent.SessionUsage(
                    SessionId(event.sessionId),
                    event.cost,
                    event.tokens.toCore(),
                )
            is Oc2Adapted.SessionViewed ->
                GatewayEvent.SessionViewed(SessionId(event.sessionId), event.idleMs ?: 0L)
            is Oc2Adapted.RunStarted -> GatewayEvent.RunStarted(SessionId(event.sessionId))
            is Oc2Adapted.RunIdle ->
                GatewayEvent.RunEnded(SessionId(event.sessionId), RunOutcome.Idle)
            is Oc2Adapted.RunRetrying ->
                GatewayEvent.RunRetrying(
                    sessionId = SessionId(event.sessionId),
                    attempt = event.attempt,
                    nextAtMs = event.nextAtMs,
                    tag = null,
                    details = event.message?.let(redact),
                )
            is Oc2Adapted.RunEnded ->
                GatewayEvent.RunEnded(SessionId(event.sessionId), event.outcome.toCore())
            is Oc2Adapted.MessageUpserted -> GatewayEvent.MessageUpserted(event.message.toCore())
            is Oc2Adapted.PartUpserted -> GatewayEvent.PartUpserted(event.part.toCore())
            is Oc2Adapted.PartDelta ->
                GatewayEvent.PartDelta(
                    sessionId = SessionId(event.sessionId),
                    messageId = MessageId(event.messageId),
                    partId = PartId(event.partId),
                    field =
                        when (event.field) {
                            Oc2DeltaField.Text -> DeltaField.Text
                            Oc2DeltaField.Reasoning -> DeltaField.Reasoning
                            Oc2DeltaField.ToolInputRaw -> DeltaField.ToolInputRaw
                        },
                    delta = event.delta,
                )
            is Oc2Adapted.TranscriptStale ->
                GatewayEvent.TranscriptStale(SessionId(event.sessionId))
            is Oc2Adapted.Notice ->
                GatewayEvent.Notice(
                    SessionId(event.sessionId),
                    event.messageId?.let(::MessageId),
                    event.kind.toCore(),
                )
            is Oc2Adapted.RevertChanged ->
                GatewayEvent.RevertChanged(
                    SessionId(event.sessionId),
                    when (event.kind) {
                        Oc2RevertKind.Staged -> RevertKind.Staged
                        Oc2RevertKind.Cleared -> RevertKind.Cleared
                        Oc2RevertKind.Committed -> RevertKind.Committed
                    },
                )
            is Oc2Adapted.SessionCompacted ->
                GatewayEvent.SessionCompacted(SessionId(event.sessionId))
            is Oc2Adapted.PermissionAsked -> GatewayEvent.PermissionAsked(event.request.toCore())
            is Oc2Adapted.PermissionSettled ->
                GatewayEvent.PermissionSettled(
                    SessionId(event.sessionId),
                    RequestId(event.requestId),
                )
            is Oc2Adapted.FormCreated -> GatewayEvent.FormCreated(event.form.toCore())
            is Oc2Adapted.FormSettled ->
                GatewayEvent.FormSettled(
                    SessionId(event.sessionId),
                    FormId(event.formId),
                    event.answered,
                )
            is Oc2Adapted.InboxChanged ->
                GatewayEvent.InboxChanged(
                    SessionId(event.sessionId),
                    InboxItemId(event.inboxId),
                    when (event.change) {
                        Oc2InboxChange.Enqueued -> InboxChange.Enqueued
                        Oc2InboxChange.Delivered -> InboxChange.Delivered
                        Oc2InboxChange.Cancelled -> InboxChange.Cancelled
                        Oc2InboxChange.DeliveryChanged -> InboxChange.DeliveryChanged
                    },
                )
            is Oc2Adapted.RefreshHint -> GatewayEvent.RefreshHint(HintKind(event.kind.wireFamily()))
            is Oc2Adapted.CredentialSwitched ->
                GatewayEvent.CredentialSwitched(event.integrationId, event.credentialId)
            is Oc2Adapted.Installation ->
                GatewayEvent.Installation(
                    when (event.kind) {
                        Oc2InstallationKind.Available -> InstallationKind.Available
                        Oc2InstallationKind.Installed -> InstallationKind.Installed
                    },
                    event.version,
                )
            is Oc2Adapted.TerminalChanged ->
                GatewayEvent.TerminalChanged(
                    event.ptyId,
                    when (event.kind) {
                        Oc2TerminalChange.Created -> TerminalChange.Created
                        Oc2TerminalChange.Updated -> TerminalChange.Updated
                        Oc2TerminalChange.Exited -> TerminalChange.Exited
                        Oc2TerminalChange.Deleted -> TerminalChange.Deleted
                    },
                )
            is Oc2Adapted.ShellChanged -> GatewayEvent.ShellChanged(event.shellId)
            is Oc2Adapted.WorktreeOutcome ->
                GatewayEvent.WorktreeOutcome(
                    ready = event.ready,
                    directory = event.directory,
                    projectId = event.projectId,
                    name = event.name,
                    branch = event.branch,
                    details = event.message?.let(redact),
                    workspace = event.workspace,
                )
        }

    private fun Oc2RunOutcome.toCore(): RunOutcome =
        when (this) {
            Oc2RunOutcome.Succeeded -> RunOutcome.Succeeded
            is Oc2RunOutcome.Interrupted ->
                RunOutcome.Interrupted(
                    when (reason) {
                        "user" -> InterruptReason.User
                        "inactivity" -> InterruptReason.Inactivity
                        "shutdown" -> InterruptReason.Shutdown
                        "superseded" -> InterruptReason.Superseded
                        else -> InterruptReason.Unknown
                    }
                )
            is Oc2RunOutcome.Failed ->
                RunOutcome.Failed(
                    kind = null,
                    tag = type?.let(::WireTag),
                    details = message?.let(redact),
                )
        }

    private fun Oc2AdaptedMessage.toCore(): MessageInfo =
        MessageInfo(
            id = id,
            sessionId = sessionId,
            role = if (role == Oc2MessageRole.User) Role.User else Role.Assistant,
            agent = agent,
            providerId = providerId,
            modelId = modelId,
            cost = cost ?: 0.0,
            tokens = tokens?.toCore() ?: Tokens(),
            time = MsgTime(createdMs, completedMs),
            // Server prose: redacted like the history path (Oc2History) before it can be shown.
            errorText = error?.message?.let { redact(it).text },
            finish = finish,
        )

    private fun Oc2AdaptedPart.toCore(): Part =
        Part(
            id = partId,
            messageId = messageId,
            sessionId = sessionId,
            type =
                when (kind) {
                    Oc2PartKind.Text -> PartType.Text
                    Oc2PartKind.Reasoning -> PartType.Reasoning
                    Oc2PartKind.Tool -> PartType.Tool
                    Oc2PartKind.Notice -> PartType.Notice
                },
            rawType =
                when (kind) {
                    Oc2PartKind.Text -> "text"
                    Oc2PartKind.Reasoning -> "reasoning"
                    Oc2PartKind.Tool -> "tool"
                    Oc2PartKind.Notice -> "v2:notice"
                },
            text = text ?: "",
            callId = tool?.callId,
            toolName = tool?.name,
            tool = tool?.toCore(),
            ended = endedAtMs != null,
            noticeMetadata =
                notice?.let { persistentMapOf("kind" to it.toCore().raw) } ?: persistentMapOf(),
        )

    private fun Oc2AdaptedTool.toCore(): ToolState =
        ToolState(
            status =
                when (status) {
                    Oc2ToolStatus.Pending -> ToolStatus.Pending
                    Oc2ToolStatus.Running -> ToolStatus.Running
                    Oc2ToolStatus.Completed -> ToolStatus.Completed
                    Oc2ToolStatus.Error -> ToolStatus.Error
                },
            input = input ?: EmptyJsonObject,
            inputRaw = raw,
            // Plan 09 §2.2: a failed call's server text is its output (ToolState.declined reads it
            // there).
            output =
                if (status != Oc2ToolStatus.Error) output
                // A failed step's server message settles open calls; redact it like errorText.
                else if (settledBy == Oc2SettleReason.StepFailed) error?.let { redact(it).text }
                else error,
            metadata = metadata,
            outputFiles =
                attachments
                    .map {
                        ToolOutputFile(
                            path = null,
                            filename = it.name,
                            mime = it.mime,
                            url = it.url,
                        )
                    }
                    .toImmutableList(),
            segments =
                content
                    .mapNotNull { item ->
                        when (item) {
                            is Oc2ToolResultItem.Text -> ToolSegment.Text(item.text)
                            is Oc2ToolResultItem.File ->
                                ToolSegment.File(
                                    ToolOutputFile(null, item.name, item.mime, item.uri)
                                )
                            is Oc2ToolResultItem.Unknown -> null
                        }
                    }
                    .toImmutableList(),
            executedFlag = executed ?: true,
            startedAtMs = startedAtMs,
            completedAtMs = completedAtMs,
        )

    private fun Oc2NoticeKind.toCore(): NoticeKind =
        when (this) {
            Oc2NoticeKind.InstructionsUpdated -> NoticeKind.InstructionsUpdated
            Oc2NoticeKind.SkillChanged -> NoticeKind.SkillChanged
            Oc2NoticeKind.ShellChanged -> NoticeKind.ShellChanged
        }

    /**
     * The wire family core's open [HintKind] keeps (N4-D22's six: agent, config, catalog,
     * integration, plugin, websearch).
     */
    private fun Oc2HintKind.wireFamily(): String =
        when (this) {
            Oc2HintKind.Agents -> "agent"
            Oc2HintKind.Config -> "config"
            Oc2HintKind.Catalog -> "catalog"
            Oc2HintKind.Integrations -> "integration"
            Oc2HintKind.Plugins -> "plugin"
            Oc2HintKind.WebSearch -> "websearch"
        }

    private fun Oc2ModelRef.toCore(): ModelRef = ModelRef(providerId = providerID, modelId = id)

    private fun Oc2Tokens.toCore(): Tokens = Tokens(input, output, reasoning, cacheRead, cacheWrite)

    /** Dart `mapApi2PermissionRequest`: `source.id` doubles as the call id. */
    private fun Oc2PermissionRequest.toCore(): PermissionRequest {
        val messageId = source?.messageID?.takeIf { it.isNotEmpty() }
        val callId = source?.id?.takeIf { it.isNotEmpty() }
        return PermissionRequest(
            id = RequestId(id),
            sessionId = SessionId(sessionID ?: ""),
            permission = action,
            patterns = resources.toImmutableList(),
            metadata = metadata ?: EmptyJsonObject,
            always = save.toImmutableList(),
            tool =
                if (messageId != null && callId != null)
                    PermissionTool(MessageId(messageId), callId)
                else null,
            canAlwaysAllow = true, // Dart PermissionRequest default; OC2 has no grantable flag
            message = message,
        )
    }

    private fun Oc2FormInfo.toCore(): FormSpec =
        FormSpec(
            id = FormId(id),
            sessionId = SessionId(sessionID),
            title = title,
            metadata = metadata,
            fields = fields.map { it.toCore() }.toImmutableList(),
        )

    private fun Oc2FormField.toCore(): FormField =
        FormField(
            key = key,
            type = FormFieldType(type.wire),
            title = title,
            description = description,
            required = required,
            options =
                options.map { FormOption(it.value, it.label, it.description) }.toImmutableList(),
            // The complete wire field (conditions, limits, pattern, default) for the forms engine.
            raw =
                Oc2Wire.json.encodeToJsonElement(Oc2FormField.serializer(), this) as? JsonObject
                    ?: EmptyJsonObject,
        )
}
