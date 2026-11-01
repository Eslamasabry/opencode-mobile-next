package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
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
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState

/**
 * The words the transcript shows that are not server text. P3 takes them as a parameter; S2-CHAT reads them from
 * `:ui:words` (no copy in this module).
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
 * The transcript (plan 09 §7.1): one reverse LazyColumn over the engine's newest-first rows, with String keys and
 * the RowKind as contentType, arranged from kit-chat parts only (K1). The live line and pending bubbles join in S2-CHAT.
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
    KitTranscriptList(
        state = list,
        newestKey = state.newestKey,
        jumpLabel = words.jumpToLatest,
        motion = motion,
        modifier = modifier,
    ) {
        items(state.rows, key = { it.key }, contentType = { it.kind }) { row -> ChatRow(row, words, reveal, motion) }
    }
}

@Composable
private fun ChatRow(row: TranscriptRow, words: ChatTranscriptWords, reveal: RevealRegistry, motion: KitMotionLevel) {
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
            )
        is PromptRow -> KitPromptBubble(row.text)
        is ThoughtRow -> KitQuietLine(words.thought(row.firstLine))
        is WorkLineRow -> KitQuietLine(words.workLine(row.calls, row.failed, row.state))
        is NoticeRow -> KitQuietLine(words.notice(row.notice))
        is FooterRow -> KitQuietLine(words.footer(row.failed))
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
