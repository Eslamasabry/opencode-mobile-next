package io.github.eslamasabry.oc.feature.chat.starprompt

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow

/** What the ask reports, in words only: no conversation, no text, no ids. */
public enum class StarPromptEvent { Shown, Tapped, Dismissed }

/**
 * The one-time "star us on GitHub" ask, app-wide (not per conversation). The chat reports when its own prompt starts a
 * turn and when a turn ends; the app answers whether to ask now. The chat shows the card and reports what the person
 * did; the app counts, remembers and sends the (opt-in) analytics.
 */
public interface ChatStarPrompt {
    /** A prompt started a turn. True = ask during this wait; it is already remembered as asked, so it never repeats. */
    public suspend fun promptStarted(): Boolean

    /** A turn ended; [success] = a reply with no error and no stop. */
    public suspend fun replyEnded(success: Boolean)

    public fun report(event: StarPromptEvent)
}

@ContributesTo(AppScope::class)
public interface StarPromptBindings {
    @Multibinds(allowEmpty = true) public val starPrompts: Set<ChatStarPrompt>
}

/** Rules the chat applies around the ask. */
public object StarPromptTurn {
    /**
     * The turn that just ended was a good one: its newest reply footer is neither failed nor stopped, and it belongs
     * to the newest prompt. [rows] are newest first.
     */
    public fun succeeded(rows: List<TranscriptRow>): Boolean {
        val prompt = rows.firstOrNull { it is PromptRow } ?: return false
        val footer = rows.firstOrNull { it is FooterRow } as? FooterRow ?: return false
        return footer.turnId == prompt.turnId && !footer.failed && !footer.stopped
    }
}
