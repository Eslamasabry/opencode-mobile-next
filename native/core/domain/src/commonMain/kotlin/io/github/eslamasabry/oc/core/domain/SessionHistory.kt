package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts

/**
 * Flutter session_history.dart: newest raw pages may contain only staged-away rows. Advance to a visible boundary
 * without inventing a cursor, returning that boundary page unchanged so a cleared revert can rehydrate its suffix.
 * A source replacement or a repeated cursor refuses the whole read instead of publishing partial history.
 */
public suspend fun readHistoryAtStagedBoundary(
    gateway: ServerGateway,
    session: SessionId,
    boundary: MessageId? = null,
    cursor: String? = null,
    limit: Int = 100,
    isCurrent: () -> Boolean,
): Outcome<ServerPage<MessageWithParts>> {
    val cursors = HashSet<String>()
    var next = cursor
    while (true) {
        when (val page = gateway.messagePage(session, next, limit)) {
            is Outcome.Failed -> return page
            is Outcome.Ok -> {
                if (!isCurrent()) return Failure.Refused(RefusalReason.StaleScope).failed()
                val raw = page.value
                val older = raw.nextCursor
                if (boundary == null || raw.items.any { it.info.id < boundary.raw } || older == null) return page
                if (older == next || !cursors.add(older)) return Failure.UnreadableReply().failed()
                next = older
            }
        }
    }
}
