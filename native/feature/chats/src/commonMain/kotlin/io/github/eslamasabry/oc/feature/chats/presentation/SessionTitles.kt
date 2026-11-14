package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.text.ConversationTitle

/** A row title as the screen draws it; the screen picks the words for [New] and [Untitled]. */
public sealed interface RowTitle {
    public data class Text(val text: String) : RowTitle

    /** The server's dated placeholder: "New conversation". */
    public data object New : RowTitle

    /** Nothing readable: the surface's own untitled wording. */
    public data object Untitled : RowTitle
}

/**
 * Display rules for session titles (Dart `displaySessionTitleText`, `presentedSessionTitleText`,
 * `isPlaceholderSessionTitle`, `stripIsoStamp`). The stored title is never changed; every list goes through [present].
 */
public object SessionTitles {
    /** The title with leaked model markup cut off, trimmed; empty when nothing readable is left. */
    public fun text(title: String?): String = ConversationTitle.readable(title)

    public fun isPlaceholder(title: String): Boolean = ConversationTitle.isPlaceholder(title)

    public fun present(raw: String?): RowTitle =
        when (val shown = ConversationTitle.shown(raw)) {
            is ConversationTitle.Shown.Text -> RowTitle.Text(shown.text)
            ConversationTitle.Shown.New -> RowTitle.New
            ConversationTitle.Shown.Untitled -> RowTitle.Untitled
        }
}
