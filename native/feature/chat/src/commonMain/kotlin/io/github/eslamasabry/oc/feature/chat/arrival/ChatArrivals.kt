package io.github.eslamasabry.oc.feature.chat.arrival

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many times the device has shown "Back returns to ‹title›, where you left it." The app stores it for the device
 * (one device-wide key, never per profile, so it is not swept with a profile). Where the app binds none, the count
 * lives until the process ends.
 */
public interface BackNoteCount {
    public fun shown(): Int

    public fun recordShown()
}

@ContributesTo(AppScope::class)
public interface BackNoteCountBindings {
    @Multibinds(allowEmpty = true) public val backNoteCounts: Set<BackNoteCount>
}

/**
 * A person has just switched to [target] with the edge light. [leftTitle] is the conversation they left (the words of
 * the Back note); [backNote] says this switch is one of the first [ChatArrivals.NOTE_SWITCHES] and the note is still
 * to be shown; [hint] says the message box still says "Reply in ‹title›" (until the person types).
 */
public data class ChatArrival(
    val target: ConversationRef,
    val leftTitle: String?,
    val seq: Long,
    val backNote: Boolean,
    val hint: Boolean = true,
)

/**
 * What the chat does when the edge light switches to a conversation (plan v3.1 §7 C1): the message box says "Reply in
 * ‹title›" until the person types, the keyboard stays down, and for the first three switches a note under the Back
 * arrow says where Back goes.
 *
 * The app's wiring calls [switched] on every light switch, right before it calls `AppNavigator.switchTo`. Nothing else
 * calls it: a notification, the home list or a rings tap is not a switch and gets none of this. The page for the
 * target (a fresh one or one kept from before) reads [latest] filtered to its own conversation.
 */
@SingleIn(AppScope::class)
@Inject
public class ChatArrivals(counts: Set<BackNoteCount>) {
    private val count: BackNoteCount = counts.singleOrNull() ?: InMemoryCount()
    private val mutable = MutableStateFlow<ChatArrival?>(null)
    private var seq = 0L

    /** The arrival the page of [ChatArrival.target] shows, until it is used up or replaced by the next switch. */
    public val latest: StateFlow<ChatArrival?> = mutable.asStateFlow()

    /**
     * The edge light is switching to [target]; [leftTitle] is the title of the conversation being left, or null when
     * it has none (no Back note then, only the hint). Main thread only.
     */
    public fun switched(target: ConversationRef, leftTitle: String?): ChatArrival {
        seq += 1
        val title = leftTitle?.trim()?.takeIf { it.isNotEmpty() }
        val next = ChatArrival(target, title, seq, backNote = title != null && count.shown() < NOTE_SWITCHES)
        mutable.value = next
        return next
    }

    /** The page showed the Back note of arrival [seq]: it counts once, here, and is not offered again. */
    public fun noteShown(seq: Long) {
        val now = mutable.value ?: return
        if (now.seq != seq || !now.backNote) return
        count.recordShown()
        mutable.value = now.copy(backNote = false)
    }

    /** The person typed in the box of arrival [seq]: "Reply in ‹title›" gives way to the usual words. */
    public fun typed(seq: Long) {
        val now = mutable.value ?: return
        if (now.seq != seq || !now.hint) return
        mutable.value = now.copy(hint = false)
    }

    /** The page of [target] is gone for good (popped): its arrival ends with it. */
    public fun closed(target: ConversationRef) {
        if (mutable.value?.target == target) mutable.value = null
    }

    public companion object {
        /** The Back note shows on this many switches per device, then never. */
        public const val NOTE_SWITCHES: Int = 3
    }

    private class InMemoryCount : BackNoteCount {
        private var n = 0

        override fun shown(): Int = n

        override fun recordShown() {
            n += 1
        }
    }
}
