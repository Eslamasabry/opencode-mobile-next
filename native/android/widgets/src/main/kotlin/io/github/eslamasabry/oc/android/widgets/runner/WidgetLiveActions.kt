package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the live connection knows right now (Flutter `ConnectionController` state behind `_handleWidgetAction`).
 * Production reads the live graph of the tapped profile: its request engine, its feed and its gateway.
 */
public interface WidgetLiveWorld {
    /** Connected, not suspended, and [tap]'s profile and scope are the live ones (a location switch changes it). */
    public fun isCurrent(tap: WidgetTap): Boolean

    /** The exact pending permission still waiting for an answer, or null. */
    public fun pendingPermission(requestId: String): WidgetPendingAsk?

    /** When the conversation's current run started (epoch ms), 0 when it is not running. */
    public fun startedAt(session: String): Long

    /**
     * How a mid-turn message reaches [session]: `steer` (native steering, or a turn that joins), `afterReply` (an
     * ordinary send the server holds until the reply ends), `restart` or `unknown` (never sent).
     */
    public fun steerMode(session: String): String

    /** ServerCapabilities.promptSteering of the live gateway. */
    public val promptSteering: Boolean

    public suspend fun answer(session: String, request: String, allowOnce: Boolean): Outcome<Unit>

    public suspend fun stop(session: String): Outcome<Unit>

    /** One send; [delivery] is Steer where the server supports it, else null (after-reply). Never resent. */
    public suspend fun send(session: String, text: String, delivery: PromptDelivery?): Outcome<Unit>
}

/**
 * A widget tap handled by the live connection (port of Flutter `handleNativeWidgetAction` + `_handleWidgetAction`;
 * B-ANDR-121, B-AGENT-317, B-SEC-183). The same tap id is answered once and its result is remembered (32 at most); a
 * conversation with an action in flight answers `busy`; a stale profile, scope, run or request never reaches the
 * server; allow once answers only the exact pending permission; steering uses steer delivery where the server
 * supports it, else an ordinary after-reply send, and never sends when the mid-turn behaviour is unknown or restarts
 * the turn. A failed or thrown mutation is `uncertain/uncertainDelivery`: no server words, never replayed.
 */
public class WidgetLiveActions(private val world: WidgetLiveWorld) {
    private val lock = Any()
    private val results = LinkedHashMap<String, WidgetRunResult>()
    private val inFlight = mutableSetOf<String>()

    public suspend fun handle(payload: Map<*, *>?): Map<String, Any?> {
        val tap =
            WidgetTap.from(payload)
                ?: return mapOf("id" to "", "action" to "", "status" to "rejected", "error" to "invalidRequest")
        return handle(tap).toMap()
    }

    public suspend fun handle(tap: WidgetTap): WidgetRunResult {
        fun refuse(error: String) = WidgetRunResult(tap.id, tap.action, "rejected", error)
        if (!world.isCurrent(tap)) return refuse("staleRequest")
        val target = if (tap.action == "voice") "voice" else tap.runId.ifEmpty { tap.sessionId }
        synchronized(lock) {
            results[tap.id]?.let { return it }
            if (!inFlight.add(target)) return refuse("busy")
            results[tap.id] = WidgetRunResult(tap.id, tap.action, "uncertain", "uncertainDelivery")
        }
        var result = WidgetRunResult(tap.id, tap.action, "uncertain", "uncertainDelivery")
        try {
            result = run(tap, ::refuse) ?: result
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed await cannot prove a mutation was not delivered: never expose a server error, retry, or
            // turn an uncertain steer into a new turn.
        } finally {
            synchronized(lock) { inFlight.remove(target) }
        }
        synchronized(lock) {
            if (world.isCurrent(tap)) {
                results[tap.id] = result
                while (results.size > 32) results.remove(results.keys.first())
            }
        }
        return result
    }

    /** Null keeps the uncertain result. */
    private suspend fun run(tap: WidgetTap, refuse: (String) -> WidgetRunResult): WidgetRunResult? {
        val accepted = WidgetRunResult(tap.id, tap.action, "accepted")
        return when (tap.action) {
            "allowOnce", "reject" -> {
                val pending = world.pendingPermission(tap.requestId)
                if (tap.requestId.isEmpty() || pending == null || pending.sessionId != tap.sessionId)
                    return refuse("staleRequest")
                if (world.answer(tap.sessionId, tap.requestId, tap.action == "allowOnce") is Outcome.Ok) accepted
                else null
            }
            "stop", "steer" -> {
                if (tap.expectedStartedAt <= 0 || tap.expectedStartedAt != world.startedAt(tap.sessionId))
                    return refuse("staleRequest")
                if (tap.action == "stop") {
                    if (world.stop(tap.sessionId) is Outcome.Ok) accepted else null
                } else {
                    val mode = world.steerMode(tap.sessionId)
                    if (mode == "unknown" || mode == "restart") return refuse("unsupported")
                    if (tap.text.isEmpty()) return refuse("invalidRequest")
                    val delivery = if (world.promptSteering) PromptDelivery.Steer else null
                    if (world.send(tap.sessionId, tap.text, delivery) is Outcome.Ok) accepted else null
                }
            }
            // Voice (new conversation) and AI Team actions are not handled by the native live path yet.
            else -> refuse("unsupported")
        }
    }
}

/**
 * The tool and the file or command of recent tool calls by call id, so a request that names only its call ("outside
 * this project") can say what the call does where its conversation is not open (Flutter `_noteToolCall` /
 * `permissionToolCall`). At most 64, oldest dropped first.
 */
public class WidgetToolCalls {
    public data class Call(val tool: String, val target: String?)

    private val calls = LinkedHashMap<String, Call>()

    public fun note(part: Part) {
        val callId = part.callId.orEmpty()
        val tool = part.toolName.orEmpty()
        val input = part.tool?.input
        if (part.type != PartType.Tool || callId.isEmpty() || tool.isEmpty() || input == null) return
        var target =
            TARGET_KEYS.firstNotNullOfOrNull { key ->
                (input[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            }
        synchronized(calls) {
            target = target ?: calls[callId]?.target
            calls.remove(callId)
            calls[callId] = Call(tool, target)
            while (calls.size > 64) calls.remove(calls.keys.first())
        }
    }

    public fun of(request: PermissionRequest): Call? {
        val callId = request.tool?.callId
        return if (callId.isNullOrEmpty()) null else synchronized(calls) { calls[callId] }
    }

    private companion object {
        val TARGET_KEYS = listOf("filePath", "file_path", "path", "command")
    }
}
