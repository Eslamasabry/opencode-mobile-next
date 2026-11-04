package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.core.model.InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** B-PROTO-045: the pending server inbox is read afresh, never reconstructed by replay. */
internal object Oc2Inbox {
    suspend fun list(gateway: Oc2Gateway, session: SessionId): Outcome<ImmutableList<InboxItem>> {
        val revision = gateway.scopeRevision
        val read = gateway.json(path(session), query = gateway.locationQuery())
        if (read is Outcome.Failed) return read
        if (gateway.scopeRevision != revision)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val root = (read as Outcome.Ok).value
        val rows =
            ((root as? JsonObject)?.get("data") ?: root) as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        val seen = HashSet<String>()
        return rows
            .map { row ->
                val wire =
                    (row as? JsonObject)?.let(Oc2InboxItem::fromJson)
                        ?: return Failure.UnreadableReply().failed()
                if (
                    wire.id.isBlank() ||
                        !seen.add(wire.id) ||
                        (wire.sessionID != null && wire.sessionID != session.raw)
                )
                    return Failure.UnreadableReply().failed()
                InboxItem(
                    InboxItemId(wire.id),
                    session,
                    wire.timeCreated,
                    wire.type,
                    wire.payload,
                    when (wire.delivery?.name) {
                        "Steer" -> InboxDelivery.Steer
                        "Queue" -> InboxDelivery.Queue
                        else -> null
                    },
                )
            }
            .toImmutableList()
            .ok()
    }

    suspend fun cancel(gateway: Oc2Gateway, session: SessionId, item: InboxItemId): Outcome<Unit> =
        gateway.json(path(session, item), HttpMethod.Delete, query = gateway.locationQuery()).map {}

    suspend fun delivery(
        gateway: Oc2Gateway,
        session: SessionId,
        item: InboxItemId,
        delivery: InboxDelivery,
    ): Outcome<Unit> =
        gateway
            .json(
                path(session, item) + if (delivery == InboxDelivery.Steer) "/steer" else "/queue",
                HttpMethod.Post,
                query = gateway.locationQuery(),
            )
            .map {}

    private fun path(session: SessionId, item: InboxItemId? = null): String =
        "/session/${session.raw.encodeURLPathPart()}/inbox" +
            (item?.let { "/${it.raw.encodeURLPathPart()}" } ?: "")
}
