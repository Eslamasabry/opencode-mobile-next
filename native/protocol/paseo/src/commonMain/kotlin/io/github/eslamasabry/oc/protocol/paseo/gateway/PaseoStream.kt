package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.domain.events.UserPromptEcho
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoItemId
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoItemMessage
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoMergeToolCall
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.paseoText
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Port of Dart lib/paseo/gateway/events.dart (`_onStream`, `_onTimelineItem`, `_finishOpen`) onto the neutral
 * GatewayEvent vocabulary (B-CHAT-070, 071):
 *   turn_started → RunStarted; turn_completed → RunEnded(Succeeded); turn_canceled → RunEnded(Interrupted(User));
 *   turn_failed → RunEnded(Failed(tag PaseoTurnFailed)), or RunEnded(Succeeded) when the reply already showed the
 *   agent's own error row (no second error; Dart emitted only `session.idle` then);
 *   a new item → MessageUpserted + PartUpserted; a streamed delta of a known item → PartDelta; a turn end finishes
 *   the open replies (MessageUpserted with a completion time).
 */

/**
 * The run's failure tag: words come from the app ("The agent could not finish this reply"), never
 * the daemon.
 */
public val PaseoTurnFailedTag: WireTag = WireTag("PaseoTurnFailed")

internal fun PaseoGateway.onStream(id: String, event: JsonObject, payload: JsonObject) {
    val state = live.getOrPut(id) { PaseoLive() }
    val seq = payload["seq"].longOrNull()
    val epoch = payload.str("epoch")
    if (seq != null && epoch != null) {
        // A replayed sequence number of the same stream epoch is ignored.
        if (state.epoch == epoch && seq <= state.lastSeq) return
        state.epoch = epoch
        state.lastSeq = seq
    }
    val session = SessionId(id)
    when (event.str("type")) {
        "turn_started" -> {
            state.runId = null
            state.turnErrorShown = false
            state.failedBeforeLoad = false
            awaitingTurn.remove(id)
            turnActive += id
            emitStatus(id, SessionStatus.Busy)
        }
        "turn_completed",
        "turn_canceled" -> {
            state.runId = null
            awaitingTurn.remove(id)
            turnActive.remove(id)
            finishOpen(state)
            emitStatus(id, SessionStatus.Idle)
            emit(
                GatewayEvent.RunEnded(
                    session,
                    if (event.str("type") == "turn_canceled")
                        RunOutcome.Interrupted(InterruptReason.User)
                    else RunOutcome.Succeeded,
                )
            )
        }
        "turn_failed" -> {
            state.runId = null
            awaitingTurn.remove(id)
            turnActive.remove(id)
            finishOpen(state)
            emitStatus(id, SessionStatus.Idle)
            val shown = state.turnErrorShown
            state.turnErrorShown = false
            emit(
                GatewayEvent.RunEnded(
                    session,
                    if (shown) RunOutcome.Succeeded
                    else RunOutcome.Failed(null, PaseoTurnFailedTag, null),
                )
            )
        }
        "permission_requested" -> event.obj("request")?.let { requests.onPermissionRequest(id, it) }
        "permission_resolved" -> event.str("requestId")?.let { requests.onPermissionResolved(it) }
        "timeline" -> {
            val item = event.obj("item") ?: return
            onTimelineItem(
                id,
                state,
                item,
                paseoText(event["provider"], max = 128),
                seq,
                paseoMillis(payload["timestamp"]),
            )
        }
    }
}

private fun PaseoGateway.onTimelineItem(
    id: String,
    state: PaseoLive,
    raw: JsonObject,
    provider: String,
    seq: Long?,
    at: Long?,
) {
    var item = raw
    val type = item.str("type") ?: return
    val streamed = type == "assistant_message" || type == "reasoning"
    val own = if (type == "reasoning") null else item.str("messageId")
    val messageId: String
    if (streamed && own.isNullOrEmpty()) {
        // A run of id-less deltas shares the id of its first delta until another kind interrupts
        // it.
        if (state.runType != type || state.runId == null) {
            state.runType = type
            state.runId = paseoItemId(item, seq)
        }
        messageId = state.runId!!
    } else {
        state.runId = null
        state.runType = null
        messageId = paseoItemId(item, seq)
    }
    if (type == "tool_call") {
        item = paseoMergeToolCall(state.tools.remove(messageId), item)
        // The person rejected this call's card: its error is their own reason (B-AGENT-186).
        if (item.str("status") == "failed" && requests.wasRejected(messageId))
            item = item.with("declinedByPerson", JsonPrimitive(true))
        state.tools[messageId] = item
        while (state.tools.size > 2048) state.tools.remove(state.tools.keys.first())
    }
    val text = state.announced[messageId]
    if (streamed && text != null) {
        val delta = paseoText(item["text"])
        text.append(delta)
        emit(
            GatewayEvent.PartDelta(
                SessionId(id),
                MessageId(messageId),
                PartId("$messageId:0"),
                if (type == "reasoning") DeltaField.Reasoning else DeltaField.Text,
                delta,
            )
        )
        return
    }
    val message =
        paseoItemMessage(
            agentId = id,
            item = item,
            id = messageId,
            provider = provider,
            createdMs = at ?: nowMs(),
            completedMs = if (type == "user_message") at else null,
        ) ?: return
    backgroundTasks.observe(real(id), message)
    if (message.info.errorText != null) state.turnErrorShown = true
    if (text == null) {
        state.announced[messageId] = StringBuilder(if (streamed) paseoText(item["text"]) else "")
        while (state.announced.size > 4096) state.announced.remove(state.announced.keys.first())
        val correlation = item.str("clientMessageId") ?: item.str("messageId")
        val echo =
            if (
                type == "user_message" &&
                    correlation != null &&
                    correlation.isNotEmpty() &&
                    correlation.length <= 256
            ) {
                UserPromptEcho(MessageId(correlation), message.parts)
            } else null
        emit(GatewayEvent.MessageUpserted(message.info, echo))
        if (message.info.role == Role.Assistant && message.info.time?.completedMs == null)
            state.open[messageId] = message.info
    }
    for (part in message.parts) {
        emit(GatewayEvent.PartUpserted(rememberAndLink(part.copy(sessionId = id))))
    }
}

/**
 * The turn ended: its replies are finished whether or not the timeline is read again (else they
 * read as cut off).
 */
internal fun PaseoGateway.finishOpen(state: PaseoLive) {
    if (state.open.isEmpty()) return
    val now = nowMs()
    for (info in state.open.values) {
        emit(
            GatewayEvent.MessageUpserted(
                info.copy(time = MsgTime(info.time?.createdMs ?: now, now))
            )
        )
    }
    state.open.clear()
}
