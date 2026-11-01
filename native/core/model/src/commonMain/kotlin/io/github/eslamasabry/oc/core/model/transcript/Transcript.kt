package io.github.eslamasabry.oc.core.model.transcript

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Plan 09 §2.2 shapes, written by S0-CORE so gateways and GatewayEvent compile. S2-MODEL owns this file from here:
// it adds MessageErrorKind.fromName/refineFromText, ToolStatus.parse, ToolState.declined and the §2.3 trap tests.
// TODO(S2-MODEL): behaviour helpers and trap rows.

public enum class Role { User, Assistant }

public data class Tokens(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
) {
    /** Same context-total definition as the upstream OpenCode client (Dart Tokens.total). */
    public val total: Long get() = input + output + reasoning + cacheRead + cacheWrite
}

public data class MsgTime(val createdMs: Long?, val completedMs: Long?)

/** Dart MessageErrorKind, same seven values. */
public enum class MessageErrorKind { ContextOverflow, OutputLength, ProviderAuth, ContentFilter, Aborted, ModelNotFound, Unknown }

public data class MessageInfo(
    val id: String,
    val sessionId: String,
    val role: Role,
    val agent: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val cost: Double = 0.0,
    val tokens: Tokens = Tokens(),
    val time: MsgTime? = null,
    /** Server words; shown only through the Redacted Details fold. */
    val errorText: String? = null,
    val errorKind: MessageErrorKind? = null,
    /** Raw: stop | length | tool-calls | content-filter | error | … */
    val finish: String? = null,
    val parentId: String? = null,
)

public enum class PartType { Text, Reasoning, Tool, File, StepStart, StepFinish, Snapshot, Patch, Switch, Notice, Compaction, Unknown }

public enum class ToolStatus { Pending, Running, Completed, Error, Cancelled }

public data class ToolOutputFile(val path: String?, val filename: String?, val mime: String?, val url: String?)

public sealed interface ToolSegment {
    public data class Text(val text: String) : ToolSegment

    public data class File(val file: ToolOutputFile) : ToolSegment
}

public val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

public data class ToolState(
    val status: ToolStatus,
    val title: String? = null,
    /** Complete structured input only. */
    val input: JsonObject = EmptyJsonObject,
    /** Streamed raw input while Pending (never parsed into actions). */
    val inputRaw: String? = null,
    val output: String? = null,
    val outputValue: JsonElement? = null,
    val metadata: JsonObject? = null,
    val outputFiles: ImmutableList<ToolOutputFile> = persistentListOf(),
    val segments: ImmutableList<ToolSegment> = persistentListOf(),
    val startedAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val pruned: Boolean = false,
    /** Raw flag; read [ran] instead (trap T2). */
    val executedFlag: Boolean = true,
) {
    /** Outcome wins over the OC2 beta `executed:false` stamp: completed/error/has time = it ran. */
    public val ran: Boolean
        get() =
            executedFlag || status == ToolStatus.Completed || status == ToolStatus.Error ||
                startedAtMs != null || completedAtMs != null
}

public data class Part(
    val id: String,
    val messageId: String,
    val sessionId: String,
    val type: PartType,
    /** Keeps "v2:notice", "subtask", unknown names. */
    val rawType: String,
    val text: String = "",
    val callId: String? = null,
    val toolName: String? = null,
    val tool: ToolState? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val synthetic: Boolean = false,
    val ended: Boolean = false,
    val noticeMetadata: ImmutableMap<String, String> = persistentMapOf(),
) {
    /** Exactly Dart Part.isRenderable. */
    public val isRenderable: Boolean
        get() =
            !synthetic &&
                when (type) {
                    PartType.Text, PartType.Reasoning -> text.isNotBlank()
                    PartType.Tool, PartType.File -> true
                    else -> false
                }
}

public data class MessageWithParts(val info: MessageInfo, val parts: ImmutableList<Part>)
