package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// Port of lib/state/context_usage.dart (B-CHAT-019), the KitContextLevel rule of lib/ui/kit/kit_context_ring.dart
// (B-CHAT-057) and calculateSessionContextMetrics of lib/ui/screens/session_context_screen.dart (B-CHAT-102).

// ---- B-CHAT-019: the reading --------------------------------------------------------------------------------------

/** Share of the window [tokens] fill; null when either side is unknown (not positive). Not clamped. */
public fun contextFraction(tokens: Long, limit: Long): Double? =
    if (tokens > 0 && limit > 0) tokens.toDouble() / limit.toDouble() else null

/** One assistant reply that reported tokens, as a share of its model's window. */
public data class ContextReply(
    val messageId: String,
    /** Tokens used by this reply over the model's window (not clamped). */
    val fraction: Double,
    /** A compaction notice stands between this reply and the one before it: the drop in the chart. */
    val afterCompaction: Boolean = false,
)

/** The newest reply's reading. [replies] is oldest first; the last one is the reading itself. */
public data class ContextReading(val tokens: Long, val limit: Long, val replies: List<ContextReply>) {
    public val fraction: Double get() = tokens.toDouble() / limit.toDouble()
}

/**
 * The newest assistant reply that reported tokens, against its own model's window from [contextWindowOf]
 * (providerId, modelId → window, null when the catalog does not know it). Null when there is no catalog
 * ([contextWindowOf] null), no such reply, or the newest reply's model or window is unknown: the ring is then absent,
 * never a guess. The model's display name stays with the caller's catalog.
 */
public fun contextReading(
    messages: List<MessageWithParts>,
    contextWindowOf: ((providerId: String, modelId: String) -> Long?)?,
): ContextReading? {
    if (contextWindowOf == null) return null
    val replies = mutableListOf<ContextReply>()
    var compacted = false
    var tokens: Long? = null
    var limit: Long? = null
    for (message in messages) {
        if (ctxIsCompactionNotice(message)) {
            compacted = true
            continue
        }
        val info = message.info
        if (info.role != Role.Assistant || info.tokens.total <= 0) continue
        val window = ctxWindowOf(info, contextWindowOf)
        val fraction = window?.let { contextFraction(info.tokens.total, it) }
        if (window == null || fraction == null) {
            // The newest reply's model is unknown: no reading.
            tokens = null
            limit = null
            continue
        }
        replies += ContextReply(info.id, fraction, afterCompaction = compacted && replies.isNotEmpty())
        compacted = false
        tokens = info.tokens.total
        limit = window
    }
    if (tokens == null || limit == null || replies.isEmpty()) return null
    return ContextReading(tokens, limit, replies.toList())
}

/** Paseo agent context use (Dart paseoContextUse): used over max from an agent usage report. */
public data class AgentContextUse(val tokens: Long, val limit: Long) {
    public val fraction: Double get() = tokens.toDouble() / limit.toDouble()
}

/** Null when the usage report is absent, partial, not numeric or not positive. */
public fun agentContextUse(usage: JsonElement?): AgentContextUse? {
    if (usage !is JsonObject) return null
    val used = ctxNumber(usage["contextWindowUsedTokens"]) ?: return null
    val max = ctxNumber(usage["contextWindowMaxTokens"]) ?: return null
    if (!used.isFinite() || !max.isFinite() || used <= 0 || max <= 0) return null
    return AgentContextUse(used.roundToLong(), max.roundToLong())
}

private fun ctxNumber(raw: JsonElement?): Double? = (raw as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

private fun ctxIsCompactionNotice(message: MessageWithParts): Boolean =
    message.parts.any { it.type == PartType.Compaction || it.rawType == "v2:compaction" || it.rawType == "compaction" }

private fun ctxWindowOf(info: MessageInfo, contextWindowOf: (String, String) -> Long?): Long? {
    val provider = info.providerId ?: return null
    val model = info.modelId ?: return null
    return contextWindowOf(provider, model)
}

// ---- B-CHAT-057: the ring level ------------------------------------------------------------------------------------

public enum class ContextLevel {
    /** Under 70 %. */
    Calm,

    /** 70 % to under 95 %. */
    Warning,

    /** 95 % and over. */
    Full,
}

public const val CONTEXT_WARNING_FROM: Double = 0.7
public const val CONTEXT_FULL_FROM: Double = 0.95

public fun contextLevel(fraction: Double): ContextLevel =
    when {
        fraction >= CONTEXT_FULL_FROM -> ContextLevel.Full
        fraction >= CONTEXT_WARNING_FROM -> ContextLevel.Warning
        else -> ContextLevel.Calm
    }

/**
 * A whole percent, floored so "95 %" never shows below the full line; the epsilon absorbs binary fractions
 * (0.7 * 100 = 70.000…01). Clamped to 0–100.
 */
public fun contextPercent(fraction: Double): Int =
    floor(fraction.coerceIn(0.0, 1.0) * 100 + 1e-9).toInt().coerceIn(0, 100)

/** The ring sends its one halo when the value crosses 70 % or 95 % upward (never on the way down or while above). */
public fun contextLevelCrossedUp(before: Double, after: Double): Boolean =
    (before < CONTEXT_WARNING_FROM && after >= CONTEXT_WARNING_FROM) ||
        (before < CONTEXT_FULL_FROM && after >= CONTEXT_FULL_FROM)

// ---- B-CHAT-102: the context screen's facts ------------------------------------------------------------------------

public enum class ContextBreakdownKind { User, Assistant, Tool, Other }

/** An estimated share of the latest input: always worded as an estimate. */
public data class ContextBreakdownSegment(val kind: ContextBreakdownKind, val tokens: Long, val percent: Double)

public data class ContextSheetFacts(
    /** The latest assistant message that reported tokens: the truth the metrics use. */
    val currentMessageId: String?,
    val providerId: String?,
    val modelId: String?,
    /** That message's tokens, cache read and write included in [contextTokens]. */
    val tokens: Tokens,
    /** The model's window; null when unknown. */
    val contextLimit: Long?,
    val userMessages: Int,
    val assistantMessages: Int,
    /** Sum of assistant message costs over the loaded messages. */
    val sessionCost: Double,
    /** Estimated makeup of the latest input from visible text; empty when no input tokens. */
    val breakdown: List<ContextBreakdownSegment>,
) {
    public val contextTokens: Long get() = tokens.total

    /** Share of the window; null when the window is unknown. */
    public val usage: Double?
        get() = contextLimit?.takeIf { it > 0 }?.let { contextTokens.toDouble() / it.toDouble() }

    /** The breakdown is an estimate (Dart "Estimated input makeup"); the feature must label it so. */
    public val estimated: Boolean get() = breakdown.isNotEmpty()
}

/**
 * Facts for the context screen: metrics from the newest assistant message that reported tokens, against [window]
 * (that message's model window, looked up by the caller; null when unknown).
 */
public fun contextSheetFacts(messages: List<MessageWithParts>, window: Long?): ContextSheetFacts {
    val current = messages.lastOrNull { it.info.role == Role.Assistant && it.info.tokens.total > 0 }?.info
    var userMessages = 0
    var assistantMessages = 0
    var sessionCost = 0.0
    for (message in messages) {
        when (message.info.role) {
            Role.User -> userMessages++
            Role.Assistant -> {
                assistantMessages++
                sessionCost += message.info.cost
            }
        }
    }
    return ContextSheetFacts(
        currentMessageId = current?.id,
        providerId = current?.providerId,
        modelId = current?.modelId,
        tokens = current?.tokens ?: Tokens(),
        contextLimit = window?.takeIf { it > 0 && current?.providerId != null && current?.modelId != null },
        userMessages = userMessages,
        assistantMessages = assistantMessages,
        sessionCost = sessionCost,
        breakdown = ctxEstimateBreakdown(messages, current?.tokens?.input ?: 0),
    )
}

@OptIn(ExperimentalSerializationApi::class)
private val CTX_PRETTY = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** Dart ToolState.inputJson: the raw streamed input while pending, else the structured input as indented JSON. */
private fun ctxInputText(status: ToolStatus, input: JsonObject, raw: String?): String =
    if (status == ToolStatus.Pending) raw ?: "" else CTX_PRETTY.encodeToString(JsonObject.serializer(), input)

private fun ctxEstimateBreakdown(messages: List<MessageWithParts>, inputTokens: Long): List<ContextBreakdownSegment> {
    if (inputTokens <= 0) return emptyList()
    var userChars = 0L
    var assistantChars = 0L
    var toolChars = 0L
    for (message in messages) {
        when (message.info.role) {
            Role.User ->
                for (part in message.parts) {
                    if (part.type == PartType.Text) userChars += part.text.length
                    if (part.type == PartType.File) userChars += (part.filename ?: part.url ?: "").length
                }
            Role.Assistant ->
                for (part in message.parts) {
                    if (part.type == PartType.Text || part.type == PartType.Reasoning) {
                        assistantChars += part.text.length
                    } else if (part.type == PartType.Tool) {
                        val state = part.tool ?: continue
                        toolChars += ctxInputText(state.status, state.input, state.inputRaw).length
                        toolChars += state.output?.length ?: 0
                    }
                }
        }
    }
    fun estimatedTokens(chars: Long): Long = ceil(chars / 4.0).toLong()
    var user = estimatedTokens(userChars)
    var assistant = estimatedTokens(assistantChars)
    var tool = estimatedTokens(toolChars)
    val estimated = user + assistant + tool
    if (estimated > inputTokens && estimated > 0) {
        val scale = inputTokens.toDouble() / estimated.toDouble()
        user = floor(user * scale).toLong()
        assistant = floor(assistant * scale).toLong()
        tool = floor(tool * scale).toLong()
    }
    val other = max(0L, inputTokens - user - assistant - tool)
    return listOf(
            ContextBreakdownKind.User to user,
            ContextBreakdownKind.Assistant to assistant,
            ContextBreakdownKind.Tool to tool,
            ContextBreakdownKind.Other to other,
        )
        .filter { it.second > 0 }
        .map { (kind, tokens) -> ContextBreakdownSegment(kind, tokens, tokens.toDouble() / inputTokens * 100) }
}
