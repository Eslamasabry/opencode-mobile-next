package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind

/**
 * C6 (Flutter `_joinedTurnPrompt` / `_steerWait`, chat_transcript.dart:350-370, 432-475): a message that joined a turn
 * whose newest step is still running is not read until that step ends. The quiet note says so.
 *
 * [command] true when the step in hand is a shell command ("reads this when the command finishes").
 */
public data class SteerWait(val promptKey: String, val command: Boolean)

public object SteerWaitRules {
    /**
     * [rows] newest first. A steer waits when a run is going, a send now joins the running turn ([joins]), the newest row
     * is a prompt, and an earlier turn still has a step running.
     */
    public fun wait(
        rows: List<TranscriptRow>,
        work: Map<String, TurnWork>,
        busy: Boolean,
        joins: Boolean,
    ): SteerWait? {
        if (!busy || !joins) return null
        val prompt = rows.firstOrNull() as? PromptRow ?: return null
        val earlier = rows.firstOrNull { it.turnId != prompt.turnId } ?: return null
        val step = work[earlier.turnId]?.now ?: return null
        return SteerWait(prompt.key, command = step.facts.kind == ToolKind.Shell)
    }
}
