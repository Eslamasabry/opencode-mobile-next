package io.github.eslamasabry.oc.feature.chat.inbox.presentation

import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState

/**
 * N-48 (E20-S01): a send that may already have arrived is said once, under its own bubble (with Review). The chat's
 * "messages are waiting" line counts only what no bubble already says: never the held send itself, and never the
 * fresh copy its Send again put in the queue while the old note still stands.
 */
public object PendingNotice {
    public fun unsaid(rows: List<ChatInboxRow>, bubbles: List<PendingSend>): List<ChatInboxRow> {
        val said =
            bubbles.filter { it.state == PendingState.Unconfirmed }.mapTo(HashSet()) { it.text.trim() }
        return rows.filterNot { it.local && (it.dispatched || it.text.trim() in said) }
    }
}
