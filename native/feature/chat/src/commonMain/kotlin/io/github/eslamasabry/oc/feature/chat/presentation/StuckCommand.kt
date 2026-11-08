package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * S28 story 14 (Flutter `stuck_command.dart`): a foreground command that runs far longer than an agent lets one
 * run (agents cap one near ten minutes) means the turn is hung or this phone lost track of it. The live line then
 * says so with how long it has run; a command sent to the background is never this.
 */
public object StuckCommandRules {
    /** Flutter `stuckCommandAfter`: the agents' ten-minute cap, with room for their own `timeout 900` runs. */
    public const val STUCK_AFTER_MS: Long = 15 * 60 * 1000L

    private const val NAME_MAX = 40
    private val cdPrefix = Regex("^cd\\s+(\"[^\"]*\"|\\S+)\\s*(;|&&)\\s*")

    /** The newest running step, when it is a foreground command past [STUCK_AFTER_MS]; null otherwise. */
    public fun of(work: Map<String, TurnWork>, nowMs: Long): LiveNoticeUi.StuckCommand? {
        val step = work.values.lastOrNull { it.now != null }?.now ?: return null
        if (step.facts.kind != ToolKind.Shell || step.state != StepState.Running) return null
        val input = step.output.input
        val background =
            listOf("run_in_background", "runInBackground").any { key ->
                (input[key] as? JsonPrimitive)?.booleanOrNull == true
            }
        if (background) return null
        val started = step.startedAtMs ?: return null
        val age = nowMs - started
        if (age < STUCK_AFTER_MS) return null
        val raw = (input["command"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return LiveNoticeUi.StuckCommand(raw?.let(::commandName), age)
    }

    /** The command as the line shows it: a leading `cd <path>;` dropped, the first line, cut to 40 characters. */
    public fun commandName(raw: String): String? {
        val first = raw.trim().replaceFirst(cdPrefix, "").lineSequence().first().trim()
        if (first.isEmpty()) return null
        return if (first.length > NAME_MAX) first.take(NAME_MAX - 1) + "…" else first
    }
}
