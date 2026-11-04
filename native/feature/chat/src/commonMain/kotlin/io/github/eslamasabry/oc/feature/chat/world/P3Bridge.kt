package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.domain.events.DeltaField as CoreDeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent as CoreEvent
import io.github.eslamasabry.oc.core.domain.events.NoticeKind as CoreNoticeKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome as CoreOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo as CoreMessage
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts as CoreMessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part as CorePart
import io.github.eslamasabry.oc.core.model.transcript.PartType as CorePartType
import io.github.eslamasabry.oc.core.model.transcript.Role as CoreRole
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus as CoreToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus

/**
 * TEMPORARY (delete with S2-TX): `:engine:transcript` still reads its P3 copies of the event and
 * message types (`state/Wire.kt`, INTEGRATE-W1 §5). Until S2-TX swaps the reducer onto core's
 * types, this maps core's `GatewayEvent` and history pages onto them, field for field, so the chat
 * shows a real conversation. It holds no rule beyond the two the P3 copies cannot express (Idle
 * never replaces a failure; a notice needs a message), each noted. One instance per open transcript
 * (it remembers whether the current run already ended badly).
 */
internal class P3Bridge(private val sessionId: String) {
    private var endedBadly = false

    fun message(m: CoreMessageWithParts): MessageWithParts =
        MessageWithParts(info(m.info), m.parts.map(::part))

    /** Null = nothing the P3 reducer reads (or another session's event). */
    fun event(e: CoreEvent): GatewayEvent? =
        when (e) {
            is CoreEvent.RunStarted ->
                mine(e.sessionId.raw) {
                    endedBadly = false
                    GatewayEvent.RunStarted(it)
                }
            is CoreEvent.RunEnded ->
                mine(e.sessionId.raw) { sid ->
                    outcome(e.outcome)?.let { GatewayEvent.RunEnded(sid, it) }
                }
            is CoreEvent.MessageUpserted ->
                mine(e.message.sessionId) { GatewayEvent.MessageUpserted(info(e.message)) }
            is CoreEvent.PartUpserted ->
                mine(e.part.sessionId) { GatewayEvent.PartUpserted(part(e.part)) }
            is CoreEvent.PartDelta ->
                mine(e.sessionId.raw) { sid ->
                    GatewayEvent.PartDelta(
                        sid,
                        e.messageId.raw,
                        e.partId.raw,
                        field(e.field),
                        e.delta,
                    )
                }
            is CoreEvent.TranscriptStale ->
                mine(e.sessionId.raw) { GatewayEvent.TranscriptStale(it) }
            // A session-level notice (no message) means "reconcile the list" (INTEGRATE-W1 D12):
            // the reducer cannot
            // hold it, and the transcript re-reads on the next stale or run end anyway.
            is CoreEvent.Notice -> {
                val mid = e.messageId?.raw
                if (mid == null) null
                else
                    mine(e.sessionId.raw) {
                        GatewayEvent.Notice(it, mid, "$mid:${e.kind.raw}", notice(e.kind))
                    }
            }
            is CoreEvent.SessionCompacted ->
                mine(e.sessionId.raw) { GatewayEvent.TranscriptStale(it) }
            else -> null
        }

    private inline fun mine(sid: String, make: (String) -> GatewayEvent?): GatewayEvent? =
        if (sid == sessionId) make(sid) else null

    /**
     * INTEGRATE-W1 §7: `Idle` ends the run but never replaces an earlier Failed or Interrupted of
     * the same run.
     */
    private fun outcome(o: CoreOutcome): RunOutcome? =
        when (o) {
            CoreOutcome.Succeeded -> RunOutcome.Succeeded.also { endedBadly = false }
            CoreOutcome.Idle -> if (endedBadly) null else RunOutcome.Succeeded
            is CoreOutcome.Interrupted ->
                RunOutcome.Interrupted(o.reason.name).also { endedBadly = true }
            is CoreOutcome.Failed ->
                RunOutcome.Failed(o.kind?.name ?: o.tag?.raw).also { endedBadly = true }
        }

    private fun info(m: CoreMessage): MessageInfo =
        MessageInfo(
            id = m.id,
            sessionId = m.sessionId,
            role = if (m.role == CoreRole.User) Role.User else Role.Assistant,
            createdMs = m.time?.createdMs,
            completedMs = m.time?.completedMs,
            finish = m.finish,
            errorText = m.errorText,
            errorKind = m.errorKind,
        )

    private fun part(raw: CorePart): Part {
        val p =
            io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundShellToolPart(raw) ?: raw
        return Part(
            id = p.id,
            messageId = p.messageId,
            sessionId = p.sessionId,
            type = type(p.type),
            // A compaction part carries no words; its kind name lets the reducer draw the
            // compaction notice
            // (B-CHAT-069: a compaction is a notice in the transcript, never a reply).
            text = if (p.type == CorePartType.Compaction) NoticeKind.Compaction.name else p.text,
            toolName = p.toolName,
            toolStatus = p.tool?.status?.let(::tool),
            synthetic = p.synthetic,
            ended = p.ended,
            searchText =
                listOfNotNull(
                        p.toolName,
                        p.tool?.title,
                        p.tool?.input?.toString(),
                        p.tool?.inputRaw,
                        p.tool?.output,
                        p.tool?.outputValue?.toString(),
                        p.tool?.outputFiles?.joinToString {
                            listOfNotNull(it.path, it.filename).joinToString(" ")
                        },
                        p.filename,
                        p.mime,
                        p.text.takeIf {
                            p.type in
                                setOf(
                                    CorePartType.File,
                                    CorePartType.Notice,
                                    CorePartType.Switch,
                                    CorePartType.Compaction,
                                ) &&
                                (p.noticeMetadata["kind"] ?: p.toolName) !in
                                    setOf("instructions", "skill")
                        },
                    )
                    .joinToString("\n"),
        )
    }

    private fun type(t: CorePartType): PartType =
        when (t) {
            CorePartType.Text -> PartType.Text
            CorePartType.Reasoning -> PartType.Reasoning
            CorePartType.Tool -> PartType.Tool
            CorePartType.File -> PartType.File
            CorePartType.StepStart -> PartType.StepStart
            CorePartType.StepFinish -> PartType.StepFinish
            CorePartType.Switch -> PartType.Switch
            CorePartType.Notice -> PartType.Notice
            CorePartType.Compaction -> PartType.Compaction
            CorePartType.Snapshot,
            CorePartType.Patch,
            CorePartType.Unknown -> PartType.Unknown
        }

    private fun tool(s: CoreToolStatus): ToolStatus =
        when (s) {
            CoreToolStatus.Pending -> ToolStatus.Pending
            CoreToolStatus.Running -> ToolStatus.Running
            CoreToolStatus.Completed -> ToolStatus.Completed
            CoreToolStatus.Error -> ToolStatus.Error
            CoreToolStatus.Cancelled -> ToolStatus.Cancelled
        }

    private fun field(f: CoreDeltaField): DeltaField =
        when (f) {
            CoreDeltaField.Text -> DeltaField.Text
            CoreDeltaField.Reasoning -> DeltaField.Reasoning
            CoreDeltaField.ToolInputRaw -> DeltaField.ToolInputRaw
        }

    /** Server notice text is never shown (trap T10): only the kind crosses. */
    private fun notice(k: CoreNoticeKind): NoticeKind =
        when (k) {
            CoreNoticeKind.InstructionsUpdated -> NoticeKind.InstructionsUpdated
            CoreNoticeKind.SkillChanged -> NoticeKind.SkillChanged
            CoreNoticeKind.ShellChanged -> NoticeKind.ShellChanged
            else -> NoticeKind.Other
        }
}
