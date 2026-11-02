package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.json.JsonElement
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

/**
 * Dart `settleStaleLive` over [messages] (oldest first).
 *
 * - B-CHAT-116: a running compaction line becomes [COMPACTION_PASSED] once anything follows it or the conversation
 *   is not [busy]. Its message stays in place, so the answer has one message per message given.
 * - B-CHAT-117: a tool call still pending or running (and one the server ran) is shown completed when the same
 *   turn wrote words after it, or the conversation is not [busy], unless it is a background process
 *   (`background: true` in its metadata or input). Words of an earlier turn never finish a later turn's tool.
 *
 * Returns [messages] itself when nothing needed settling. [moreFollows]: [messages] is the start of a longer
 * conversation (the rest begins with a prompt), so its last message is not the newest.
 */
public fun settleStaleLive(
    messages: List<MessageWithParts>,
    busy: Boolean,
    moreFollows: Boolean = false,
): List<MessageWithParts> {
    val replace = HashMap<Int, MessageWithParts>()
    // Words written later in the same turn, walking from the end.
    var wordsLater = false
    for (i in messages.indices.reversed()) {
        val message = messages[i]
        if (isPrompt(message)) {
            wordsLater = false
            continue
        }
        val compaction = message.parts.any(::isCompactionLineLive)
        if (compaction && (!busy || moreFollows || i < messages.lastIndex)) {
            replace[i] =
                message.copy(
                    parts =
                        message.parts
                            .map { p ->
                                if (isCompactionLineLive(p)) {
                                    p.copy(
                                        noticeMetadata =
                                            (p.noticeMetadata + (KIND to COMPACTION_PASSED)).toPersistentMap()
                                    )
                                } else {
                                    p
                                }
                            }
                            .toImmutableList()
                )
            continue
        }
        if (message.info.role != Role.Assistant) continue
        var parts: MutableList<Part>? = null
        for (p in message.parts.indices.reversed()) {
            val part = message.parts[p]
            val tool = part.tool
            if (part.type == PartType.Text && part.text.isNotBlank()) {
                wordsLater = true
            } else if (
                part.type == PartType.Tool &&
                    tool != null &&
                    (tool.status == ToolStatus.Running || tool.status == ToolStatus.Pending) &&
                    tool.ran &&
                    (wordsLater || !busy) &&
                    !isBackground(part)
            ) {
                val edited = parts ?: message.parts.toMutableList().also { parts = it }
                edited[p] = part.copy(tool = tool.copy(status = ToolStatus.Completed))
            }
        }
        parts?.let { replace[i] = message.copy(parts = it.toImmutableList()) }
    }
    if (replace.isEmpty()) return messages
    return messages.indices.map { replace[it] ?: messages[it] }
}

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
