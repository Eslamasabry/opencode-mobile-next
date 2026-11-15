package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * Optional (issue 147): a backend whose history read can start an agent's process (Paseo: a saved agent's timeline
 * loads it) reads that history only for a conversation a person opened. Every other reader (list rows, previews, the
 * return brief, widgets, monitors) gets what is already known without waking anything.
 */
public interface HistoryOpenGateway {
    /** A person opened [session] (or is about to: a row was pressed): its history may now be read in full. */
    public fun historyOpened(session: SessionId)
}
