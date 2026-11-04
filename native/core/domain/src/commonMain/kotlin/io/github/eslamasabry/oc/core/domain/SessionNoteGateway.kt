package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation

/** The mobile-owned instruction entry; other instruction values must never leave the adapter. */
public interface SessionNoteGateway {
    public suspend fun loadSessionNote(
        session: SessionId,
        location: ServerLocation,
    ): Outcome<String?>

    public suspend fun saveSessionNote(
        session: SessionId,
        location: ServerLocation,
        value: String?,
        beforeSend: () -> Boolean,
    ): Outcome<Unit>
}
