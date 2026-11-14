package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import kotlinx.coroutines.flow.first

/**
 * Where a reader stood in the transcript, by the row's stable key and how far into it, never by list position.
 * A list position names a different row once rows arrive below while the person is away (a run still working) or the
 * list is rebuilt. [older] are the keys of the next rows toward the oldest, so a row that is gone can fall back to the
 * nearest earlier one; its length is fixed at [OLDER_KEYS_KEPT].
 */
internal class ScrollPlace(val key: String, val offset: Int, val older: List<String>) {
    companion object {
        const val OLDER_KEYS_KEPT = 8

        val Saver: Saver<ScrollPlaceHolder, Any> =
            Saver(
                save = { holder ->
                    holder.place?.let { listOf<Any>(it.key, it.offset) + it.older } ?: emptyList<Any>()
                },
                restore = { saved ->
                    val items = saved as List<*>
                    ScrollPlaceHolder().also { holder ->
                        if (items.size >= 2)
                            holder.place =
                                ScrollPlace(
                                    items[0] as String,
                                    items[1] as Int,
                                    items.drop(2).map { it as String },
                                )
                    }
                },
            )
    }
}

/** The saved place: one per chat entry; plain fields, written only from the collector below. */
internal class ScrollPlaceHolder {
    var place: ScrollPlace? = null
}

internal object ScrollPlaceRules {
    /** The place of the row at [firstVisible] (index into [keys], newest first) with [offset] px scrolled into it. */
    fun capture(keys: List<String>, firstVisible: Int, offset: Int): ScrollPlace? {
        val key = keys.getOrNull(firstVisible) ?: return null
        val older = keys.drop(firstVisible + 1).take(ScrollPlace.OLDER_KEYS_KEPT)
        return ScrollPlace(key, offset, older)
    }

    /**
     * Where to put the list to bring [place] back: the row's own index and the saved offset, else the nearest earlier
     * row that is still there (offset 0, it is a different row), else null (nothing to restore).
     */
    fun target(place: ScrollPlace, keys: List<String>): Pair<Int, Int>? {
        val at = keys.indexOf(place.key)
        if (at >= 0) return at to place.offset
        for (key in place.older) {
            val index = keys.indexOf(key)
            if (index >= 0) return index to 0
        }
        return null
    }
}

/**
 * The reader's place survives leaving the chat and coming back (the conversation switcher's Back rule: exactly as
 * they left it). The list state itself saves a position by index, which names another row once rows arrived below, so
 * this restores by key once, when the entry comes back, and then keeps the place current. [keys] are the list's item
 * keys, newest first (index 0 = the first item). A reader who was following the newest row is left following.
 */
@Composable
internal fun KeepScrollPlace(list: TranscriptListState, keys: List<String>) {
    val holder = rememberSaveable(saver = ScrollPlace.Saver) { ScrollPlaceHolder() }
    val current by rememberUpdatedState(keys)
    LaunchedEffect(list) {
        val saved = holder.place
        if (saved != null && !list.following) {
            // Wait for the rows (a conversation coming back may still be reading them).
            snapshotFlow { current }.first { it.isNotEmpty() }
            ScrollPlaceRules.target(saved, current)?.let { (index, offset) ->
                list.lazy.scrollToItem(index, offset)
            }
        }
        snapshotFlow { list.lazy.placeIn(current) }
            .collect { place ->
                // A follower keeps no place (they are on the newest message, wherever it has moved to).
                holder.place = if (list.following) null else place
            }
    }
}

private fun LazyListState.placeIn(keys: List<String>): ScrollPlace? =
    ScrollPlaceRules.capture(keys, firstVisibleItemIndex, firstVisibleItemScrollOffset)

/**
 * Find brings its current hit's row into view (plan 09 §6.6); a jump never animates across thousands of rows (§7.5).
 * Coming back to the chat with Find still open leaves the view where it was: the jump happens only when the hit or the
 * query is not the one already jumped to (the token is saved with the entry, and cleared when Find closes).
 */
@Composable
internal fun FindJump(find: FindUi?, loaded: Boolean, itemsBelowRows: Int, list: TranscriptListState) {
    var jumped by rememberSaveable { mutableStateOf("") }
    val latest by rememberUpdatedState(find)
    val below by rememberUpdatedState(itemsBelowRows)
    LaunchedEffect(find?.currentKey, find?.current, find?.query, find == null && loaded) {
        val now = latest
        if (now == null) {
            // Only a loaded chat that has no Find open forgets the jump; a chat still loading says nothing about it.
            if (loaded) jumped = ""
            return@LaunchedEffect
        }
        val token = "${now.query}\u0000${now.currentKey}\u0000${now.current}"
        if (now.currentIndex >= 0 && token != jumped) {
            jumped = token
            list.following = false
            val target = below + now.currentIndex
            list.lazy.scrollToItem(target)
            // Once the row is laid out, put it clear of the top bar and of the bottom block (the list's content
            // padding is the room those two take): a row that fits stands in the middle of the room, a taller one
            // starts at the top edge of the room. Offsets count from the newest edge, so scrolling forward lowers them.
            withFrameNanos { }
            val info = list.lazy.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
            val room = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            if (room > 0) list.lazy.scrollBy(findScrollDelta(item.offset, item.size, room))
        }
    }
}

/**
 * How far to scroll forward (positive: toward older rows, lowering the row's offset from the newest edge) so a row of
 * [size] at [offset] stands clear in a [room] tall: centred when it fits, its top at the room's top edge when it is
 * taller.
 */
internal fun findScrollDelta(offset: Int, size: Int, room: Int): Float =
    if (size >= room) (offset + size - room).toFloat() else (offset - (room - size) / 2).toFloat()

/**
 * Find looks inside what is folded away: when the current match is in a thought or in a step's text, that thought, step
 * and the work line holding them open (once per hit), so the marked match is on screen. The person can close them again;
 * the next hit opens its own. A match in the message text needs no fold. Closing Find opens nothing.
 */
@Composable
internal fun FindOpensFolds(find: FindUi?, rows: List<TranscriptRow>, facts: TurnFactsUi, folds: TranscriptFolds) {
    val latestRows by rememberUpdatedState(rows)
    val latestFacts by rememberUpdatedState(facts)
    LaunchedEffect(find?.currentKey, find?.current, find?.query, find?.currentSource) {
        val hit = find ?: return@LaunchedEffect
        val key = hit.currentKey ?: return@LaunchedEffect
        val turn = latestRows.firstOrNull { it.key == key }?.turnId
        for (fold in findFoldKeys(key, hit.currentSource, turn, latestFacts)) folds.open[fold] = true
    }
}

/** The fold keys that must be open to show a match in [rowKey]'s part [source] (empty for a message's own words). */
internal fun findFoldKeys(rowKey: String, source: String?, turnId: String?, facts: TurnFactsUi): List<String> =
    when {
        rowKey.startsWith("thought:") ->
            listOfNotNull(facts.reading.details[rowKey]?.key, turnId?.let { "work:$it" })
        rowKey.startsWith("work:") ->
            listOfNotNull(rowKey, source?.substringAfter('/', "")?.takeIf { it.isNotEmpty() }?.let { "step:$it" })
        else -> emptyList()
    }
