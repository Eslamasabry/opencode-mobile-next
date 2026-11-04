package io.github.eslamasabry.oc.engine.setupassist

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of lib/domain/setup_assistant.dart: the transport-neutral setup assistant contract.

/** Independent of transport flavor. False means the UI must show [reason]. */
public data class SetupSupport(
    val readConfig: Boolean = false,
    /** Requires the atomic transaction facet. Production adapters keep this false. */
    val writeConfig: Boolean = false,
    val mcpInventory: Boolean = false,
    /** A server-enforced, proposal-only assistant, not ordinary chat access. */
    val assistant: Boolean = false,
    val reason: String = "This server does not expose setup configuration APIs.",
)

public enum class SetupFailureCode { Offline, Unsupported, NeedsSignIn, Invalid, Conflict, Storage, Transport, Busy, Uncertain }

/** An expected setup failure. [message] is adapter/controller-authored copy only; never a response body. */
public data class SetupFailure(val code: SetupFailureCode, val message: String) {
    override fun toString(): String = "SetupFailure(${code.name})"
}

/** The value every setup command returns (expected failures are values, native AGENTS rule 5). */
public sealed interface SetupOutcome<out T> {
    public data class Ok<out T>(val value: T) : SetupOutcome<T>

    public data class Failed(val failure: SetupFailure) : SetupOutcome<Nothing>
}

public fun <T> SetupOutcome<T>.valueOrNull(): T? = (this as? SetupOutcome.Ok)?.value

public fun SetupOutcome<*>.failureOrNull(): SetupFailure? = (this as? SetupOutcome.Failed)?.failure

/** Raw transport boundary, for controllers only; UI uses [SetupController]. Never persist, log or show raw config. */
public interface SetupConfigGateway {
    public val support: SetupSupport

    public suspend fun readConfig(): SetupOutcome<JsonObject>

    public suspend fun patchConfig(patch: JsonObject): SetupOutcome<Unit>

    public suspend fun listMcpServers(): SetupOutcome<List<SetupMcpStatus>>
}

/**
 * Optional host transaction contract. Never emulate this with GET plus PATCH. The host atomically compares `expected`
 * before committing all edits; Conflict means no write occurred. Restore consumes a host-owned handle.
 */
public interface SetupTransactionalConfigGateway : SetupConfigGateway {
    public suspend fun readSnapshot(): SetupOutcome<SetupConfigRevision>

    public suspend fun commit(expected: SetupConfigRevision, edits: List<SetupEdit>, operationId: String): SetupOutcome<SetupConfigCommit>

    public suspend fun restore(commit: SetupConfigCommit, operationId: String): SetupOutcome<SetupConfigCommit>
}

/** Raw controller-only source snapshot. Never expose to UI or persistence. */
public data class SetupConfigRevision(val targetId: String, val revision: String, val config: JsonObject)

/** Raw memory-only receipt. The opaque restore handle remains on this boundary. */
public data class SetupConfigCommit(val before: SetupConfigRevision, val after: SetupConfigRevision, val undoHandle: String)

/** connected/pending/disabled/failed/needs_auth/unknown. No error bodies. */
public data class SetupMcpStatus(val name: String, val status: String)

/** Input only. Never render this; render [SetupDiff]. A null [value] is Dart's null. */
public data class SetupEdit(val path: List<String>, val value: JsonElement? = null, val remove: Boolean = false)

/** A redacted, UI-safe change line. Build with [SetupDiff.of]. */
public data class SetupDiff(
    val path: List<String>,
    val before: JsonElement?,
    val after: JsonElement?,
    val remove: Boolean,
    val beforePresent: Boolean = true,
    val afterPresent: Boolean = true,
) {
    public companion object {
        public fun of(
            redactor: Redactor,
            path: List<String>,
            before: JsonElement?,
            after: JsonElement?,
            remove: Boolean,
            beforePresent: Boolean = true,
            afterPresent: Boolean = true,
        ): SetupDiff =
            SetupDiff(
                path = path.map(redactor::redact),
                before = setupRedact(redactor, before),
                after = setupRedact(redactor, after),
                remove = remove,
                beforePresent = beforePresent,
                afterPresent = afterPresent,
            )
    }
}

public data class SetupProposal(val id: String, val changes: List<SetupDiff>, val canApply: Boolean, val reason: String?) {
    /** Any change may affect future sessions; never claim an automatic restart. */
    val effect: String get() = "Runtime activation is not verified. Recheck server status after a change."
}

public enum class SetupPhase {
    Idle, Loading, Applying, Verifying, Undoing, Uncertain, Ready, Empty, Offline, Unsupported, NeedsSignIn, Error,
}

public data class SetupSnapshot(
    val phase: SetupPhase = SetupPhase.Idle,
    val config: JsonObject = JsonObject(emptyMap()),
    val servers: List<SetupMcpStatus> = emptyList(),
    val proposal: SetupProposal? = null,
    val reason: String? = null,
    val canUndo: Boolean = false,
)

private val SENSITIVE_KEY =
    Regex(
        "key|token|secret|password|credential|environment|headers|options|request|command|template|prompt|system|content",
        RegexOption.IGNORE_CASE,
    )

/**
 * Structural redaction complementing the [Redactor]: arbitrary env/header values, provider credentials and free-form
 * executable/prompt bodies are hidden even when they do not resemble a known credential.
 */
public fun setupRedact(redactor: Redactor, value: JsonElement?, key: String = ""): JsonElement? {
    if (SENSITIVE_KEY.containsMatchIn(key)) return JsonPrimitive(Redactor.MASK)
    return when (value) {
        null -> null
        is JsonNull -> value
        is JsonObject -> JsonObject(value.entries.associate { (k, v) -> redactor.redact(k) to (setupRedact(redactor, v, k) ?: JsonNull) })
        is JsonArray -> JsonArray(value.map { setupRedact(redactor, it, key) ?: JsonNull })
        is JsonPrimitive -> if (value.isString) JsonPrimitive(redactor.redact(value.content)) else value
    }
}

/** The redacted form of a whole config object. */
public fun setupRedactObject(redactor: Redactor, value: JsonObject?): JsonObject =
    (setupRedact(redactor, value ?: JsonObject(emptyMap())) as? JsonObject) ?: JsonObject(emptyMap())
