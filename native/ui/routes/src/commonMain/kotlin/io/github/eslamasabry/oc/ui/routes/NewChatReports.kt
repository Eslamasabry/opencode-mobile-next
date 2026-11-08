package io.github.eslamasabry.oc.ui.routes

import kotlinx.coroutines.flow.SharedFlow

/** What became of a new conversation opened with a [ChatsRoute.New.reportKey]. */
public enum class NewChatOutcome {
    /** The first message was sent: the words are the conversation's first message now. */
    FirstMessageSent,

    /** The person left the new conversation without sending. */
    Left,

    /** A send was tried and whether it arrived is not known. */
    Unknown,
}

public data class NewChatReport(val key: String, val outcome: NewChatOutcome)

/**
 * One report per keyed new conversation, from the new-conversation page to whoever opened it (FactsBook's "Talk about
 * this" tells the node whether the chat started). A report nobody listens for is dropped.
 */
public interface NewChatReports {
    public val reports: SharedFlow<NewChatReport>

    public fun report(key: String, outcome: NewChatOutcome)
}
