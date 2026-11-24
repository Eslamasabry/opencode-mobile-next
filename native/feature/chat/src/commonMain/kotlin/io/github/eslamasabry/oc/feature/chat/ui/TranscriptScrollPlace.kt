package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
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
                    holder.place?.let { listOf<Any>(holder.following, it.key, it.offset) + it.older } ?: emptyList<Any>()
                },
                restore = { saved ->
                    val items = saved as List<*>
                    ScrollPlaceHolder().also { holder ->
                        if (items.size >= 3) {
                            holder.following = items[0] as Boolean
                            holder.place =
                                ScrollPlace(
                                    items[1] as String,
                                    items[2] as Int,
                                    items.drop(3).map { it as String },
                                )
                        }
                    }
                },
            )
    }
}

/**
 * The saved place: one per chat entry; plain fields, written only from the collector below. [following] is whether
 * the reader was following the newest row when the place was taken; [fromStore] says the place came from the per-run
 * [ScrollPlaceStore] (a conversation opened again as a new entry), not from this entry's own saved state.
 */
internal class ScrollPlaceHolder {
    var place: ScrollPlace? = null
    var following: Boolean = false
    var fromStore: Boolean = false
}

/** A place kept for a conversation, with whether the reader was following the newest row. */
internal class KeptPlace(val place: ScrollPlace, val following: Boolean)

/**
 * Where each conversation was left, for this app run only (memory, nothing persisted, so nothing for profile deletion
 * to sweep): reopening a conversation from Home, All conversations, Search or the edge light is a new back-stack entry
 * whose own saved state is empty. Bounded: the least recently used conversation is forgotten past [KEPT].
 */
public object ScrollPlaceStore {
    private const val KEPT = 24
    private val kept = LinkedHashMap<String, KeptPlace>()

    internal fun get(conversation: String): KeptPlace? = kept[conversation]

    internal fun put(conversation: String, place: ScrollPlace, following: Boolean) {
        kept.remove(conversation)
        kept[conversation] = KeptPlace(place, following)
        while (kept.size > KEPT) kept.remove(kept.keys.first())
    }

    private val readers = mutableMapOf<String, MutableSet<() -> Unit>>()

    /**
     * A notification opens the newest reply, including when its conversation is already on screen.
     * Main thread only.
     */
    public fun forget(conversation: String) {
        kept.remove(conversation)
        readers[conversation]?.toList()?.forEach { it() }
    }

    internal fun observeForget(conversation: String, reader: () -> Unit): () -> Unit {
        readers.getOrPut(conversation) { mutableSetOf() }.add(reader)
        return {
            readers[conversation]?.let {
                it.remove(reader)
                if (it.isEmpty()) readers.remove(conversation)
            }
        }
    }

    internal fun clear() = kept.clear()
}

/** The shared notification-open boundary: discard the old reading place before navigation. */
public fun openChatFromNotification(ref: ConversationRef, openChat: (ConversationRef) -> Unit) {
    ScrollPlaceStore.forget("${ref.profileId.raw}/${ref.sessionId.raw}")
    openChat(ref)
}

internal object ScrollPlaceRules {
    /** The place of the row at [firstVisible] (index into [keys], newest first) with [offset] px scrolled into it. */
    fun capture(keys: List<String>, firstVisible: Int, offset: Int): ScrollPlace? {
        val key = keys.getOrNull(firstVisible) ?: return null
        // Only the few keys kept (issue 147: this runs on every publish, and a copy of every older key grew with
        // the history loaded).
        val from = firstVisible + 1
        val older = if (from >= keys.size) emptyList() else keys.subList(from, minOf(keys.size, from + ScrollPlace.OLDER_KEYS_KEPT)).toList()
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
 * keys, newest first (index 0 = the first item).
 *
 * Back to the same entry (from a diff, a request's details, Find, any page pushed over the chat) restores the exact row
 * and offset also for a reader who was following the newest row: if more arrived meanwhile they land where they were
 * and the list shows the "new below" cue. A conversation opened again as a new entry ([conversation] names it) returns
 * to where it was left in this run, unless the reader was following the newest row: then it opens on the newest row.
 */
@Composable
internal fun KeepScrollPlace(list: TranscriptListState, keys: List<String>, conversation: String? = null) {
    val holder =
        rememberSaveable(saver = ScrollPlace.Saver) {
            ScrollPlaceHolder().also { fresh ->
                val kept = conversation?.let(ScrollPlaceStore::get) ?: return@also
                fresh.place = kept.place
                fresh.following = kept.following
                fresh.fromStore = true
            }
        }
    var resets by remember(list, conversation) { mutableIntStateOf(0) }
    DisposableEffect(list, conversation) {
        val stop = conversation?.let { ScrollPlaceStore.observeForget(it) { resets++ } }
        onDispose { stop?.invoke() }
    }
    // Held by identity: a new list of keys is a new value without comparing every key (issue 147: the keys change on
    // every publish while a reply streams, and comparing them walked every row).
    val keysState = remember { mutableStateOf(keys, referentialEqualityPolicy()) }
    keysState.value = keys
    val current by keysState
    LaunchedEffect(list, resets) {
        if (resets > 0) {
            holder.place = null
            holder.fromStore = false
            holder.following = true
            list.following = true
            list.scrollToNewest()
        }
        val saved = holder.place
        if (saved != null && !(holder.fromStore && holder.following)) {
            // Wait for the rows (a conversation coming back may still be reading them).
            snapshotFlow { current }.first { it.isNotEmpty() }
            ScrollPlaceRules.target(saved, current)?.let { (index, offset) ->
                // Rows that arrived below put the place away from the newest edge: the reader stays where they were.
                val newestIndex = if (list.anchor == TranscriptAnchor.NormalOrder) current.lastIndex else 0
                list.following = holder.following && index == newestIndex
                list.lazy.scrollToItem(index, offset)
            }
        }
        snapshotFlow { list.lazy.placeIn(current) to list.following }
            .collect { (place, following) ->
                if (place == null) return@collect
                holder.place = place
                holder.following = following
                if (conversation != null) ScrollPlaceStore.put(conversation, place, following)
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
            val target = list.lazyIndex(below + now.currentIndex)
            list.lazy.scrollToItem(target)
            // Once the row is laid out, put it clear of the top bar and of the bottom block (the list's content
            // padding is the room those two take): a row that fits stands in the middle of the room, a taller one
            // starts at the top edge of the room. Offsets count from the newest edge, so scrolling forward lowers them.
            withFrameNanos { }
            val info = list.lazy.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
            val room = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
            val delta = findScrollDelta(item.offset, item.size, room)
            if (room > 0) {
                list.lazy.dispatchRawDelta(if (list.anchor == TranscriptAnchor.NormalOrder) -delta else delta)
            }
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
