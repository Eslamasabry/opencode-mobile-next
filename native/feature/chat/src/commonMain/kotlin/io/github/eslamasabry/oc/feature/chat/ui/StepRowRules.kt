package io.github.eslamasabry.oc.feature.chat.ui

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * E20-S14: a step that took less than this shows no time. "0.0s" on a Read or a Write said nothing; under a tenth of a
 * second there is nothing worth saying.
 */
internal const val STEP_DURATION_MIN_MS: Long = 100

/**
 * How long the step row says the step took (or, while it runs, has been running), or null when it says nothing:
 * not started, no times from the server, or under [STEP_DURATION_MIN_MS].
 */
internal fun shownStepDurationMs(
    state: StepState,
    durationMs: Long?,
    startedAtMs: Long?,
    nowMs: Long,
): Long? {
    val runningFor =
        if (state == StepState.Running) startedAtMs?.let { (nowMs - it).coerceAtLeast(0) } else null
    return (durationMs ?: runningFor)?.takeIf { it >= STEP_DURATION_MIN_MS }
}

/** The kinds whose row names a file; their waiting row names it as soon as the streamed input has the path. */
private val FILE_KINDS = setOf(ToolKind.Read, ToolKind.Edit, ToolKind.Write)

private val PATH_KEYS = listOf("filePath", "file_path", "path")

/**
 * E20-S14: what a file step's row names. The engine's [subject] when it has one; otherwise, while the call waits, the
 * file name from its input — the structured input, or the raw input the server streams before it is complete, read
 * only once the path's own string has closed (a half-sent path is never shown).
 */
internal fun stepRowSubject(
    kind: ToolKind,
    state: StepState,
    subject: String?,
    input: JsonObject,
    rawInput: String,
): String? {
    if (subject != null) return subject
    if (kind !in FILE_KINDS) return null
    if (state != StepState.Pending && state != StepState.Running) return null
    val path =
        PATH_KEYS.firstNotNullOfOrNull { key ->
            (input[key] as? JsonPrimitive)?.takeIf { it != JsonNull && it.isString }?.content?.trim()?.ifEmpty { null }
        } ?: rawPath(rawInput)
    return path?.let(::fileName)
}

/** The first complete `"filePath": "…"` (or `file_path`, `path`) string in partial JSON; null until it has closed. */
private fun rawPath(raw: String): String? {
    if (raw.isBlank()) return null
    for (key in PATH_KEYS) {
        val match = Regex("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(raw) ?: continue
        val text =
            try {
                Json.parseToJsonElement("\"${match.groupValues[1]}\"").jsonPrimitive.content
            } catch (_: IllegalArgumentException) {
                null
            }
        text?.trim()?.ifEmpty { null }?.let { return it }
    }
    return null
}

private fun fileName(path: String): String {
    val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    return if (parts.isEmpty()) path else parts.last()
}
