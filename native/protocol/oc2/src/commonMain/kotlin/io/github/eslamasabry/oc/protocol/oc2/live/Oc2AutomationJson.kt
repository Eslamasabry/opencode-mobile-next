package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.model.transcript.MessageAutomation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.wire.obj
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private const val SECONDS_EPOCH_CUTOFF = 10_000_000_000L
private const val MILLIS_PER_SECOND = 1_000L

internal fun parseAutomation(obj: JsonObject, redactor: Redactor): ServerAutomation? {
    val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return null
    val name = obj.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: id
    val lastOutput = obj.str("lastOutput")?.takeIf { it.isNotBlank() }?.let(redactor::details)
    val pauseReason =
        (obj.str("pauseReason") ?: obj.str("reason"))
            ?.takeIf { it.isNotBlank() }
            ?.let(redactor::details)
    return ServerAutomation(
        id = id,
        name = name,
        status = parseStatus(obj.str("status")),
        trigger = parseTrigger(obj.obj("trigger")),
        action = parseAction(obj.obj("action")),
        nextDueAtMs = parseTimestampMs(obj["nextDueAt"]),
        runCount = (obj["runCount"] as? JsonPrimitive)?.intOrNull ?: 0,
        lastRunAtMs = parseTimestampMs(obj["lastRunAt"]),
        lastOutcome = obj.str("lastOutcome")?.let(::parseOutcome),
        lastOutput = lastOutput,
        pauseReason = pauseReason,
        expiresAtMs = parseTimestampMs(obj["expiresAt"] ?: obj["expires"]),
        sessionId = obj.str("sessionID") ?: obj.str("sessionId"),
        directory = obj.str("directory"),
        projectId = obj.str("projectID") ?: obj.str("projectId"),
    )
}

internal fun parseAutomationMetadata(metadata: JsonObject?): MessageAutomation? {
    val auto = metadata?.obj("automation")
    val name = auto?.str("name")?.trim()?.takeIf { it.isNotEmpty() }
    return name?.let { validName ->
        MessageAutomation(
            id = auto.str("id")?.takeIf { it.isNotBlank() },
            name = validName,
            run = (auto["run"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
            late = (auto["late"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
}

internal fun extractAutomationArray(element: JsonElement): List<JsonElement>? {
    val unwrapped = (element as? JsonObject)?.get("data") ?: element
    return when (unwrapped) {
        is JsonArray -> unwrapped.toList()
        is JsonObject -> (unwrapped["items"] as? JsonArray)?.toList()
        else -> null
    }
}

internal fun extractAutomationObject(element: JsonElement): JsonObject? {
    val unwrapped = (element as? JsonObject)?.get("data") ?: element
    return unwrapped as? JsonObject
}

private fun parseStatus(raw: String?): AutomationStatus =
    when (raw?.lowercase()) {
        "active" -> AutomationStatus.Active
        "paused" -> AutomationStatus.Paused
        "done", "completed", "finished" -> AutomationStatus.Done
        else -> AutomationStatus.Unknown
    }

private fun parseOutcome(raw: String): AutomationOutcome =
    when (raw.lowercase()) {
        "fired", "ok", "succeeded" -> AutomationOutcome.Fired
        "late" -> AutomationOutcome.Late
        "skipped" -> AutomationOutcome.Skipped
        "disabled" -> AutomationOutcome.Disabled
        "failed", "error" -> AutomationOutcome.Failed
        "expired" -> AutomationOutcome.Expired
        else -> AutomationOutcome.Unknown
    }

private fun parseTrigger(obj: JsonObject?): AutomationTrigger {
    if (obj == null) return AutomationTrigger.Unknown
    return when (obj.str("type")?.lowercase()) {
        "at" -> AutomationTrigger.At(parseTimestampMs(obj["at"] ?: obj["time"]))
        "every" ->
            AutomationTrigger.Every(
                obj.str("every") ?: obj.str("interval") ?: obj.str("duration") ?: ""
            )
        "cron" -> AutomationTrigger.Cron(obj.str("cron") ?: obj.str("expression") ?: "")
        "event" -> AutomationTrigger.Event(obj.str("source"), obj.str("kind") ?: obj.str("event"))
        else -> AutomationTrigger.Unknown
    }
}

private fun parseAction(obj: JsonObject?): AutomationAction {
    if (obj == null) return AutomationAction.Unknown
    return when (obj.str("type")?.lowercase()) {
        "prompt" ->
            AutomationAction.Prompt(
                text = obj.str("prompt") ?: obj.str("text") ?: "",
                agent = obj.str("agent")?.takeIf { it.isNotBlank() },
            )
        "shell" -> AutomationAction.Shell(command = obj.str("command") ?: obj.str("shell") ?: "")
        "code" -> AutomationAction.Code(source = obj.str("code") ?: obj.str("source") ?: "")
        else -> AutomationAction.Unknown
    }
}

private fun parseTimestampMs(element: JsonElement?): Long? {
    val primitive = element as? JsonPrimitive
    val numeric = primitive?.longOrNull
    val raw = primitive?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    val scale: (Long) -> Long = { num ->
        if (num in 1 until SECONDS_EPOCH_CUTOFF) num * MILLIS_PER_SECOND else num
    }
    return when {
        numeric != null -> scale(numeric)
        raw == null -> null
        else ->
            raw.toLongOrNull()?.let(scale)
                ?: try {
                    Instant.parse(raw).toEpochMilliseconds()
                } catch (_: IllegalArgumentException) {
                    null
                }
    }
}
