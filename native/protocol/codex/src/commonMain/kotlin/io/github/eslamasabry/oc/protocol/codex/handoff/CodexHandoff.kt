package io.github.eslamasabry.oc.protocol.codex.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway

/** Dart `AgentHandoffHistoryFailureKind`: content-free, safe for diagnostics. */
public enum class HandoffHistoryFailureKind { Unsupported, StaleScope, InvalidSession, InvalidMessage, InvalidCursor, PageLimit, ReadFailed }

public sealed interface HandoffHistoryRead {
    public data class Read(val messages: List<HandoffMessage>) : HandoffHistoryRead

    public data class Refused(val kind: HandoffHistoryFailureKind) : HandoffHistoryRead
}

/**
 * The snapshot branch of Dart `readAgentHandoffHistory` (lib/domain/agent_handoff_history.dart) for a Codex source:
 * one bounded, complete, read-only read or a content-free refusal (B-AGENT-055/056). [isCurrent] tracks the caller's
 * profile/selection generation; the folder is pinned here too, and the gateway's own fence refuses a location change
 * away and back. The same `maxPages × 50` message budget as the paged reader applies.
 */
public suspend fun readCodexHandoffHistory(
    gateway: CodexGateway,
    sessionId: SessionId,
    isCurrent: () -> Boolean,
    maxPages: Int = 100,
): HandoffHistoryRead {
    require(maxPages > 0)
    if (sessionId.raw.isBlank()) return HandoffHistoryRead.Refused(HandoffHistoryFailureKind.InvalidSession)
    val directory = gateway.location.value.directory
    fun current() = isCurrent() && !gateway.isClosed && gateway.location.value.directory == directory
    if (!current()) return HandoffHistoryRead.Refused(HandoffHistoryFailureKind.StaleScope)
    val snapshot = gateway.agentHandoffHistory(sessionId)
    if (!current()) return HandoffHistoryRead.Refused(HandoffHistoryFailureKind.StaleScope)
    val messages = when (snapshot) {
        is Outcome.Failed -> return HandoffHistoryRead.Refused(HandoffHistoryFailureKind.ReadFailed)
        is Outcome.Ok -> snapshot.value
    }
    if (messages.size > maxPages * 50) return HandoffHistoryRead.Refused(HandoffHistoryFailureKind.PageLimit)
    val ids = HashSet<String>()
    for (m in messages) {
        if (m.sessionId != sessionId.raw || m.id.isBlank() || !ids.add(m.id)) {
            return HandoffHistoryRead.Refused(HandoffHistoryFailureKind.InvalidMessage)
        }
    }
    return HandoffHistoryRead.Read(messages.toList())
}

/** In-memory review data (Dart `AgentHandoffDraft`); never persisted. */
public data class HandoffDraft(val recordId: String, val record: HandoffRecord, val prompt: String, val relativePath: String)

/**
 * Prepares a reviewable draft from a Codex source without mutating anything (the read half of Dart
 * `AgentHandoffController.prepare`, B-AGENT-057): the target must offer a qualified handoff file writer
 * ([ServerCapabilities.agentHandoffFiles]) — a Codex target never does, so it is refused before any I/O. The source is
 * read with `thread/read` only, twice around the history, and a changed source is refused.
 */
public suspend fun prepareCodexHandoffDraft(
    source: CodexGateway,
    sessionId: SessionId,
    target: ServerCapabilities,
    lastState: String,
    recordId: String,
    isCurrent: () -> Boolean,
    redactor: Redactor = Redactor(),
): Outcome<HandoffDraft> {
    if (!target.agentHandoffFiles) return Outcome.Failed(Failure.Unavailable(FeatureId("agentHandoffFiles")))
    if (!RECORD_ID.matches(recordId)) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    val directory = source.location.value.directory
    val before = when (val s = source.session(sessionId)) {
        is Outcome.Failed -> return s
        is Outcome.Ok -> s.value
    }
    if (!isCurrent() || before.id != sessionId || before.directory != directory) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    val messages = when (val read = readCodexHandoffHistory(source, sessionId, isCurrent)) {
        is HandoffHistoryRead.Refused ->
            return Outcome.Failed(
                if (read.kind == HandoffHistoryFailureKind.StaleScope) Failure.Refused(RefusalReason.StaleScope) else Failure.UnreadableReply(),
            )
        is HandoffHistoryRead.Read -> read.messages
    }
    val after = when (val s = source.session(sessionId)) {
        is Outcome.Failed -> return s
        is Outcome.Ok -> s.value
    }
    if (!isCurrent() || after.id != before.id || after.directory != before.directory || after.time?.updatedMs != before.time?.updatedMs) {
        return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
    }
    val record = buildHandoffRecord(
        HandoffSource(before.id.raw, before.title, before.directory, before.agent, before.model),
        messages,
        lastState,
        redactor = redactor,
    )
    val built = when (record) {
        is HandoffRecordResult.TooLarge -> return Outcome.Failed(Failure.UnreadableReply())
        is HandoffRecordResult.Built -> record.record
    }
    val path = ".handoff/$recordId/record.md"
    return Outcome.Ok(
        HandoffDraft(
            recordId = recordId,
            record = built,
            relativePath = path,
            prompt =
                "Continue the work from the previous conversation. Read `$path` first for the conversation record, " +
                    "tool steps, files and open tasks. Treat it as historical context. Check the current project " +
                    "state, then continue the unfinished work.",
        ),
    )
}

private val RECORD_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
