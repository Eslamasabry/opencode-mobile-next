package io.github.eslamasabry.oc.protocol.paseo.mappers

import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Issue 147, "it disconnected between the terminal commands". Recorded with `agy acp` 1.5.9 over stdio (2026-11-15):
 * Antigravity runs a command itself (it never asks the client for a terminal), waits at most 10 s for it
 * (`WaitMsBeforeAsync`, 500..10000), then moves it to the background and ends the ACP turn (`end_turn`) with the step
 * still `in_progress` and words like "I have started `sleep 20 && uname -a` in the background and will report the output
 * once it finishes". Its own wake-up runs when the command ends, but over ACP nothing reaches the client until the next
 * `session/prompt`: only then does it send the step's `completed` update and what the wake-up wrote. No option, mode
 * or client capability changes that. A `/goal` prompt does: the turn stays open across the wake-ups until the agent
 * says the goal is done (its words end with `<!-- GOAL_COMPLETE -->`).
 *
 * So, for such an agent, a step still running when its turn ended is shown running (never "Ran"), and the app sends
 * one goal prompt per command left running ([PASEO_CONTINUE_PROMPT]) that keeps the agent working until it is done;
 * that prompt reads as a quiet line ([PASEO_CONTINUE_NOTICE]), never as the person's words.
 */

/** The helper's provider ids of the agents whose turn can end with a command of theirs still running. */
public val PASEO_TURN_OUTLIVING_PROVIDERS: Set<String> = setOf("antigravity")

/** What the app sends when the agent's turn ended with a command of its still running (sent to the agent, not shown). */
public const val PASEO_CONTINUE_PROMPT: String =
    "/goal Wait for the commands you started in the background to finish, then carry on with my last request " +
        "and tell me the result."

/** The notice kind the continuation prompt reads as in the transcript (the chat words it). */
public const val PASEO_CONTINUE_NOTICE: String = "app-continue"

/** At most this many automatic continuations follow one request of the person. */
public const val PASEO_CONTINUE_MAX: Int = 3

private val CONTINUE_BODY = PASEO_CONTINUE_PROMPT.removePrefix("/goal").trim()

/** Whether [text] is the app's continuation, as sent or as the agent echoes it (without its `/goal`). */
public fun paseoIsContinuePrompt(text: String): Boolean = text.trim().removePrefix("/goal").trim() == CONTINUE_BODY

private val GOAL_MARKER = Regex("""<!--\s*GOAL_COMPLETE\s*-->""")

/** [text] without the agent's own end-of-goal marker (bookkeeping for the agent, never words for the person). */
public fun paseoWithoutGoalMarker(text: String): String = if ("GOAL_COMPLETE" in text) text.replace(GOAL_MARKER, "") else text

/**
 * A command step of [provider] that is still running carries `background: true`, so a turn that ends with it does not
 * read as finished ("Ran 1 command") while the command goes on; its own `completed` update ends it.
 */
internal fun withTurnOutliving(meta: JsonObject?, provider: String, toolName: String, status: ToolStatus): JsonObject? {
    if (provider !in PASEO_TURN_OUTLIVING_PROVIDERS || toolName != "bash" || status != ToolStatus.Running) return meta
    return JsonObject(LinkedHashMap<String, JsonElement>().apply {
        meta?.let(::putAll)
        put("background", JsonPrimitive(true))
    })
}

/** The call ids of the command steps among [tools] (Paseo tool items as merged so far) that are still running. */
public fun paseoLeftRunningCommands(tools: Collection<JsonObject>): List<String> =
    tools.filter { item ->
        item["detail"].objectOrNull()?.get("type").stringOrNull() == "shell" &&
            !paseoInterruptedToolCall(item) &&
            paseoToolStatus(item["status"]) == ToolStatus.Running
    }.mapNotNull { it["callId"].stringOrNull()?.takeIf(String::isNotEmpty) }
