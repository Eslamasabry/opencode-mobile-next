package io.github.eslamasabry.oc.protocol.oc2.wire

import io.github.eslamasabry.oc.core.common.json.RawObjectSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ported from lib/api2/models/models_content.dart. Each union reads its discriminator first; anything it does not know
// is kept raw in an Unknown variant, never an error (plan 04 §7.4).

private fun <T> withDiscriminator(serializer: KSerializer<T>, value: T, key: String, tag: String): JsonElement {
    val body = Oc2Wire.json.encodeToJsonElement(serializer, value) as? JsonObject ?: EmptyObject
    return JsonObject(body + (key to JsonPrimitive(tag)))
}

/** One item of a tool result's `content` array, by `type`: `text` | `file`. */
@Serializable(with = Oc2ToolResultItemSerializer::class)
public sealed interface Oc2ToolResultItem {
    @Serializable
    public data class Text(public val text: String = "") : Oc2ToolResultItem

    @Serializable
    public data class File(public val uri: String = "", public val mime: String? = null, public val name: String? = null) :
        Oc2ToolResultItem

    public data class Unknown(public val raw: JsonObject) : Oc2ToolResultItem

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2ToolResultItem = Oc2ToolResultItemSerializer.read(obj)
    }
}

internal object Oc2ToolResultItemSerializer : RawObjectSerializer<Oc2ToolResultItem>("oc2.ToolResultItem") {
    override fun read(obj: JsonObject): Oc2ToolResultItem =
        when (obj.str("type")) {
            "text" -> Oc2Wire.decode(Oc2ToolResultItem.Text.serializer(), obj)
            "file" -> Oc2Wire.decode(Oc2ToolResultItem.File.serializer(), obj)
            else -> null
        } ?: Oc2ToolResultItem.Unknown(obj)

    override fun write(value: Oc2ToolResultItem): JsonElement =
        when (value) {
            is Oc2ToolResultItem.Text -> withDiscriminator(Oc2ToolResultItem.Text.serializer(), value, "type", "text")
            is Oc2ToolResultItem.File -> withDiscriminator(Oc2ToolResultItem.File.serializer(), value, "type", "file")
            is Oc2ToolResultItem.Unknown -> value.raw
        }
}

/** Tool call state, by `status`: `streaming` → `running` → `completed` | `error`. */
@Serializable(with = Oc2ToolStateSerializer::class)
public sealed interface Oc2ToolState {
    /** The result items (completed and error states); empty otherwise. */
    public val content: List<Oc2ToolResultItem> get() = emptyList()

    /** The text items joined with newlines (Dart `textOutput`). */
    public val textOutput: String get() = content.filterIsInstance<Oc2ToolResultItem.Text>().joinToString("\n") { it.text }

    /** Arguments still streaming in: [rawInput] is the partial JSON text. */
    @Serializable
    public data class Streaming(@SerialName("input") public val rawInput: String = "") : Oc2ToolState

    @Serializable
    public data class Running(public val input: JsonObject = EmptyObject, public val metadata: JsonObject? = null) :
        Oc2ToolState

    @Serializable
    public data class Completed(
        public val input: JsonObject = EmptyObject,
        override val content: List<Oc2ToolResultItem> = emptyList(),
        public val metadata: JsonObject? = null,
    ) : Oc2ToolState

    @Serializable
    public data class Error(
        public val input: JsonObject = EmptyObject,
        public val error: Oc2StructuredError? = null,
        override val content: List<Oc2ToolResultItem> = emptyList(),
        public val metadata: JsonObject? = null,
    ) : Oc2ToolState

    public data class Unknown(public val raw: JsonObject) : Oc2ToolState

    public companion object {
        /** Anything that is not an object reads as [Unknown] with an empty body. */
        public fun fromJson(element: JsonElement?): Oc2ToolState = Oc2ToolStateSerializer.read(element as? JsonObject ?: EmptyObject)
    }
}

internal object Oc2ToolStateSerializer : RawObjectSerializer<Oc2ToolState>("oc2.ToolState") {
    override fun read(obj: JsonObject): Oc2ToolState =
        when (obj.str("status")) {
            "streaming" -> Oc2Wire.decode(Oc2ToolState.Streaming.serializer(), obj)
            "running" -> Oc2Wire.decode(Oc2ToolState.Running.serializer(), obj)
            "completed" -> Oc2Wire.decode(Oc2ToolState.Completed.serializer(), obj)
            "error" -> Oc2Wire.decode(Oc2ToolState.Error.serializer(), obj)
            else -> null
        } ?: Oc2ToolState.Unknown(obj)

    override fun write(value: Oc2ToolState): JsonElement =
        when (value) {
            is Oc2ToolState.Streaming -> withDiscriminator(Oc2ToolState.Streaming.serializer(), value, "status", "streaming")
            is Oc2ToolState.Running -> withDiscriminator(Oc2ToolState.Running.serializer(), value, "status", "running")
            is Oc2ToolState.Completed -> withDiscriminator(Oc2ToolState.Completed.serializer(), value, "status", "completed")
            is Oc2ToolState.Error -> withDiscriminator(Oc2ToolState.Error.serializer(), value, "status", "error")
            is Oc2ToolState.Unknown -> value.raw
        }
}

/** `pruned` is set when the server pruned this item's output from the context (newer builds only). */
@Serializable
public data class Oc2ContentTime(
    public val created: Long? = null,
    public val ran: Long? = null,
    public val completed: Long? = null,
    public val pruned: Long? = null,
)

/** Assistant message content item, by `type`: `text` | `reasoning` | `tool`. */
@Serializable(with = Oc2AssistantContentSerializer::class)
public sealed interface Oc2AssistantContent {
    @Serializable
    public data class Text(public val text: String = "", public val state: JsonObject? = null) : Oc2AssistantContent

    @Serializable
    public data class Reasoning(
        public val text: String = "",
        public val state: JsonObject? = null,
        public val time: Oc2ContentTime = Oc2ContentTime(),
    ) : Oc2AssistantContent

    /** A tool call; [id] is the call id, which is also the transcript part id. */
    @Serializable
    public data class Tool(
        public val id: String = "",
        public val name: String = "",
        public val executed: Boolean? = null,
        public val state: Oc2ToolState = Oc2ToolState.Unknown(EmptyObject),
        public val time: Oc2ContentTime = Oc2ContentTime(),
    ) : Oc2AssistantContent

    public data class Unknown(public val raw: JsonObject) : Oc2AssistantContent {
        public val type: String? get() = raw.str("type")
    }

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2AssistantContent = Oc2AssistantContentSerializer.read(obj)
    }
}

internal object Oc2AssistantContentSerializer : RawObjectSerializer<Oc2AssistantContent>("oc2.AssistantContent") {
    override fun read(obj: JsonObject): Oc2AssistantContent =
        when (obj.str("type")) {
            "text" -> Oc2Wire.decode(Oc2AssistantContent.Text.serializer(), obj)
            "reasoning" -> Oc2Wire.decode(Oc2AssistantContent.Reasoning.serializer(), obj)
            "tool" -> Oc2Wire.decode(Oc2AssistantContent.Tool.serializer(), obj)
            else -> null
        } ?: Oc2AssistantContent.Unknown(obj)

    override fun write(value: Oc2AssistantContent): JsonElement =
        when (value) {
            is Oc2AssistantContent.Text -> withDiscriminator(Oc2AssistantContent.Text.serializer(), value, "type", "text")
            is Oc2AssistantContent.Reasoning -> withDiscriminator(Oc2AssistantContent.Reasoning.serializer(), value, "type", "reasoning")
            is Oc2AssistantContent.Tool -> withDiscriminator(Oc2AssistantContent.Tool.serializer(), value, "type", "tool")
            is Oc2AssistantContent.Unknown -> value.raw
        }
}
