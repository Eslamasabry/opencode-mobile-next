package io.github.eslamasabry.oc.engine.delivery.queue

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One entry of `oc.offlineQueue` (port of `lib/state/offline_queue.dart` `QueuedPrompt`; plan 04
 * §13.3, plan 05 §13).
 *
 * The lists are immutable, so a caller that keeps editing its own lists after queuing can never
 * change a stored entry (B-STATE-033). Attachments keep only `mime`, `filename` and `url`, exactly
 * what Flutter persists.
 *
 * [dispatchedAt] is the **dispatch marker**: unix ms when a send handed this entry to the
 * transport. It is written to disk before the request leaves the device and cleared only by
 * positive evidence (the server accepted it and the entry is removed) or by the person's explicit
 * resend. While it is set the entry never flushes on its own, in this process or after a restart
 * (B-STATE-040…043).
 *
 * `toString` never prints the text or attachment URLs (rule 7: no prompt text in logs).
 */
public class QueuedPrompt(
    public val id: String,
    public val profileId: ProfileId,
    public val sessionId: SessionId,
    public val text: String,
    attachments: List<PromptAttachment> = persistentListOf(),
    mentions: List<PromptAgentMention> = persistentListOf(),
    public val modelProviderId: String? = null,
    public val modelId: String? = null,
    public val agent: String? = null,
    public val variant: String? = null,
    public val createdAt: Long,
    public val error: String? = null,
    public val dispatchedAt: Long? = null,
) {
    /**
     * Snapshot of the caller's list (B-STATE-033). Only the three persisted fields survive, as in
     * Flutter.
     */
    public val attachments: ImmutableList<PromptAttachment> =
        attachments
            .map { PromptAttachment(mime = it.mime, filename = it.filename, url = it.url) }
            .toImmutableList()

    public val mentions: ImmutableList<PromptAgentMention> = mentions.toImmutableList()

    public val ref: ConversationRef
        get() = ConversationRef(profileId, sessionId)

    /**
     * A send left the device without a confirmed outcome: the entry waits for the person, never the
     * next flush.
     */
    public val dispatched: Boolean
        get() = dispatchedAt != null

    public val model: ModelRef?
        get() =
            if (modelProviderId != null && modelId != null) ModelRef(modelProviderId, modelId)
            else null

    /**
     * Flutter's `payloadBytes`, measured exactly as Dart does: UTF-16 code units of the text plus
     * every attachment URL's length. Not bytes. The same stored queue keeps and evicts the same
     * entries in both apps (plan 04 §13.3).
     */
    public val payloadBytes: Long
        get() = text.length.toLong() + attachments.sumOf { it.url.length.toLong() }

    public fun withError(error: String?): QueuedPrompt =
        copy(error = error, dispatchedAt = dispatchedAt)

    public fun withDispatchedAt(dispatchedAt: Long?): QueuedPrompt =
        copy(error = error, dispatchedAt = dispatchedAt)

    public fun copy(
        sessionId: SessionId = this.sessionId,
        profileId: ProfileId = this.profileId,
        error: String? = this.error,
        dispatchedAt: Long? = this.dispatchedAt,
    ): QueuedPrompt =
        QueuedPrompt(
            id,
            profileId,
            sessionId,
            text,
            attachments,
            mentions,
            modelProviderId,
            modelId,
            agent,
            variant,
            createdAt,
            error,
            dispatchedAt,
        )

    public fun movedTo(
        destination: ConversationRef,
        availableModels: Set<ModelRef>,
        availableAgents: Set<String>,
        redactedText: String,
    ): QueuedPrompt {
        val keptModel = model?.takeIf { it in availableModels }
        return QueuedPrompt(
            id = id,
            profileId = destination.profileId,
            sessionId = destination.sessionId,
            text = redactedText,
            attachments = attachments,
            mentions = mentions,
            modelProviderId = keptModel?.providerId,
            modelId = keptModel?.modelId,
            agent = agent?.takeIf { it in availableAgents },
            variant = variant.takeIf { keptModel != null },
            createdAt = createdAt,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is QueuedPrompt &&
            other.id == id &&
            other.profileId == profileId &&
            other.sessionId == sessionId &&
            other.text == text &&
            other.attachments == attachments &&
            other.mentions == mentions &&
            other.modelProviderId == modelProviderId &&
            other.modelId == modelId &&
            other.agent == agent &&
            other.variant == variant &&
            other.createdAt == createdAt &&
            other.error == error &&
            other.dispatchedAt == dispatchedAt

    override fun hashCode(): Int = id.hashCode() * 31 + createdAt.hashCode()

    override fun toString(): String =
        "QueuedPrompt(id=$id, session=${sessionId.raw}, textLen=${text.length}, attachments=${attachments.size}, " +
            "dispatched=$dispatched, hasError=${error != null})"
}

/**
 * Thrown inside [QueuedPromptCodec.decodeQueue] only; the store turns it into "unreadable" and
 * never shows it.
 */
public class QueueFormatException internal constructor() : Exception("unreadable queue")

/**
 * The exact Flutter codec (plan 04 §13.3). [encodeQueue] writes what Dart's `jsonEncode([for e in
 * entries e.toJson()])` writes, key for key, with explicit `null` for absent optionals, so a queue
 * written by native reads the same in a byte diff. [decodeQueue] reads like
 * `OfflineQueueStore.load` + `QueuedPrompt.fromJson`, including the places where Dart fails the
 * **whole** queue (a non-list blob, an entry without id/profile/session, a repeated id, a
 * non-number where a number is cast).
 */
public object QueuedPromptCodec {
    public fun toJson(entry: QueuedPrompt): LinkedHashMap<String, Any?> =
        linkedMapOf(
            "id" to entry.id,
            "profileID" to entry.profileId.raw,
            "sessionID" to entry.sessionId.raw,
            "text" to entry.text,
            "attachments" to
                entry.attachments.map {
                    linkedMapOf<String, Any?>(
                        "mime" to it.mime,
                        "filename" to it.filename,
                        "url" to it.url,
                    )
                },
            "mentions" to
                entry.mentions.map {
                    linkedMapOf<String, Any?>(
                        "name" to it.name,
                        "value" to it.value,
                        "start" to it.start.toLong(),
                        "end" to it.end.toLong(),
                    )
                },
            "modelProviderID" to entry.modelProviderId,
            "modelID" to entry.modelId,
            "agent" to entry.agent,
            "variant" to entry.variant,
            "createdAt" to entry.createdAt,
            "error" to entry.error,
            "dispatchedAt" to entry.dispatchedAt,
        )

    public fun encodeQueue(entries: List<QueuedPrompt>): String =
        DartJson.encode(entries.map(::toJson))

    /**
     * Null for an entry Dart's `fromJson` returns null for. Throws [QueueFormatException] where
     * Dart's casts throw.
     */
    public fun fromJson(value: Any?): QueuedPrompt? {
        if (value !is Map<*, *>) return null
        val id = dartString(value["id"]) ?: ""
        val profileId = dartString(value["profileID"]) ?: ""
        val sessionId = dartString(value["sessionID"]) ?: ""
        if (id.isEmpty() || profileId.isEmpty() || sessionId.isEmpty()) return null
        val attachments =
            (value["attachments"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
                PromptAttachment(
                    mime = dartString(it["mime"]) ?: "",
                    filename = dartString(it["filename"]) ?: "",
                    url = dartString(it["url"]) ?: "",
                )
            }
        val mentions =
            (value["mentions"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
                PromptAgentMention(
                    name = dartString(it["name"]) ?: "",
                    value = dartString(it["value"]) ?: "",
                    start = dartNumToInt(it["start"])?.toInt() ?: 0,
                    end = dartNumToInt(it["end"])?.toInt() ?: 0,
                )
            }
        return QueuedPrompt(
            id = id,
            profileId = ProfileId(profileId),
            sessionId = SessionId(sessionId),
            text = dartString(value["text"]) ?: "",
            attachments = attachments,
            mentions = mentions,
            modelProviderId = dartString(value["modelProviderID"]),
            modelId = dartString(value["modelID"]),
            agent = dartString(value["agent"]),
            variant = dartString(value["variant"]),
            createdAt = dartNumToInt(value["createdAt"]) ?: 0,
            error = dartString(value["error"]),
            dispatchedAt = dartNumToInt(value["dispatchedAt"]),
        )
    }

    /** `OfflineQueueStore.load`: the whole queue or nothing. Empty/absent → empty list. */
    public fun decodeQueue(raw: String): List<QueuedPrompt> {
        if (raw.isEmpty()) return emptyList()
        val decoded = DartJson.decodeOrNull(raw) ?: throw QueueFormatException()
        if (decoded !is List<*>) throw QueueFormatException()
        val seen = HashSet<String>()
        return decoded.map { item ->
            val prompt = fromJson(item) ?: throw QueueFormatException()
            if (!seen.add(prompt.id)) throw QueueFormatException()
            prompt
        }
    }

    /**
     * Dart `value?.toString()`: strings as they are, numbers and booleans in Dart's spelling, null
     * stays null.
     */
    internal fun dartString(value: Any?): String? =
        when (value) {
            null -> null
            is String -> value
            is Long,
            is Int -> value.toString()
            is Double -> dartDouble(value)
            is Boolean -> value.toString()
            else ->
                DartJson.encode(
                    value
                ) // lists and maps: never an id in practice; any stable text keeps the entry
        }

    /**
     * Dart `(value as num?)?.toInt()`: null stays null, a number truncates, anything else throws
     * (fails the queue).
     */
    private fun dartNumToInt(value: Any?): Long? =
        when (value) {
            null -> null
            is Long -> value
            is Int -> value.toLong()
            is Double ->
                if (value.isNaN() || value.isInfinite()) throw QueueFormatException()
                else value.toLong()
            else -> throw QueueFormatException()
        }

    private fun dartDouble(value: Double): String =
        when {
            value.isNaN() -> "NaN"
            value.isInfinite() -> if (value > 0) "Infinity" else "-Infinity"
            value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e21 ->
                "${value.toLong()}.0"
            else -> value.toString()
        }
}
