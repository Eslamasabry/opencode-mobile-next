package io.github.eslamasabry.oc.feature.chat.ui

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
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
 * not started, no times from the server, under [STEP_DURATION_MIN_MS], or stopped (P-102: "Stopped 2m 13s" read as
 * two minutes of work; the time was only how long it waited before the stop).
 */
internal fun shownStepDurationMs(
    state: StepState,
    durationMs: Long?,
    startedAtMs: Long?,
    nowMs: Long,
): Long? {
    if (state == StepState.Stopped || state == StepState.NotRun) return null
    val runningFor =
        if (state == StepState.Running) startedAtMs?.let { (nowMs - it).coerceAtLeast(0) } else null
    return (durationMs ?: runningFor)?.takeIf { it >= STEP_DURATION_MIN_MS }
}

/**
 * P-105: a step still open while its turn waits for the person (an approval, a question) is blocked on them, not
 * running: its row shows the attention mark and "Waiting for you", never a live dot and a ticking clock.
 */
internal fun stepWaitsForYou(state: StepState, work: WorkLineState): Boolean =
    work == WorkLineState.WaitingForYou && (state == StepState.Running || state == StepState.Pending)

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

/**
 * S28 story 6 (Flutter `tool_card_body.dart` `_shellRerun`): the command a finished shell step offers to run again
 * ("Run this command again" opens the Run shell command dialog with it, to read or change first). Null while the
 * step is pending or running, for a step that never ran, and for anything but a shell step.
 */
internal fun rerunCommand(kind: ToolKind, state: StepState, input: JsonObject, subject: String?): String? {
    if (kind != ToolKind.Shell) return null
    if (state != StepState.Done && state != StepState.Failed) return null
    val command = (input["command"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: subject
    return command?.takeIf { it.isNotBlank() }
}
