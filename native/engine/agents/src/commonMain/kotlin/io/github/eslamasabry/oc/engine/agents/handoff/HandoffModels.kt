package io.github.eslamasabry.oc.engine.agents.handoff

/**
 * The slice of a conversation the handoff reads (Flutter `Session`, `MessageWithParts`, `Part`, `ToolState`, `Todo`,
 * `FileDiff` in lib/domain/models). Only the fields the record writer and history reader use; the gateway adapter maps
 * its own transcript types onto these.
 */
public data class HandoffSession(
    val id: String,
    val title: String? = null,
    val directory: String? = null,
    val path: String? = null,
    val agent: String? = null,
    val model: String? = null,
    /** `time.updated`: the source snapshot fence. */
    val updated: Long? = null,
)

public data class HandoffMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val parts: List<HandoffPart> = emptyList(),
    val errorText: String? = null,
)

/**
 * [toolInput] and [toolMetadata] hold JSON-like values (String, Int/Long, Boolean, List, Map); only allowlisted keys are
 * ever exported.
 */
public data class HandoffPart(
    val type: String,
    val text: String = "",
    val synthetic: Boolean = false,
    val filename: String? = null,
    val url: String? = null,
    val toolName: String? = null,
    val tool: HandoffToolState = HandoffToolState(status = "pending"),
)

public data class HandoffToolState(
    val status: String,
    val input: Map<String, Any?> = emptyMap(),
    val inputJson: String? = null,
    val output: String? = null,
    val outputValue: Any? = null,
    val metadata: Map<String, Any?>? = null,
    val pruned: Boolean = false,
    val executed: Boolean = true,
)

public data class HandoffTodo(val content: String, val status: String, val priority: String? = null) {
    val done: Boolean get() = status == "completed"
}

/** [before], [after] and [patch] are never exported. */
public data class HandoffFileDiff(
    val file: String,
    val additions: Int? = null,
    val deletions: Int? = null,
    val before: String? = null,
    val after: String? = null,
    val patch: String? = null,
    val status: String? = null,
)

/** Flutter `ModelRef`. [normalized] strips a redundant `provider/` prefix like `bareModelID`. */
public data class HandoffModelRef(val providerId: String, val modelId: String) {
    val normalized: HandoffModelRef
        get() {
            val bare = bareModelId(providerId, modelId)
            return if (bare == modelId) this else HandoffModelRef(providerId, bare)
        }
}

public fun bareModelId(providerId: String, modelId: String): String {
    val prefix = "$providerId/"
    if (providerId.isNotEmpty() && modelId.startsWith(prefix)) {
        val rest = modelId.substring(prefix.length)
        if (rest.isNotEmpty()) return rest
    }
    return modelId
}
