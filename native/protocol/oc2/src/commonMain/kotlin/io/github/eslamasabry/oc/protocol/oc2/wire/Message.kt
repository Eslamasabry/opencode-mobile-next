package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ported from lib/api2/models/models_content.dart (messages): the 10-variant session message union plus Unknown.

@Serializable
public data class Oc2MessageTime(
    public val created: Long? = null,
    public val streamed: Long? = null,
    public val completed: Long? = null,
)

/** `{output, truncated}` of a shell message. */
@Serializable
public data class Oc2ShellOutput(public val output: String? = null, public val truncated: Boolean? = null)

@Serializable(with = Oc2MessageSerializer::class)
public sealed interface Oc2Message {
    public val id: String
    public val time: Oc2MessageTime
    public val metadata: JsonObject?

    /** The wire `type` (for [Unknown], whatever the server sent, or `"unknown"`). */
    public val type: String

    @Serializable
    public data class User(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val text: String = "",
        public val files: List<Oc2FileAttachment> = emptyList(),
    ) : Oc2Message {
        override val type: String get() = "user"
    }

    @Serializable
    public data class Assistant(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val agent: String? = null,
        public val model: Oc2ModelRef? = null,
        public val content: List<Oc2AssistantContent> = emptyList(),
        public val finish: String? = null,
        public val rawFinish: String? = null,
        public val cost: Double? = null,
        public val tokens: Oc2Tokens? = null,
        public val error: Oc2StructuredError? = null,
    ) : Oc2Message {
        override val type: String get() = "assistant"
        public val completed: Boolean get() = time.completed != null

        /** Non-empty text items joined with newlines. */
        public val text: String
            get() = content.filterIsInstance<Oc2AssistantContent.Text>().map { it.text }.filter { it.isNotEmpty() }
                .joinToString("\n")
    }

    @Serializable
    public data class Synthetic(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val text: String = "",
        public val description: String? = null,
    ) : Oc2Message {
        override val type: String get() = "synthetic"
    }

    @Serializable
    public data class System(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val text: String = "",
        public val description: String? = null,
    ) : Oc2Message {
        override val type: String get() = "system"
    }

    @Serializable
    public data class Skill(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val skill: String = "",
        public val name: String = "",
        public val text: String = "",
    ) : Oc2Message {
        override val type: String get() = "skill"
    }

    @Serializable
    public data class Shell(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val shellID: String? = null,
        public val command: String = "",
        public val status: String = "",
        public val exit: Int? = null,
        public val output: Oc2ShellOutput? = null,
    ) : Oc2Message {
        override val type: String get() = "shell"
        public val outputText: String? get() = output?.output
        public val outputTruncated: Boolean get() = output?.truncated ?: false
    }

    @Serializable
    public data class AgentSwitched(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val agent: String = "",
        public val previous: String? = null,
    ) : Oc2Message {
        override val type: String get() = "agent-switched"
    }

    @Serializable
    public data class ModelSwitched(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val model: Oc2ModelRef? = null,
        public val previous: Oc2ModelRef? = null,
    ) : Oc2Message {
        override val type: String get() = "model-switched"
    }

    @Serializable
    public data class LocationSwitched(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val location: Oc2Location? = null,
        public val projectID: String? = null,
        public val subpath: String? = null,
    ) : Oc2Message {
        override val type: String get() = "location-switched"
    }

    @Serializable
    public data class Compaction(
        override val id: String,
        override val time: Oc2MessageTime = Oc2MessageTime(),
        override val metadata: JsonObject? = null,
        public val status: String = "",
        public val reason: String? = null,
        public val summary: String? = null,
        public val error: Oc2StructuredError? = null,
    ) : Oc2Message {
        override val type: String get() = "compaction"
    }

    /** A message type this client does not know; [raw] keeps everything so the mapper can show a quiet notice. */
    public data class Unknown(
        override val id: String,
        override val time: Oc2MessageTime,
        override val metadata: JsonObject?,
        override val type: String,
        public val raw: JsonObject,
    ) : Oc2Message

    public companion object {
        private val variants: Map<String, KSerializer<out Oc2Message>> =
            mapOf(
                "user" to User.serializer(),
                "assistant" to Assistant.serializer(),
                "synthetic" to Synthetic.serializer(),
                "system" to System.serializer(),
                "skill" to Skill.serializer(),
                "shell" to Shell.serializer(),
                "agent-switched" to AgentSwitched.serializer(),
                "model-switched" to ModelSwitched.serializer(),
                "location-switched" to LocationSwitched.serializer(),
                "compaction" to Compaction.serializer(),
            )

        /** The wire `type` values with a typed variant. */
        public val knownTypes: Set<String> get() = variants.keys

        /** Null without a string `id`; an unknown `type` gives [Unknown]. */
        public fun fromJson(obj: JsonObject): Oc2Message? {
            val id = obj.str("id") ?: return null
            val type = obj.str("type")
            return variants[type]?.let { Oc2Wire.decode(it, obj) }
                ?: Unknown(
                    id = id,
                    time = Oc2Wire.decode(Oc2MessageTime.serializer(), obj["time"]) ?: Oc2MessageTime(),
                    metadata = obj.obj("metadata"),
                    type = type ?: "unknown",
                    raw = obj,
                )
        }

        @Suppress("UNCHECKED_CAST")
        internal fun toJson(message: Oc2Message): JsonElement {
            if (message is Unknown) return message.raw
            val serializer = variants.getValue(message.type) as KSerializer<Oc2Message>
            val body = Oc2Wire.json.encodeToJsonElement(serializer, message) as? JsonObject ?: EmptyObject
            return JsonObject(body + ("type" to JsonPrimitive(message.type)))
        }
    }
}

internal object Oc2MessageSerializer : RawObjectSerializer<Oc2Message>("Message") {
    override fun read(obj: JsonObject): Oc2Message = Oc2Message.fromJson(obj) ?: throw SerializationException("message without id")

    override fun write(value: Oc2Message): JsonElement = Oc2Message.toJson(value)
}
