package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The words the transcript shows that are not server text. The P3 overload of [ChatTranscript]
 * takes them as a parameter (`:quality`'s P3 stream test drives it); the chat screen passes
 * [rememberChatWords]' resources.
 */
@Immutable
public interface ChatTranscriptWords {
    public val jumpToLatest: String

    public fun workLine(calls: Int, failed: Int, state: WorkState): String

    public fun notice(kind: NoticeKind): String

    public fun footer(failed: Boolean): String

    public fun thought(firstLine: String): String
}

/**
 * The words of the slice-2 items the P3 interface does not cover (resolved once per composition of
 * the screen).
 */
@Immutable
internal class ChatListWords(
    val messageActions: String,
    val working: String,
    val stopping: String,
    val sending: String,
    val notSent: String,
    val unconfirmed: String,
    val tryAgain: String,
)

/** Keys of the two item kinds the feature adds above the engine rows (plan 09 §7.2). */
internal object ListKeys {
    const val LIVE: String = "live"

    /** CHAT-TRANSCRIPT: background work still running (B-CHAT-007), just above the live line. */
    const val BACKGROUND: String = "background"

    fun pending(localId: Long): String = "pending:$localId"
}

/** The P3 transcript: the engine's rows only (kept for `:quality`'s P3 stream test). */
@Composable
public fun ChatTranscript(
    state: TranscriptState,
    words: ChatTranscriptWords,
    list: TranscriptListState,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    modifier: Modifier = Modifier,
) {
    ChatTranscriptList(
        rows = state.rows,
        live = null,
        pending = persistentListOf(),
        words = words,
        listWords = null,
        list = list,
        reveal = reveal,
        motion = motion,
        onOpenMenu = null,
        onRetrySend = {},
        modifier = modifier,
    )
}

/**
 * The transcript (plan 09 §7.1): one reverse LazyColumn, newest first. Bottom to top: the live line
 * (only while a turn runs) → optimistic prompt bubbles → the engine's rows. String keys, the
 * RowKind as contentType, kit parts only. The list follows when the newest key changes and the
 * person has not scrolled away (§7.3, in the kit).
 */
@Composable
internal fun ChatTranscriptList(
    rows: ImmutableList<TranscriptRow>,
    live: LiveTurnUi?,
    pending: ImmutableList<PendingSend>,
    words: ChatTranscriptWords,
    listWords: ChatListWords?,
    list: TranscriptListState,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    onOpenMenu: ((String) -> Unit)?,
    onRetrySend: (Long) -> Unit,
    modifier: Modifier = Modifier,
    facts: TurnFactsUi = TurnFactsUi.Empty,
) {
    val folds = rememberTranscriptFolds()
    val newestKey =
        when {
            live != null -> ListKeys.LIVE
            pending.isNotEmpty() -> ListKeys.pending(pending.first().localId)
            else -> rows.firstOrNull()?.key
        }
    KitTranscriptList(
        state = list,
        newestKey = newestKey,
        jumpLabel = words.jumpToLatest,
        motion = motion,
        modifier = modifier,
    ) {
        if (live != null && listWords != null) {
            item(key = ListKeys.LIVE, contentType = "live") {
                // No Stop here (plan 09 §4.4): Stop sits in the composer's row.
                // Retry, stall and shortening fold into the live line: no new bar (CHAT-TRANSCRIPT).
                val notice = facts.live
                KitQuietLine(
                    when {
                        live.stopping -> listWords.stopping
                        notice != null -> liveNoticeWords(notice)
                        else -> listWords.working
                    },
                    Modifier.testTag("chat-live-line"),
                )
            }
        }
        if (facts.background.isNotEmpty() && listWords != null) {
            item(key = ListKeys.BACKGROUND, contentType = "background") {
                KitQuietLine(backgroundWords(facts.background, facts.nowMs), Modifier.testTag("chat-background-work"))
            }
        }
        if (listWords != null) {
            items(
                pending,
                key = { ListKeys.pending(it.localId) },
                contentType = { RowKind.Prompt },
            ) { p ->
                PendingBubble(p, listWords, onRetry = { onRetrySend(p.localId) })
            }
        }
        items(rows, key = { it.key }, contentType = { it.kind }) { row ->
            ChatRow(row, words, listWords, reveal, motion, onOpenMenu, facts, folds)
        }
    }
}

@Composable
private fun PendingBubble(p: PendingSend, words: ChatListWords, onRetry: () -> Unit) {
    Column {
        KitPromptBubble(p.text)
        when (p.state) {
            PendingState.Sending -> KitQuietLine(words.sending)
            PendingState.Unconfirmed -> KitQuietLine(words.unconfirmed)
            PendingState.Failed -> {
                KitQuietLine(words.notSent)
                KitButton(
                    words.tryAgain,
                    onRetry,
                    role = KitButtonRole.Tertiary,
                    modifier = Modifier.testTag("chat-send-error-try-again"),
                )
            }
        }
    }
}

@Composable
private fun ChatRow(
    row: TranscriptRow,
    words: ChatTranscriptWords,
    listWords: ChatListWords?,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    onOpenMenu: ((String) -> Unit)?,
    facts: TurnFactsUi = TurnFactsUi.Empty,
    folds: TranscriptFolds? = null,
) {
    when (row) {
        is BlockRow ->
            KitReplyBlock(
                text = row.text,
                kind = row.kind.toKit(),
                live = row.live,
                streamed = row.origin == Origin.Streamed,
                revealKey = row.key,
                revealGroup = row.key.substringBeforeLast(':'),
                registry = reveal,
                motion = motion,
                continued = row.continued,
                continues = row.continues,
            )
        is PromptRow ->
            if (onOpenMenu != null && listWords != null) {
                // Tap is the visible way to the message's actions; long-press is the familiar
                // hidden twin (06 §11.1).
                KitTappable(
                    onClick = { onOpenMenu(row.key) },
                    onClickLabel = listWords.messageActions,
                    onLongClick = { onOpenMenu(row.key) },
                    onLongClickLabel = listWords.messageActions,
                    role = KitTappableRole.Row,
                ) {
                    KitPromptBubble(row.text)
                }
            } else {
                KitPromptBubble(row.text)
            }
        is ThoughtRow -> KitQuietLine(words.thought(row.firstLine))
        is WorkLineRow -> {
            val work = facts.work[row.turnId]
            if (work != null && folds != null) TurnWorkLine(work, facts.nowMs, folds)
            else KitQuietLine(
                if (listWords != null) workLineWords(row.calls, row.state)
                else words.workLine(row.calls, row.failed, row.state)
            )
        }
        is NoticeRow -> KitQuietLine(words.notice(row.notice))
        is FooterRow -> Column {
            if (onOpenMenu != null && listWords != null) {
                KitTappable(
                    onClick = { onOpenMenu(row.key) },
                    onClickLabel = listWords.messageActions,
                    role = KitTappableRole.Row,
                    modifier = Modifier.testTag("chat-turn-footer"),
                ) {
                    KitQuietLine(words.footer(row.failed))
                }
            } else {
                KitQuietLine(words.footer(row.failed))
            }
            val result = facts.results[row.turnId]
            if (result != null && folds != null) TurnResultLine(row.turnId, result, folds)
        }
    }
}

private fun RowKind.toKit(): KitBlockKind =
    when (this) {
        RowKind.Heading -> KitBlockKind.Heading
        RowKind.Quote -> KitBlockKind.Quote
        RowKind.ListItem -> KitBlockKind.ListItem
        RowKind.Code -> KitBlockKind.Code
        else -> KitBlockKind.Prose
    }
