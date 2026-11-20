package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of Flutter lib/domain/transcript_settle.dart (B-CHAT-116/117): live state that has outlived what it
// described, cleared before the transcript draws it.

/**
 * The kind a running compaction line takes once its moment has passed: it draws nothing (Dart `compactionPassed`).
 * Native keeps a compaction's kind in `noticeMetadata["kind"]` where Dart kept it in `toolName`.
 */
public const val COMPACTION_PASSED: String = "passed"

private const val COMPACTION_RUNNING = "running"
private const val KIND = "kind"

/** A compaction line still saying it runs (Dart `p.type == 'v2:compaction' && p.toolName == 'running'`). */
public fun isCompactionLineLive(part: Part): Boolean =
    isCompaction(part) && part.noticeMetadata[KIND] == COMPACTION_RUNNING

/** [settleStaleLive]'s compaction line that is no longer live: it reads as passed. */
private fun compactionPassed(message: MessageWithParts): MessageWithParts =
    message.copy(
        parts =
            message.parts
                .map { p ->
                    if (isCompactionLineLive(p)) {
                        p.copy(noticeMetadata = (p.noticeMetadata + (KIND to COMPACTION_PASSED)).toPersistentMap())
                    } else {
                        p
                    }
                }
                .toImmutableList()
    )

/**
 * [settleStaleLive]'s still-live step of a turn that ended. Words later in the turn prove the step finished. With none,
 * the run just ended (a force-stop, a lost connection) while the step still said it ran: that is no proof it ran, so
 * it never settles as "Ran 1 command" (B-461 job 2). It stops, and says only what the app knows: a step whose
 * request the person was shown ([asked]) was stopped before they answered; a step the server had started was stopped
 * before it finished; any other step is plainly stopped.
 */
private fun settledStep(part: Part, tool: ToolState, wordsLater: Boolean, asked: Boolean): Part {
    if (wordsLater) return part.copy(tool = tool.copy(status = ToolStatus.Completed))
    val mark =
        when {
            asked -> UNANSWERED_KEY
            tool.startedAtMs != null -> STOPPED_EARLY_KEY
            else -> null
        }
    val metadata =
        if (mark == null) tool.metadata
        else JsonObject((tool.metadata ?: JsonObject(emptyMap())) + (mark to JsonPrimitive(true)))
    return part.copy(tool = tool.copy(status = ToolStatus.Pending, metadata = metadata))
}

/**
 * Dart `settleStaleLive` over [messages] (oldest first).
 *
 * - B-CHAT-116: a running compaction line becomes [COMPACTION_PASSED] once anything follows it or the conversation
 *   is not [busy]. Its message stays in place, so the answer has one message per message given.
 * - B-CHAT-117: a tool call still pending or running (and one the server ran) is shown completed when the same
 *   turn wrote words after it. When the conversation is not [busy] and no words came after it, it stops
 *   ([settledStep]). A background process (`background: true` in its metadata or input) is never settled, nor a
 *   delegated step whose sub-agent is in [liveChildren] (working or waiting on the person): the parent going idle
 *   does not end that step. Words of an earlier turn never finish a later turn's tool.
 *
 * Returns [messages] itself when nothing needed settling. [moreFollows]: [messages] is the start of a longer
 * conversation (the rest begins with a prompt), so its last message is not the newest. [askedCalls]: tool call ids
 * the person was shown a request for. [liveChildren]: raw session ids of sub-agents still working or waiting.
 */
public fun settleStaleLive(
    messages: List<MessageWithParts>,
    busy: Boolean,
    moreFollows: Boolean = false,
    askedCalls: Set<String> = emptySet(),
    liveChildren: Set<String> = emptySet(),
): List<MessageWithParts> {
    val replace = HashMap<Int, MessageWithParts>()
    val tools = ToolSettle(busy, askedCalls, liveChildren)
    for (i in messages.indices.reversed()) {
        val message = messages[i]
        if (isPrompt(message)) {
            tools.wordsLater = false
            continue
        }
        val compaction = message.parts.any(::isCompactionLineLive)
        if (compaction && (!busy || moreFollows || i < messages.lastIndex)) {
            replace[i] = compactionPassed(message)
            continue
        }
        if (message.info.role != Role.Assistant) continue
        tools.parts(message)?.let { replace[i] = message.copy(parts = it.toImmutableList()) }
    }
    if (replace.isEmpty()) return messages
    return messages.indices.map { replace[it] ?: messages[it] }
}

/** [settleStaleLive]'s walk over the steps of one turn, newest first. */
private class ToolSettle(val busy: Boolean, val askedCalls: Set<String>, val liveChildren: Set<String>) {
    /** Words written later in the same turn (the walk goes from the end). */
    var wordsLater = false

    /** A live step settles once later words of its turn exist or the run ended. */
    private val due: Boolean
        get() = wordsLater || !busy

    /** [message]'s parts with its stale steps settled, or null when none changed. */
    fun parts(message: MessageWithParts): List<Part>? {
        var parts: MutableList<Part>? = null
        for (p in message.parts.indices.reversed()) {
            val part = message.parts[p]
            val tool = part.tool
            if (part.type == PartType.Text && part.text.isNotBlank()) {
                wordsLater = true
            } else if (tool != null && due && settles(part, tool, liveChildren)) {
                val edited = parts ?: message.parts.toMutableList().also { parts = it }
                edited[p] = settledStep(part, tool, wordsLater, asked = part.callId?.let { it in askedCalls } == true)
            }
        }
        return parts
    }
}

/** A live tool call the server ran that may settle: not a background process, not a step whose sub-agent is live. */
private fun settles(part: Part, tool: ToolState, liveChildren: Set<String>): Boolean =
    part.type == PartType.Tool && tool.ranLive && !keepsRunning(part, tool, liveChildren)

/** Still says it runs, and the server ran it. */
private val ToolState.ranLive: Boolean
    get() = status.live && ran

/** A step that outlives its turn: a background process, or a delegated step whose sub-agent is live. */
private fun keepsRunning(part: Part, tool: ToolState, liveChildren: Set<String>): Boolean =
    isBackground(part) || waitsOnChild(tool, liveChildren)

/** A delegated step whose sub-agent still works or waits on the person: its turn has not ended for it. */
public fun waitsOnChild(tool: ToolState, liveChildren: Set<String>): Boolean =
    liveChildren.isNotEmpty() && taskChildSessionId(tool.metadata)?.let { it in liveChildren } == true

/**
 * The status a tool call keeps when a later report about the same call arrives (Dart Paseo `_mergeToolCall`): a
 * step that has finished does not start again, so a late report that still says it runs (a replay after
 * compaction, a reconnect) cannot revive its spinner. Any other later report wins.
 */
public fun settledToolStatus(earlier: ToolStatus, later: ToolStatus): ToolStatus =
    if (!earlier.live && later.live) earlier else later

private val ToolStatus.live: Boolean
    get() = this == ToolStatus.Running || this == ToolStatus.Pending

/** A message the person sent: a user message with no server notice (`v2:*`) in it. */
private fun isPrompt(message: MessageWithParts): Boolean =
    message.info.role == Role.User && message.parts.none { it.rawType.startsWith("v2:") || isNoticeType(it.type) }

private fun isNoticeType(type: PartType): Boolean =
    type == PartType.Compaction || type == PartType.Notice || type == PartType.Switch

private fun isCompaction(part: Part): Boolean = part.type == PartType.Compaction || part.rawType == "v2:compaction"

private fun isBackground(part: Part): Boolean =
    isTrue(part.tool?.metadata?.get("background")) || isTrue(part.tool?.input?.get("background"))

/** Dart `value == true`: only the JSON boolean true. */
private fun isTrue(value: JsonElement?): Boolean = value is JsonPrimitive && !value.isString && value.content == "true"

/** Set on a step the run ended on while it still waited for an answer (settled as not run, never as completed). */
public const val UNANSWERED_KEY: String = "oc.unanswered"

/** Set on a step the server had started when the run ended with no words after it: stopped before it finished. */
public const val STOPPED_EARLY_KEY: String = "oc.stoppedEarly"
