package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.presentation.CodeCopy
import io.github.eslamasabry.oc.feature.chat.presentation.LongTable
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.feature.chat.presentation.FenceText
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeChunk
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownBlock
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdReveal
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdBlock
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.markdown.settle
import io.github.eslamasabry.oc.ui.kitchat.markdown.takesCaret
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
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
import io.github.eslamasabry.oc.feature.chat.cards.CardAnswerDisplay
import io.github.eslamasabry.oc.feature.chat.cards.ChatTurnCards
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
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
import kotlinx.collections.immutable.toImmutableList

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

    /** QA wave 4 N3: the footer of a reply the user stopped ("Stopped"), never an error line. */
    public fun stoppedFooter(): String = footer(false)

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
    /** B5: the plain reason and next step of a failed reply, by kind. */
    val replyFailed: (ReplyFailureKind) -> String = { "" },
    val messageError: (MessageErrorKind) -> String = { "" },
    /** B2/B5: the label and copy label of the Details fold under a failure. */
    val details: String = "",
    val copyDetails: String = "",
    /** B2: why a send did not leave, in plain words. */
    val sendFailure: (Failure) -> String = { "" },
)

/**
 * P12-CHAT-FEEL: what a reply's own parts do — links through the chat's link policy, a code block's Copy, a picture's
 * tap. Null (the P3 overload's default) keeps the plain stream-only drawing.
 */
@Immutable
internal class ReplyActions(
    val markdown: KitMarkdownActions,
    val copyLabel: String,
    val copy: (String) -> Unit,
    /** QA wave 4 N9: the code block's Copy says so after a tap. */
    val copiedLabel: String? = null,
    /** B-CHAT-064: the paths a drawn block shows, to be checked on the server. */
    val checkPaths: (List<String>) -> Unit = {},
    /** B-AGENT-024: the Continue next step of a reply that lost its provider connection (null = not offered). */
    val continueLabel: String? = null,
    val onContinue: (String) -> Unit = {},
)

/** B2: a short, safe code for a send failure (no server text, no address): what QA asked to see under Details. */
internal fun failureCode(f: Failure): String =
    when (f) {
        is Failure.Refused -> "Refused.${f.reason.name}"
        is Failure.Network -> "Network.${f.why.name}"
        is Failure.Unavailable -> "Unavailable.${f.feature.name}"
        is Failure.Busy -> "Busy"
        is Failure.Server -> "Server.${f.status ?: "-"}"
        is Failure.SignIn -> "SignIn.${f.why.name}"
        else -> f::class.simpleName ?: "Failure"
    }

/** Keys of the two item kinds the feature adds above the engine rows (plan 09 §7.2). */
internal object ListKeys {
    const val LIVE: String = "live"

    /** CHAT-TRANSCRIPT: background work still running (B-CHAT-007), just above the live line. */
    const val BACKGROUND: String = "background"

    fun pending(localId: Long): String = "pending:$localId"
}

/**
 * The P3 transcript: the engine's rows only (kept for `:quality`'s P3 stream test). P12-CHAT-FEEL: it draws replies the
 * way the chat screen does (Markdown, code with Copy), so the frame test measures the shipping path; links and Copy do
 * nothing here.
 */
@Composable
public fun ChatTranscript(
    state: TranscriptState,
    words: ChatTranscriptWords,
    list: TranscriptListState,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    modifier: Modifier = Modifier,
) {
    val reply = remember { ReplyActions(KitMarkdownActions(onLink = {}), copyLabel = "Copy", copy = {}) }
    // Its callers (the P3 test, the benchmark proof screen) run without the app graph: a pattern-masking redactor.
    KitStandaloneRedaction {
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
        reply = reply,
    )
    }
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
    onOpenSession: ((String) -> Unit)? = null,
    reply: ReplyActions? = null,
    onOpenFile: ((io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact) -> Unit)? = null,
) {
    val folds = rememberTranscriptFolds()
    val currentRows by rememberUpdatedState(rows)
    // B-SEC-094 / R18: a code block's Copy hands over what the block shows (redacted), never the raw fence; only
    // Copy reply copies the reply as written (plan 09 §6.6).
    val copyCode: (String, (String) -> String) -> Unit =
        remember(reply) {
            { key, mask ->
                val r = currentRows
                CodeCopy.of(r, r.indexOfFirst { it.key == key })?.let { reply?.copy?.invoke(mask(it)) }
            }
        }
    // B0: a repeated key kills the whole screen (LazyColumn throws), so the list never sees one.
    val unique = remember(rows) { rows.distinctKeys() }
    val tables = remember(unique) { LongTable.of(unique) }
    val newestKey =
        when {
            live != null -> ListKeys.LIVE
            pending.isNotEmpty() -> ListKeys.pending(pending.first().localId)
            else -> unique.firstOrNull()?.key
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
        items(unique, key = { it.key }, contentType = { it.kind }) { row ->
            // A table longer than one chunk is drawn whole by its first chunk; its later chunks draw nothing.
            if (reply != null && row is BlockRow && row.key in tables) {
                val whole = tables[row.key]
                if (whole != null)
                    ChatRow(row.copy(text = whole, continues = false), words, listWords, reveal, motion, onOpenMenu, facts, folds, onOpenSession, reply, copyCode, onOpenFile)
            } else {
                ChatRow(row, words, listWords, reveal, motion, onOpenMenu, facts, folds, onOpenSession, reply, copyCode, onOpenFile)
            }
        }
    }
}

/** [rows] without a second row of an already used key (the first, newest, wins); same list when unique. */
internal fun ImmutableList<TranscriptRow>.distinctKeys(): ImmutableList<TranscriptRow> {
    val seen = HashSet<String>(size * 2)
    if (all { seen.add(it.key) }) return this
    val kept = HashSet<String>(size * 2)
    return filter { kept.add(it.key) }.toImmutableList()
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
                p.failure?.let { failure ->
                    val line = words.sendFailure(failure)
                    if (line.isNotEmpty()) KitQuietLine(line)
                    if (words.details.isNotEmpty())
                        KitDetailsFold(
                            label = words.details,
                            copyLabel = words.copyDetails,
                            notes = listOf(failureCode(failure)),
                            modifier = Modifier.testTag("chat-send-failure-details"),
                        )
                }
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
    onOpenSession: ((String) -> Unit)? = null,
    reply: ReplyActions? = null,
    copyCode: (String, (String) -> String) -> Unit = { _, _ -> },
    onOpenFile: ((io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact) -> Unit)? = null,
) {
    when (row) {
        is BlockRow ->
            if (reply != null) RichBlock(row, reveal, motion, reply, copyCode)
            else
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
                    KitPromptBubble(CardAnswerDisplay.text(row.text))
                }
            } else {
                KitPromptBubble(CardAnswerDisplay.text(row.text))
            }
        is ThoughtRow -> KitQuietLine(words.thought(row.firstLine))
        is WorkLineRow ->
            // P12-CHAT-CARDS: a turn's agent cards stand right under its work line, in the reply (never folded in).
            Column {
                val work = facts.work[row.turnId]
                if (work != null && folds != null) TurnWorkLine(work, facts.nowMs, folds, onOpenSession, onOpenFile)
                else KitQuietLine(
                    if (listWords != null) workLineWords(row.calls, row.state)
                    else words.workLine(row.calls, row.failed, row.state)
                )
                ChatTurnCards(row.turnId)
            }
        is NoticeRow -> KitQuietLine(words.notice(row.notice))
        is FooterRow -> Column {
            if (row.failed && listWords != null && listWords.details.isNotEmpty()) {
                val reason = row.reason.orEmpty()
                // B-SEC-014/B-SEC-133: the server's text may carry a key or an Authorization header; Details shows
                // (and copies) it masked by the app's one redactor, as Flutter redacts notes before display.
                val mask = rememberKitMask()
                // B5: why the reply failed and what to do next; the server's own words only under Details.
                Column(Modifier.testTag("chat-reply-failed")) {
                    // A failed reply keeps its actions (Copy, Share, Select text, Delete), as Flutter's does.
                    if (onOpenMenu != null)
                        KitTappable(
                            onClick = { onOpenMenu(row.key) },
                            onClickLabel = listWords.messageActions,
                            role = KitTappableRole.Row,
                            modifier = Modifier.testTag("chat-turn-footer"),
                        ) {
                            KitQuietLine(words.footer(row.failed))
                        }
                    else KitQuietLine(words.footer(row.failed))
                    val kind = ReplyFailureKind.of(reason)
                    val typed = row.errorKind?.takeUnless { it == MessageErrorKind.Unknown }
                    KitQuietLine(typed?.let(listWords.messageError)?.takeIf { it.isNotBlank() } ?: listWords.replyFailed(kind))
                    // B-AGENT-024: a lost connection's next step is a visible Continue (the turn picks up there).
                    val continueLabel = reply?.continueLabel
                    if (kind == ReplyFailureKind.ConnectionLost && !continueLabel.isNullOrBlank())
                        KitButton(
                            label = continueLabel,
                            onClick = { reply.onContinue(continueLabel) },
                            role = KitButtonRole.Secondary,
                        )
                    if (reason.isNotBlank()) KitDetailsFold(
                        label = listWords.details,
                        copyLabel = listWords.copyDetails,
                        notes = listOf(mask(reason)),
                    )
                }
            } else if (onOpenMenu != null && listWords != null) {
                KitTappable(
                    onClick = { onOpenMenu(row.key) },
                    onClickLabel = listWords.messageActions,
                    role = KitTappableRole.Row,
                    modifier = Modifier.testTag("chat-turn-footer"),
                ) {
                    KitQuietLine(if (row.stopped) words.stoppedFooter() else words.footer(row.failed))
                }
            } else {
                KitQuietLine(if (row.stopped) words.stoppedFooter() else words.footer(row.failed))
            }
            val result = facts.results[row.turnId]
            if (result != null && folds != null) TurnResultLine(row.turnId, result, folds)
        }
    }
}

/** Plan 09 R13 column (as the kit's reply block): 700 dp cap, the row gap only where the block does not continue. */
private fun Modifier.replyColumn(continued: Boolean, continues: Boolean): Modifier =
    fillMaxWidth()
        .widthIn(max = 700.dp)
        .padding(
            start = Space.S4.dp,
            end = Space.S4.dp,
            top = if (continued) 0.dp else Space.S1.dp,
            bottom = if (continues) 0.dp else Space.S1.dp,
        )

/**
 * P12-CHAT-FEEL: a reply block drawn as Markdown (Flutter `KitMarkdown`): bold, links (through the link policy), inline
 * code, lists, tables and pictures, revealed word by word while it streams (the same registry as the plain block).
 * Code: a streaming fence keeps the stream-tuned block (fence lines hidden); a settled one gets Copy and sideways
 * scroll, coloured when it is one row, seamless across STREAM-FIX chunks otherwise.
 */
@Composable
private fun RichBlock(
    row: BlockRow,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    reply: ReplyActions,
    copyCode: (String, (String) -> String) -> Unit,
) {
    val column = Modifier.replyColumn(row.continued, row.continues)
    if (row.kind == RowKind.Code) {
        val body = remember(row.text) { FenceText.body(row.text) }
        val mask = rememberKitMask()
        val streamed = row.origin == Origin.Streamed
        // A streamed fence keeps the stream-tuned block until its words have all landed and faded: the settled view
        // takes over only then, so its reveal never stops half way (and never holds up the blocks after it).
        val revealing =
            streamed && reveal.stateFor(row.key, row.key.substringBeforeLast(':'), row.live, body.length).active
        when {
            row.live || row.open || revealing ->
                KitReplyBlock(
                    text = body,
                    kind = KitBlockKind.Code,
                    live = row.live,
                    streamed = streamed,
                    revealKey = row.key,
                    revealGroup = row.key.substringBeforeLast(':'),
                    registry = reveal,
                    motion = motion,
                    continued = row.continued,
                    continues = row.continues,
                )
            !row.continued && !row.continues ->
                Box(column) {
                    KitCodeBlock(
                        source = body,
                        kind = KitCodeKind.Code,
                        language = CodeLanguage.of(FenceText.info(row.text)),
                        maxLines = Int.MAX_VALUE,
                        copyLabel = reply.copyLabel,
                        onCopy = { copyCode(row.key, mask) },
                        copiedLabel = reply.copiedLabel,
                    )
                }
            else ->
                KitCodeChunk(
                    source = body,
                    continued = row.continued,
                    continues = row.continues,
                    modifier = column,
                    copyLabel = reply.copyLabel,
                    onCopy = { copyCode(row.key, mask) },
                    copiedLabel = reply.copiedLabel,
                )
        }
        return
    }
    val blocks = remember(row.key, row.text, row.live) { KitMdSource.blocks(row.key, row.text, row.live) }
    // B-CHAT-064: the paths this block shows are checked once per settled text; confirmed ones become tappable.
    val paths = remember(blocks) { pathsOf(blocks) }
    if (paths.isNotEmpty() && !row.live) LaunchedEffect(paths) { reply.checkPaths(paths) }
    val streamed = row.origin == Origin.Streamed
    val md = remember(reveal, motion, streamed, row.key) { KitMdReveal(reveal, row.key.substringBeforeLast(':'), motion, streamed) }
    // A growing row can re-parse into fewer blocks; the reveal of a block that went away is finished, not orphaned.
    val drawn = remember(row.key) { IntArray(1) }
    SideEffect {
        for (i in blocks.size until drawn[0]) md.settle("${row.key}#$i")
        drawn[0] = blocks.size
    }
    Column(column) {
        val last = blocks.lastIndex
        blocks.forEachIndexed { i, block ->
            androidx.compose.runtime.key(block.key) {
                val tail = row.live && i == last
                KitMarkdownBlock(block, reply.markdown, live = tail, caret = tail && block.takesCaret, reveal = md)
            }
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

/** The file paths (B-CHAT-064 path spans) the blocks show, in order, each once. */
internal fun pathsOf(blocks: List<KitMdBlock>): List<String> {
    val out = LinkedHashSet<String>()
    fun scan(inlines: io.github.eslamasabry.oc.ui.kitchat.markdown.KitInlines) {
        for (span in inlines.spans) (span.kind as? io.github.eslamasabry.oc.ui.kitchat.markdown.KitSpanKind.Path)?.let { out += it.path }
    }
    fun walk(b: KitMdBlock) {
        when (b) {
            is KitMdBlock.Prose -> scan(b.inlines)
            is KitMdBlock.Heading -> scan(b.inlines)
            is KitMdBlock.ListItem -> scan(b.inlines)
            is KitMdBlock.Quote -> b.children.forEach(::walk)
            is KitMdBlock.Table -> {
                b.header.forEach(::scan)
                b.rows.forEach { r -> r.forEach(::scan) }
            }
            else -> Unit
        }
    }
    blocks.forEach(::walk)
    return out.toList()
}
