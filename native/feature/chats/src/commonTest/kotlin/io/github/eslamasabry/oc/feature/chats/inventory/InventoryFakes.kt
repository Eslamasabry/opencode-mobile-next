package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import kotlinx.collections.immutable.toImmutableList

internal fun session(
    id: String,
    title: String? = id,
    directory: String? = "/one",
    parent: String? = null,
    updated: Long? = null,
): Session =
    Session(
        id = SessionId(id),
        title = title,
        directory = directory,
        parentId = parent?.let(::SessionId),
        time = updated?.let { SessionTime(createdMs = 1, updatedMs = it) },
    )

internal fun page(vararg items: Session, next: String? = null): ServerPage<Session> =
    ServerPage(items.toList().toImmutableList(), next)

internal fun sid(id: String): SessionId = SessionId(id)

/** A scripted server: each read is a suspend lambda, every request is recorded. */
internal class FakeSource : SessionInventorySource {
    var page: suspend (String?) -> Outcome<ServerPage<Session>> = { Outcome.Ok(page()) }
    var one: suspend (SessionId) -> Outcome<Session> = { Outcome.Ok(Session(id = it, directory = "/one")) }
    var statuses: suspend () -> Outcome<Map<SessionId, SessionStatus>> = { Outcome.Ok(emptyMap()) }
    val cursors = mutableListOf<String?>()
    val detailReads = mutableListOf<SessionId>()

    override suspend fun sessionPage(cursor: String?): Outcome<ServerPage<Session>> {
        cursors += cursor
        return page(cursor)
    }

    override suspend fun sessionStatuses(): Outcome<Map<SessionId, SessionStatus>> = statuses()

    override suspend fun session(id: SessionId): Outcome<Session> {
        detailReads += id
        return one(id)
    }

    override suspend fun deleteSession(id: SessionId): Outcome<Unit> = Outcome.Ok(Unit)
}

/** In-memory pins for one profile and location. */
internal class FakePins : SessionPinsView {
    private var pinned: Set<SessionId> = emptySet()
    override val canPin: Boolean = true

    override fun ids(): Set<SessionId> = pinned

    override suspend fun setPinned(id: SessionId, pinned: Boolean): Outcome<Unit> {
        this.pinned = if (pinned) this.pinned + id else this.pinned - id
        return Outcome.Ok(Unit)
    }
}

/** A preference file holding acknowledged values only; [refuse] makes every write fail. */
internal class FakeStorage : PreviewStorage {
    val values = LinkedHashMap<String, String>()
    var refuse = false

    override fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String): Boolean {
        if (refuse) return false
        values[key] = value
        return true
    }
}

internal val offline: Failure = Failure.Network()
