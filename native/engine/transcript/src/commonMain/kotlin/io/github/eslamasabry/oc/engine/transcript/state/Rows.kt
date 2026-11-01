package io.github.eslamasabry.oc.engine.transcript.state

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

/**
 * The ready-to-draw rows (plan 09 §3.7). Every row is immutable; an unchanged row keeps its instance across
 * publishes so the list skips it by identity. This package is listed in config/compose-stability.conf
 * (`engine.**.state.**`), so Compose treats these as stable without a Compose import here. No copy lives in rows:
 * kinds and counts only, words come from `:ui:words`.
 */
public enum class RowKind {
    Prompt, Prose, Heading, Quote, ListItem, Code, Table, Pictures, AgentBlock, Rule, Thought,
    WorkLine, TimelineStep, Notice, Marker, BackgroundResult, CardCall, AssistantError, Footer, Older, Excerpt,
}

/** Where a block came from: streamed rows may animate their reveal, loaded rows never do (plan 09 §8.2). */
public enum class Origin { Streamed, Loaded }

/** Where a row sits in its turn: spacing and the 700 dp column, never frames (plan 09 §3.7). */
public enum class TurnPosition { First, Middle, Last, Only }

public sealed interface TranscriptRow {
    /** Stable String key: never an index or a hashCode (§7.2). */
    public val key: String

    /** LazyColumn contentType. */
    public val kind: RowKind
    public val turnId: String

    /** The message this row draws; null for rows that belong to no message (Older). Feeds the earlier pill (§7.3). */
    public val messageId: String?
    public val position: TurnPosition
}

/** What the person sent. [files] counts attachments; the words are the person's own, never redacted here. */
public data class PromptRow(
    override val key: String,
    override val turnId: String,
    val text: String,
    override val messageId: String? = null,
    val files: Int = 0,
    override val position: TurnPosition = TurnPosition.First,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.Prompt
}

/**
 * One Markdown block of an assistant text part. Key `b:<messageId>/<partId>:<startOffset>` (OC2 part ids are
 * per-message ordinals, so the part id alone repeats in every step; slice-0 P3 D2, now in plan 09 §3.7).
 */
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
    override val messageId: String? = null,
    override val position: TurnPosition = TurnPosition.Middle,
) : TranscriptRow

/** A reasoning part of the running turn, folded under its first line (§3.7). Finished turns fold thoughts into work. */
public data class ThoughtRow(
    override val key: String,
    override val turnId: String,
    val firstLine: String,
    override val messageId: String? = null,
    override val position: TurnPosition = TurnPosition.Middle,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.Thought
}

public enum class WorkState { Running, Done, EndedFailed, Stopped }

/**
 * One line per stretch of work (`work:<turnId>`; a finished turn has exactly one, gathering every tool call,
 * thought, passing sentence and mid-turn notice; the running turn keeps a line per stretch where it happened, the
 * later ones keyed `work:<turnId>#<n>`). Counts only: `ToolPresentation` words them.
 */
public data class WorkLineRow(
    override val key: String,
    override val turnId: String,
    val calls: Int,
    val failed: Int,
    val state: WorkState,
    override val messageId: String? = null,
    val thoughts: Int = 0,
    /** Passing words and notices folded into this line (finished turns only, §3.6 markFoldedWork). */
    val folded: Int = 0,
    /** Tool call ids in order: the timeline sheet and jump targets read them. */
    val callIds: ImmutableList<String> = persistentListOf(),
    override val position: TurnPosition = TurnPosition.Middle,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.WorkLine
}

/** Row-level notice kinds (the wire `NoticeKind` is open; rows need a closed set for `:ui:words`). */
public enum class NoticeKind { InstructionsUpdated, SkillChanged, ShellChanged, Compaction, Other }

/** A notice the server filed mid-turn (T9, T10): only its kind, never instruction or note text. */
public data class NoticeRow(
    override val key: String,
    override val turnId: String,
    val notice: NoticeKind,
    override val messageId: String? = null,
    override val position: TurnPosition = TurnPosition.Middle,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.Notice
}

public enum class MarkerKind { Switch, Compacting }

/** Hairline-word-hairline: a model/agent/location switch or the running compaction (§3.7, §9.6). */
public data class MarkerRow(
    override val key: String,
    override val turnId: String,
    val marker: MarkerKind,
    override val messageId: String? = null,
    override val position: TurnPosition = TurnPosition.Middle,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.Marker
}

/** An assistant step that failed. [recovered]: more steps followed in its turn, so it is drawn quiet (§9.6). */
public data class AssistantErrorRow(
    override val key: String,
    override val turnId: String,
    val errorKind: MessageErrorKind?,
    val recovered: Boolean,
    override val messageId: String? = null,
    override val position: TurnPosition = TurnPosition.Middle,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.AssistantError
}

/** The one footer of a finished turn, under its owner step (§3.6 turnActionOwners). */
public data class FooterRow(
    override val key: String,
    override val turnId: String,
    val failed: Boolean,
    override val messageId: String? = null,
    /** True on the newest turn when the run ended interrupted: "stopped" words (§9.6). */
    val stopped: Boolean = false,
    override val position: TurnPosition = TurnPosition.Last,
) : TranscriptRow {
    override val kind: RowKind get() = RowKind.Footer
}

/** "Load older" or "Reload"; always the last row (top of the screen). */
public data class OlderRow(val state: OlderState) : TranscriptRow {
    override val key: String get() = "older"
    override val kind: RowKind get() = RowKind.Older
    override val turnId: String get() = "older"
    override val messageId: String? get() = null
    override val position: TurnPosition get() = TurnPosition.Only
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

public sealed interface TranscriptPhase {
    public data object Loading : TranscriptPhase

    public data object Live : TranscriptPhase

    /** Only when nothing is on screen: a refresh never shows an error over a transcript (map CHAT-GENUI-54). */
    public data class Failed(val failure: Failure) : TranscriptPhase
}

/** The "Load older" row's state (§3.4 step 3). */
public enum class OlderState { None, Available, Loading, NeedsReload, Exhausted }

public data class RetryFacts(val attempt: Int, val nextAtMs: Long?)

public data class RunFacts(
    val busy: Boolean = false,
    val lastOutcome: RunOutcome? = null,
    /** The run was busy when the stream dropped: its end re-reads history (R20). */
    val crossedDrop: Boolean = false,
    val connects: Int = 0,
    /** +1 per history reset (revert, compaction): request card states watch it (§3.3). */
    val historyEpoch: Int = 0,
    val retry: RetryFacts? = null,
)

public data class TranscriptState(
    /** +1 per publish; tests count publishes with it. First field, so StateFlow's equality check stops here. */
    val revision: Long,
    /** NEWEST FIRST: index 0 is the bottom of the reverse list (§7.1). */
    val rows: ImmutableList<TranscriptRow>,
    /** Row key → OLDEST-first position (plan 09 §3.7 `index`); use [indexOf] for the newest-first index. */
    val positions: PersistentMap<String, Int>,
    val run: RunFacts,
    val phase: TranscriptPhase = TranscriptPhase.Loading,
    val older: OlderState = OlderState.None,
    /** Message id → oldest-first ordinal; changes only on a new message or an older-page prepend (§7.3 pill). */
    val messageOrder: PersistentMap<String, Int> = persistentMapOf(),
    val messageCount: Int = 0,
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
