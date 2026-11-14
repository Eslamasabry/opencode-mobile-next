package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo as CoreInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts as CoreMessage
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part as CorePart
import io.github.eslamasabry.oc.core.model.transcript.PartType as CorePartType
import io.github.eslamasabry.oc.core.model.transcript.Role as CoreRole
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolOutputFile
import io.github.eslamasabry.oc.core.model.transcript.ToolSegment
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus as CoreToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Cache-first (owner rule, 2026-11-09): a conversation's last page as it read, kept so the conversation draws its real
 * rows at once when it opens again (before its server answers, or while it cannot). The title and the newest messages
 * in core's full types ([history]): the rows are drawn from them and so are the turn facts (who ended a step, which
 * files an edit changed), so the first frame after a reopen says what the settled one says. [fit] trims it under a
 * byte cap.
 */
@Serializable
public data class KeptTranscript(val title: String? = null, val history: List<KeptMessage> = emptyList()) {
    /** The kept messages, oldest first, as the live ledger holds them. */
    public fun messages(): List<CoreMessage> = history.map { it.toCore() }

    public companion object {
        /** The newest messages kept per conversation: one screen and some scroll-back. */
        public const val MAX_MESSAGES: Int = 60

        /** A tool's long text (output, a file's content, a diff) kept per value; the live read brings the rest back. */
        public const val MAX_TOOL_TEXT: Int = 4_096

        /** A picture sent inline (`data:` address) larger than this is not kept; its row still shows. */
        public const val MAX_INLINE_URL: Int = 16_384

        private val json = Json { encodeDefaults = false }

        /**
         * The newest messages of [messages] that fit in [maxBytes] once encoded, oldest first, with long tool text and
         * large inline pictures trimmed. Null when not even the newest message fits.
         */
        public fun fit(title: String?, messages: List<CoreMessage>, maxBytes: Int): KeptTranscript? {
            val trimmed = messages.takeLast(MAX_MESSAGES).map { KeptMessage.of(it) }
            var from = 0
            while (from < trimmed.size) {
                val kept = KeptTranscript(title, trimmed.subList(from, trimmed.size).toList())
                val size = json.encodeToString(serializer(), kept).encodeToByteArray().size
                if (size <= maxBytes) return kept
                // Drop the oldest quarter (at least one) and try again: a handful of encodes, never one per message.
                from += maxOf(1, (trimmed.size - from) / 4)
            }
            return null
        }

        /**
         * [messages] with the words the transcript drew for them ([words], the reducer's kept rows): the ledger keeps
         * only a streamed reply's first words, the reducer all of them. Parts are matched by message and part id.
         */
        public fun withWords(messages: List<CoreMessage>, words: List<MessageWithParts>): List<CoreMessage> {
            val texts = HashMap<String, String>()
            for (m in words) for (p in m.parts) if (p.text.isNotEmpty()) texts["${m.info.id}/${p.id}"] = p.text
            return messages.map { m ->
                var changed = false
                val parts =
                    m.parts.map { p ->
                        val text = texts["${m.info.id}/${p.id}"]
                        if (
                            text != null &&
                                text.length > p.text.length &&
                                (p.type == CorePartType.Text || p.type == CorePartType.Reasoning)
                        ) {
                            changed = true
                            p.copy(text = text)
                        } else p
                    }
                if (changed) m.copy(parts = parts.toImmutableList()) else m
            }
        }

        /**
         * A long value cut to [MAX_TOOL_TEXT], with the line breaks of the cut part kept: a line count read from it (a
         * new file's "+N") stays true.
         */
        internal fun trim(text: String?): String? {
            if (text == null || text.length <= MAX_TOOL_TEXT) return text
            val head = text.take(MAX_TOOL_TEXT)
            val lines = text.count { it == '\n' } - head.count { it == '\n' }
            val cut = head + "\n".repeat(lines)
            // A last line without a break stays one: the cut must not end on a break the text did not end on.
            return if (cut.endsWith('\n') && !text.endsWith('\n')) "$cut…" else cut
        }

        internal fun trim(value: JsonObject): JsonObject = JsonObject(value.mapValues { (_, v) -> trimJson(v) })

        internal fun trimJson(value: JsonElement): JsonElement =
            when (value) {
                is JsonObject -> trim(value)
                is JsonArray -> JsonArray(value.map { trimJson(it) })
                is JsonPrimitive ->
                    if (value.isString && value.content.length > MAX_TOOL_TEXT) JsonPrimitive(trim(value.content))
                    else value
            }

        internal fun url(url: String?): String? =
            if (url != null && url.length > MAX_INLINE_URL && url.startsWith("data:")) null else url
    }
}

/** One kept message (core's [CoreMessage], serializable). */
@Serializable
public data class KeptMessage(val info: KeptInfo, val parts: List<KeptPart> = emptyList()) {
    public fun toCore(): CoreMessage = CoreMessage(info.toCore(), parts.map { it.toCore() }.toImmutableList())

    public companion object {
        public fun of(m: CoreMessage): KeptMessage = KeptMessage(KeptInfo.of(m.info), m.parts.map { KeptPart.of(it) })
    }
}

@Serializable
public data class KeptInfo(
    val id: String,
    val sessionId: String,
    val role: CoreRole,
    val agent: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val cost: Double = 0.0,
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val timed: Boolean = false,
    val createdMs: Long? = null,
    val completedMs: Long? = null,
    val errorText: String? = null,
    val errorKind: MessageErrorKind? = null,
    val finish: String? = null,
    val parentId: String? = null,
) {
    public fun toCore(): CoreInfo =
        CoreInfo(
            id = id,
            sessionId = sessionId,
            role = role,
            agent = agent,
            providerId = providerId,
            modelId = modelId,
            cost = cost,
            tokens = Tokens(input, output, reasoning, cacheRead, cacheWrite),
            time = if (timed) MsgTime(createdMs, completedMs) else null,
            errorText = errorText,
            errorKind = errorKind,
            finish = finish,
            parentId = parentId,
        )

    public companion object {
        public fun of(i: CoreInfo): KeptInfo =
            KeptInfo(
                id = i.id,
                sessionId = i.sessionId,
                role = i.role,
                agent = i.agent,
                providerId = i.providerId,
                modelId = i.modelId,
                cost = i.cost,
                input = i.tokens.input,
                output = i.tokens.output,
                reasoning = i.tokens.reasoning,
                cacheRead = i.tokens.cacheRead,
                cacheWrite = i.tokens.cacheWrite,
                timed = i.time != null,
                createdMs = i.time?.createdMs,
                completedMs = i.time?.completedMs,
                errorText = i.errorText,
                errorKind = i.errorKind,
                finish = i.finish,
                parentId = i.parentId,
            )
    }
}

@Serializable
public data class KeptPart(
    val id: String,
    val messageId: String,
    val sessionId: String,
    val type: CorePartType,
    val rawType: String,
    val text: String = "",
    val callId: String? = null,
    val toolName: String? = null,
    val tool: KeptTool? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val synthetic: Boolean = false,
    val ended: Boolean = false,
    val noticeMetadata: Map<String, String> = emptyMap(),
) {
    public fun toCore(): CorePart =
        CorePart(
            id = id,
            messageId = messageId,
            sessionId = sessionId,
            type = type,
            rawType = rawType,
            text = text,
            callId = callId,
            toolName = toolName,
            tool = tool?.toCore(),
            mime = mime,
            filename = filename,
            url = url,
            synthetic = synthetic,
            ended = ended,
            noticeMetadata = noticeMetadata.toImmutableMap(),
        )

    public companion object {
        public fun of(p: CorePart): KeptPart =
            KeptPart(
                id = p.id,
                messageId = p.messageId,
                sessionId = p.sessionId,
                type = p.type,
                rawType = p.rawType,
                text = p.text,
                callId = p.callId,
                toolName = p.toolName,
                tool = p.tool?.let { KeptTool.of(it) },
                mime = p.mime,
                filename = p.filename,
                url = KeptTranscript.url(p.url),
                synthetic = p.synthetic,
                ended = p.ended,
                noticeMetadata = p.noticeMetadata,
            )
    }
}

/** A tool call's state with its long text trimmed ([KeptTranscript.trim]); counts, flags and times are kept whole. */
@Serializable
public data class KeptTool(
    val status: CoreToolStatus,
    val title: String? = null,
    val input: JsonObject = JsonObject(emptyMap()),
    val inputRaw: String? = null,
    val output: String? = null,
    val outputValue: JsonElement? = null,
    val metadata: JsonObject? = null,
    val outputFiles: List<KeptFile> = emptyList(),
    val segments: List<KeptSegment> = emptyList(),
    val startedAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val pruned: Boolean = false,
    val executedFlag: Boolean = true,
) {
    public fun toCore(): ToolState =
        ToolState(
            status = status,
            title = title,
            input = input,
            inputRaw = inputRaw,
            output = output,
            outputValue = outputValue,
            metadata = metadata,
            outputFiles = outputFiles.map { it.toCore() }.toImmutableList(),
            segments =
                segments
                    .map { s -> s.file?.let { ToolSegment.File(it.toCore()) } ?: ToolSegment.Text(s.text.orEmpty()) }
                    .toImmutableList(),
            startedAtMs = startedAtMs,
            completedAtMs = completedAtMs,
            pruned = pruned,
            executedFlag = executedFlag,
        )

    public companion object {
        public fun of(t: ToolState): KeptTool =
            KeptTool(
                status = t.status,
                title = t.title,
                input = KeptTranscript.trim(t.input),
                inputRaw = KeptTranscript.trim(t.inputRaw),
                output = KeptTranscript.trim(t.output),
                outputValue = t.outputValue?.let { KeptTranscript.trimJson(it) },
                metadata = t.metadata?.let { KeptTranscript.trim(it) },
                outputFiles = t.outputFiles.map { KeptFile.of(it) },
                segments =
                    t.segments.map { s ->
                        when (s) {
                            is ToolSegment.Text -> KeptSegment(text = KeptTranscript.trim(s.text))
                            is ToolSegment.File -> KeptSegment(file = KeptFile.of(s.file))
                        }
                    },
                startedAtMs = t.startedAtMs,
                completedAtMs = t.completedAtMs,
                pruned = t.pruned,
                executedFlag = t.executedFlag,
            )
    }
}

@Serializable
public data class KeptFile(
    val path: String? = null,
    val filename: String? = null,
    val mime: String? = null,
    val url: String? = null,
) {
    public fun toCore(): ToolOutputFile = ToolOutputFile(path, filename, mime, url)

    public companion object {
        public fun of(f: ToolOutputFile): KeptFile = KeptFile(f.path, f.filename, f.mime, KeptTranscript.url(f.url))
    }
}

/** One output segment: words ([text]) or a file ([file]). */
@Serializable public data class KeptSegment(val text: String? = null, val file: KeptFile? = null)
