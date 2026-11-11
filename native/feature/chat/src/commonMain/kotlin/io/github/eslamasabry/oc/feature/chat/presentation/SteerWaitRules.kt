package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind

/**
 * C6 (Flutter `_joinedTurnPrompt` / `_steerWait`, chat_transcript.dart:350-370, 432-475): a message that joined a turn
 * whose newest step is still running is not read until that step ends. The quiet note says so.
 *
 * [command] true when the step in hand is a shell command ("reads this when the command finishes").
 */
public data class SteerWait(
    val promptKey: String,
    val command: Boolean,
    /** The words the agent has not read yet (what Stop-then-resend sends again). */
    val text: String = "",
    /** When the message was sent, if the transcript knows (the Stop button waits for a long step). */
    val sentMs: Long? = null,
)

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
        return SteerWait(prompt.key, command = step.facts.kind == ToolKind.Shell, text = prompt.text, sentMs = prompt.createdMs)
    }

    /** Flutter `KitMotion.escalateAfter`: Stop appears on the note once the wait has lasted this long. */
    public const val STOP_AFTER_MS: Long = 8_000

    /** Stop shows beside the note for a step that runs long; without a send time it shows at once (Flutter `noteSince`). */
    public fun showsStop(wait: SteerWait, nowMs: Long): Boolean =
        wait.sentMs == null || nowMs - wait.sentMs >= STOP_AFTER_MS

    /**
     * After the turn stopped: the words go out again only if the agent never started on them, that is, no answer row
     * sits newer than the message [promptKey] ([rows] newest first). Otherwise it was read in the last moment.
     */
    public fun unreadAfterStop(rows: List<TranscriptRow>, promptKey: String): Boolean {
        val at = rows.indexOfFirst { it.key == promptKey }
        if (at < 0) return false
        // A stopped or footer line is not an answer; any text, thought or step row after the message is.
        return rows.take(at).all {
            it.kind == RowKind.Prompt || it.kind == RowKind.Notice || it.kind == RowKind.Footer
        }
    }
}
