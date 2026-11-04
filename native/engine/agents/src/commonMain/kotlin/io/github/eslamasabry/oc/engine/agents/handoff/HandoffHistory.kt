package io.github.eslamasabry.oc.engine.agents.handoff

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One page of the gateway's history contract: oldest-first items; pages walk from the newest toward older history. */
public data class HandoffHistoryPage(
    val items: List<HandoffMessage>,
    val olderCursor: String? = null,
    val hasMore: Boolean = olderCursor != null,
)

/** How a backend exposes read-only history. Neither may resume a thread, start work or return partial history. */
public sealed interface HandoffHistoryReader {
    /** The paged history contract (Flutter `GenUiHistoryGateway`). */
    public fun interface Paged : HandoffHistoryReader {
        public suspend fun page(sessionId: String, cursor: String?, limit: Int): Outcome<HandoffHistoryPage>
    }

    /** A complete, bounded, chronological snapshot (Flutter `AgentHandoffHistoryGateway`). */
    public fun interface Snapshot : HandoffHistoryReader {
        public suspend fun snapshot(sessionId: String): Outcome<List<HandoffMessage>>
    }
}

/** The source connection's pinned location; read again after every await. */
public interface HandoffHistorySource {
    public val directory: String?
    public val workspace: String?
    public val isClosed: Boolean

    /** Null: this backend has no handoff history reader. */
    public val historyReader: HandoffHistoryReader?
}

public enum class HandoffHistoryFailureKind {
    Unsupported, StaleScope, InvalidSession, InvalidMessage, InvalidCursor, PageLimit, ReadFailed,
}

public sealed interface HandoffHistoryRead {
    public data class Read(val messages: ImmutableList<HandoffMessage>) : HandoffHistoryRead

    /** Content-free: no transport text, message or cursor value crosses this boundary. */
    public data class Failed(val kind: HandoffHistoryFailureKind) : HandoffHistoryRead
}

/**
 * Port of `readAgentHandoffHistory` (lib/domain/agent_handoff_history.dart): a complete bounded source history or a
 * failure, never a partial result. The `maxPages * 50` message budget also bounds a snapshot. Overlapping message ids keep
 * the copy from the newest page. [isCurrent] tracks the caller's profile/selection generation; directory and workspace
 * are pinned here.
 */
public suspend fun readHandoffHistory(
    source: HandoffHistorySource,
    sessionId: String,
    isCurrent: () -> Boolean,
    maxPages: Int = 100,
): HandoffHistoryRead {
    require(maxPages > 0) { "maxPages" }
    fun failed(kind: HandoffHistoryFailureKind) = HandoffHistoryRead.Failed(kind)
    if (sessionId.isBlank()) return failed(HandoffHistoryFailureKind.InvalidSession)
    val reader = source.historyReader ?: return failed(HandoffHistoryFailureKind.Unsupported)
    val directory = source.directory
    val workspace = source.workspace
    fun current(): Boolean =
        isCurrent() && !source.isClosed && source.directory == directory && source.workspace == workspace

    // Each message is copied so a later stream update cannot change the snapshot while another page is awaited.
    fun frozen(message: HandoffMessage) = message.copy(parts = message.parts.toImmutableList())

    when (reader) {
        is HandoffHistoryReader.Snapshot -> {
            if (!current()) return failed(HandoffHistoryFailureKind.StaleScope)
            val read = attempt({ Failure.Unexpected() }) { reader.snapshot(sessionId) }.flatten()
            // Selection changes have priority over a failed read.
            if (!current()) return failed(HandoffHistoryFailureKind.StaleScope)
            val snapshot = (read as? Outcome.Ok)?.value ?: return failed(HandoffHistoryFailureKind.ReadFailed)
            if (snapshot.size > maxPages * PAGE_SIZE) return failed(HandoffHistoryFailureKind.PageLimit)
            val ids = HashSet<String>()
            for (message in snapshot) {
                if (message.sessionId != sessionId || message.id.isBlank() || !ids.add(message.id)) {
                    return failed(HandoffHistoryFailureKind.InvalidMessage)
                }
            }
            return HandoffHistoryRead.Read(snapshot.map(::frozen).toImmutableList())
        }
        is HandoffHistoryReader.Paged -> {
            val pages = mutableListOf<List<HandoffMessage>>()
            val seenMessages = HashSet<String>()
            val seenCursors = HashSet<String>()
            var cursor: String? = null
            repeat(maxPages) {
                if (!current()) return failed(HandoffHistoryFailureKind.StaleScope)
                val read = attempt({ Failure.Unexpected() }) { reader.page(sessionId, cursor, PAGE_SIZE) }.flatten()
                if (!current()) return failed(HandoffHistoryFailureKind.StaleScope)
                val page = (read as? Outcome.Ok)?.value ?: return failed(HandoffHistoryFailureKind.ReadFailed)
                val unique = mutableListOf<HandoffMessage>()
                for (message in page.items.asReversed()) {
                    if (message.sessionId != sessionId || message.id.isBlank()) {
                        return failed(HandoffHistoryFailureKind.InvalidMessage)
                    }
                    if (seenMessages.add(message.id)) unique += frozen(message)
                }
                pages += unique.asReversed().toList()
                if (!page.hasMore) return HandoffHistoryRead.Read(pages.asReversed().flatten().toImmutableList())
                val next = page.olderCursor
                if (next == null || next.isBlank() || !seenCursors.add(next)) {
                    return failed(HandoffHistoryFailureKind.InvalidCursor)
                }
                cursor = next
            }
            return failed(HandoffHistoryFailureKind.PageLimit)
        }
    }
}

private const val PAGE_SIZE = 50

private fun <T> Outcome<Outcome<T>>.flatten(): Outcome<T> = when (this) {
    is Outcome.Ok -> value
    is Outcome.Failed -> this
}
