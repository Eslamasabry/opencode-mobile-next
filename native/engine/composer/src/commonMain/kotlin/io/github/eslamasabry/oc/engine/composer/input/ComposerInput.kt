package io.github.eslamasabry.oc.engine.composer.input

import io.github.eslamasabry.oc.core.model.MidTurnPrompt

/** A `/name args` the composer holds (plan 09 §10.7). */
public data class TypedCommand(val name: String, val arguments: String)

/** What the composer text is, for Send and for the suggestion strip. */
public object ComposerInput {
    private val SLASH_QUERY = Regex("^/(\\S*)$")
    private val COMMAND = Regex("^/(\\S+)(?:\\s+([\\s\\S]*))?$")
    private val SPACE = Regex("\\s")

    /** The word the caret ends starts with `/` ("/", "/rev", "fix this /"); without a caret, the text's start counts. */
    public fun slashWordAtCaret(text: String, caret: Int?): Boolean {
        if (caret == null) return text.trimStart().startsWith('/')
        if (caret < 1 || caret > text.length) return false
        val before = text.substring(0, caret)
        val start = before.indexOfLast { SPACE.matches(it.toString()) } + 1
        return before.substring(start).startsWith('/')
    }

    /** The `/` list's filter while the whole text is one slash word, else null. */
    public fun slashQuery(text: String): String? = SLASH_QUERY.find(text.trimStart())?.groupValues?.get(1)

    /** The `@` list's filter: the word the caret ends, when it starts with `@` at a word boundary. */
    public fun mentionQueryAtCaret(text: String, caret: Int): String? {
        if (caret < 1 || caret > text.length) return null
        val before = text.substring(0, caret)
        val start = before.indexOfLast { SPACE.matches(it.toString()) } + 1
        val word = before.substring(start)
        return if (word.startsWith('@')) word.substring(1) else null
    }

    /** `/name args` when the whole (trimmed) text is a command. */
    public fun typedCommand(text: String): TypedCommand? {
        val match = COMMAND.find(text.trim()) ?: return null
        return TypedCommand(match.groupValues[1], match.groupValues[2].trim())
    }

    /** `!command` runs in the conversation's shell; null when the text is not one. */
    public fun shellCommand(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith('!')) return null
        return trimmed.substring(1).trim().takeIf { it.isNotEmpty() }
    }
}

/** Everything the Send decision reads (plan 09 §10.2, §4.3). Capabilities arrive as plain flags. */
public data class SendInputs(
    val text: String,
    val attachments: Int = 0,
    val references: Int = 0,
    val sending: Boolean = false,
    val shelfBusy: Boolean = false,
    val readOnly: Boolean = false,
    val offline: Boolean = false,
    /** A turn is running in this conversation. */
    val busy: Boolean = false,
    /** `capabilities.promptSteering`. */
    val steering: Boolean = false,
    /** What a prompt sent mid-turn does where steering does not exist; null: unknown, promise nothing. */
    val midTurn: MidTurnPrompt? = null,
    /** The app's and the server's command names (`slashCommands`). */
    val commands: Set<String> = emptySet(),
    val shell: Boolean = false,
)

public enum class SendBlock { ReadOnly, Sending, ShelfBusy, BusyCannotSendYet, UnknownCommand, NeedsConnection, NoShell }

/** What Send does now. Never a queue while a turn runs where steering exists (owner rule, B-CHAT steer table). */
public sealed interface SendAction {
    /** Nothing to send: the field shows the mic. */
    public data object Empty : SendAction

    public data object Send : SendAction

    /** Joins the running turn. */
    public data object Steer : SendAction

    /** A turn runs and this server joins or restarts it with a new prompt (no steering). */
    public data class SendMidTurn(val effect: MidTurnPrompt) : SendAction

    /** Offline: "Send when back online" (the offline queue is `:engine:delivery`'s). */
    public data object SendWhenOnline : SendAction

    public data class Command(val command: TypedCommand) : SendAction

    public data class Shell(val command: String) : SendAction

    public data class Blocked(val reason: SendBlock) : SendAction
}

public object SendDecision {
    public fun decide(input: SendInputs): SendAction {
        if (input.readOnly) return SendAction.Blocked(SendBlock.ReadOnly)
        val hasPrompt = input.text.isNotBlank() || input.attachments > 0 || input.references > 0
        if (!hasPrompt) return SendAction.Empty
        if (input.sending) return SendAction.Blocked(SendBlock.Sending)
        if (input.shelfBusy) return SendAction.Blocked(SendBlock.ShelfBusy)
        if (input.attachments == 0) {
            ComposerInput.typedCommand(input.text)?.let { command ->
                if (command.name !in input.commands) {
                    // A `/word` this agent cannot run sends nothing and says so.
                    return SendAction.Blocked(SendBlock.UnknownCommand)
                }
                return if (input.offline) SendAction.Blocked(SendBlock.NeedsConnection) else SendAction.Command(command)
            }
            ComposerInput.shellCommand(input.text)?.let { command ->
                if (!input.shell) return SendAction.Blocked(SendBlock.NoShell)
                return if (input.offline) SendAction.Blocked(SendBlock.NeedsConnection) else SendAction.Shell(command)
            }
        }
        if (input.offline) return SendAction.SendWhenOnline
        if (!input.busy) return SendAction.Send
        if (input.steering) return SendAction.Steer
        return input.midTurn?.let { SendAction.SendMidTurn(it) } ?: SendAction.Blocked(SendBlock.BusyCannotSendYet)
    }
}
