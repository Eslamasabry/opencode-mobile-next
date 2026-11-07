package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space

/**
 * Which part of one turn a [KitTurn] row draws (Flutter `KitTurnSegment`, kit_turn.dart:64-79). The transcript builds
 * each block as its own lazy row, so one turn spans several rows; each row names its part and the turn keeps its
 * rhythm: no section gap inside it, and the section gap only under the row that ends it.
 */
public enum class KitTurnSegment {
    /** The whole turn in one row. */
    Whole,

    /** The turn's first row: its prompt. The rest follows in later rows. */
    First,

    /** A row inside the turn: neither its prompt nor its end. */
    Middle,

    /** The row that ends the turn (its footer, or its last block when it has none). */
    Last,
}

/**
 * What the next row of the same turn is, for a [KitTurnSegment.First] or [KitTurnSegment.Middle] row. Flutter lays
 * the parts of a turn out in one column with a gap per kind (kit_turn.dart:487-566); native rows carry it instead.
 */
public enum class KitTurnNext {
    /** Another block of the turn (a reply paragraph, a work line, a notice). */
    Block,

    /** The turn's phase or live line ("Writing · 7 s", "This reply was stopped…"). */
    Line,

    /** The turn's footer (Copy and More): it sits one step under the reply (kit_turn.dart:558). */
    Footer,

    /** The next chunk of this same block (STREAM-FIX): no gap at all, so the chunks read as one. */
    Joined,
}

/**
 * The space under one turn row, in dp (Flutter `gapAfter`, kit_turn.dart:587-597, plus the in-turn gaps of
 * :487-566): 16 under the prompt (`space4`), 12 between the blocks of a turn and before its phase line (`space3`),
 * 4 above the footer (`space1`), 24 under a finished turn (`sectionGap`), 8 under the newest turn (`space2`: it sits
 * right above the composer). A chunk that continues into the next row keeps no gap.
 */
public fun kitTurnGapAfter(segment: KitTurnSegment, latest: Boolean, next: KitTurnNext = KitTurnNext.Block): Int =
    when (segment) {
        KitTurnSegment.Whole,
        KitTurnSegment.Last -> if (latest) Space.S2 else FlutterKitTokens.SECTION_GAP
        KitTurnSegment.First,
        KitTurnSegment.Middle ->
            when (next) {
                KitTurnNext.Joined -> 0
                KitTurnNext.Footer -> Space.S1
                KitTurnNext.Block,
                KitTurnNext.Line -> if (segment == KitTurnSegment.First) Space.S4 else Space.S3
            }
    }

/**
 * True inside a [KitTurn]: the transcript parts (prompt bubble, reply block, quiet line, footer) then drop their own
 * 4 dp row padding, since the turn gives every gap. Outside a turn they keep it, so a caller not yet tagging its rows
 * looks as before.
 */
internal val LocalKitTurn = staticCompositionLocalOf { false }

/**
 * One row of a conversation turn (Flutter `KitTurn`, kit_turn.dart:487-600): its [content] (the prompt, one or more
 * blocks, the footer) stacked 12 dp apart, then the space the row's place in the turn asks for
 * ([kitTurnGapAfter]): 16 under the prompt, 12 between blocks, 4 above the footer, 24 under a turn that ended, 8
 * under the newest one. That gap, not a padding per block, is what groups a chat into turns.
 *
 * The transcript is a reversed lazy list, so the gap is the row's own bottom padding: it stays under the row whatever
 * the list's direction. [latest] marks the newest turn; [next] says what follows a first or middle row.
 */
@Composable
public fun KitTurn(
    segment: KitTurnSegment,
    modifier: Modifier = Modifier,
    latest: Boolean = false,
    next: KitTurnNext = KitTurnNext.Block,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalKitTurn provides true) {
        Column(
            modifier.fillMaxWidth().padding(bottom = kitTurnGapAfter(segment, latest, next).dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            content = content,
        )
    }
}
