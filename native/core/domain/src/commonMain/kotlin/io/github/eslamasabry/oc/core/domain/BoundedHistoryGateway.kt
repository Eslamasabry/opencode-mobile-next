package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import kotlinx.collections.immutable.ImmutableList

/** A full page without a cursor is incomplete: recovery must never treat it as an authoritative end. */
public data class BoundedHistoryPage(
    val items: ImmutableList<MessageWithParts>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

/**
 * Optional bounded history for agent-card recovery. The transport enforces a 1 MiB body and a 10-second total
 * deadline before decoding, and rejects malformed pages or a source that changed while the request was running.
 * Backends without this facet cannot supply authoritative card recovery through an unbounded fallback.
 */
public interface BoundedHistoryGateway {
    public suspend fun boundedHistoryPage(
        session: SessionId,
        cursor: String? = null,
        limit: Int = 50,
    ): Outcome<BoundedHistoryPage>
}
