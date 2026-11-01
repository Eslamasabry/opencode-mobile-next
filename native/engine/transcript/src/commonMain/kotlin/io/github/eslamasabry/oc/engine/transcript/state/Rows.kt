package io.github.eslamasabry.oc.engine.transcript.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

/**
 * The ready-to-draw rows (plan 09 §3.7), trimmed for P3. Every row is immutable; an unchanged row keeps its instance
 * across publishes so the list skips it by identity. This package is listed in config/compose-stability.conf
 * (`engine.**.state.**`), so Compose treats these as stable without a Compose import here.
 */
public enum class RowKind { Prompt, Prose, Heading, Quote, ListItem, Code, Thought, WorkLine, Notice, Footer }

/** Where a block came from: streamed rows may animate their reveal, loaded rows never do (plan 09 §8.2). */
public enum class Origin { Streamed, Loaded }

public sealed interface TranscriptRow {
    /** Stable String key: never an index or a hashCode (§7.2). */
    public val key: String

    /** LazyColumn contentType. */
    public val kind: RowKind
    public val turnId: String
}

public data class PromptRow(override val key: String, override val turnId: String, val text: String) : TranscriptRow {
    override val kind: RowKind get() = RowKind.Prompt
}

/** One Markdown block of an assistant text part. Key `b:<messageId>/<partId>:<startOffset>` (OC2 part ids repeat per message). */
public data class BlockRow(
    override val key: String,
    override val turnId: String,
    override val kind: RowKind,
    val partId: String,
    val start: Int,
    val text: String,
    /** True only for the tail block of a part that is receiving deltas. */
    val live: Boolean,
    /** A code fence that has not closed yet. */
    val open: Boolean,
    val origin: Origin,
) : TranscriptRow

public data class ThoughtRow(override val key: String, override val turnId: String, val firstLine: String) :
    TranscriptRow {
    override val kind: RowKind get() = RowKind.Thought
}

public enum class WorkState { Running, Done, EndedFailed, Stopped }

public data class WorkLineRow(
    override val key: String,
    override val turnId: String,
    val calls: Int,
    val failed: Int,
    val state: WorkState,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.WorkLine
}

public data class NoticeRow(override val key: String, override val turnId: String, val notice: NoticeKind) :
    TranscriptRow {
    override val kind: RowKind get() = RowKind.Notice
}

public data class FooterRow(override val key: String, override val turnId: String, val failed: Boolean) :
    TranscriptRow {
    override val kind: RowKind get() = RowKind.Footer
}

/**
 * O(1) newest-first view over an oldest-first persistent list (plan 09 §3.7 `ReversedImmutableList`). The engine keeps
 * the changing tail at the cheap end of the persistent vector and never copies 5,000 rows per publish.
 */
public class NewestFirst<T>(private val oldestFirst: PersistentList<T>) : AbstractList<T>(), ImmutableList<T> {
    override val size: Int get() = oldestFirst.size

    override fun get(index: Int): T = oldestFirst[oldestFirst.size - 1 - index]

    override fun subList(fromIndex: Int, toIndex: Int): ImmutableList<T> =
        NewestFirst(oldestFirst.subList(size - toIndex, size - fromIndex).toPersistentList())

    /** Identity of the backing list: two views over the same list are the same rows. */
    public val source: PersistentList<T> get() = oldestFirst

    override fun equals(other: Any?): Boolean =
        (other is NewestFirst<*> && other.oldestFirst === oldestFirst) || super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

public data class RunFacts(val busy: Boolean = false, val lastOutcome: RunOutcome? = null, val connects: Int = 0)

public data class TranscriptState(
    /** +1 per publish; tests count publishes with it. First field, so StateFlow's equality check stops here. */
    val revision: Long,
    /** NEWEST FIRST: index 0 is the bottom of the reverse list (§7.1). */
    val rows: ImmutableList<TranscriptRow>,
    /** Row key → OLDEST-first position; use [indexOf] for the newest-first index. Built incrementally on the lane. */
    val positions: PersistentMap<String, Int>,
    val run: RunFacts,
) {
    /** Newest-first index of [key], or -1. */
    public fun indexOf(key: String): Int = positions[key]?.let { rows.size - 1 - it } ?: -1

    /** Key of the newest row: the list host follows when it changes (§7.3). */
    public val newestKey: String? get() = if (rows.isEmpty()) null else rows[0].key

    public companion object {
        public val Empty: TranscriptState =
            TranscriptState(0, NewestFirst(persistentListOf()), persistentMapOf(), RunFacts())
    }
}
