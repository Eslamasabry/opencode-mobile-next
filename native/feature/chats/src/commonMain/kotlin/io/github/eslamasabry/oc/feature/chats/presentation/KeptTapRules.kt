package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** What the home does with a row tapped while it could not open yet (B-333). */
public sealed interface KeptTap {
    /** Nothing kept, or the row still cannot open: nothing to do. */
    public data object Wait : KeptTap

    /** The person moved to another page: the kept tap is dropped and never opens. */
    public data object Drop : KeptTap

    /** The row can open now: open [item], once. */
    public data class Open(val item: ChatFeedItem) : KeptTap

    /**
     * Its source is connected and its list read, but the list no longer shows the row (an older saved row, or a
     * conversation of another folder): open the conversation by its ref; its page says if it is gone.
     */
    public data class OpenRef(val ref: ConversationRef) : KeptTap
}

/**
 * B-333's one decision, from what the home knows now. The view model acts only when this value changes, so a feed or
 * home that keeps emitting while a row waits costs nothing and writes nothing.
 */
public object KeptTapRules {
    public fun decide(
        kept: ConversationRef?,
        onTappedPage: Boolean,
        serverAnswered: Boolean,
        items: List<ChatFeedItem>,
        /** The sources connected now: "Connecting" never stays on a row whose source is connected. */
        live: Set<ProfileId> = emptySet(),
    ): KeptTap =
        when {
            kept == null -> KeptTap.Wait
            !onTappedPage -> KeptTap.Drop
            !serverAnswered -> KeptTap.Wait
            else ->
                items.firstOrNull { it.ref == kept && it.canReopen }?.let { KeptTap.Open(it) }
                    ?: if (kept.profileId in live && items.none { it.profileId == kept.profileId && !it.canReopen })
                        KeptTap.OpenRef(kept)
                    else KeptTap.Wait
        }

    /** How long a kept tap waits for its source with nothing in progress: the conversation page's own limit. */
    public const val LIMIT_MS: Long = 90_000L

    /**
     * Waits out a kept tap: true once it has waited [limitMs] while [waits] said [KeptWait.Quiet], false as soon as it
     * says [KeptWait.Ended]. Time under [KeptWait.Updating] (an agent update) does not count. One timer per quiet
     * stretch and none while updating or ended: nothing wakes on a step, in the foreground or behind the launcher
     * (background CPU 2026-11-14). [time] measures a quiet stretch an update cut short.
     */
    public suspend fun limitSpent(
        waits: Flow<KeptWait>,
        limitMs: Long = LIMIT_MS,
        time: TimeSource = TimeSource.Monotonic,
    ): Boolean {
        val now = waits.distinctUntilChanged()
        var left = limitMs
        while (left > 0) {
            if (now.first { it != KeptWait.Updating } == KeptWait.Ended) return false
            val quiet = time.markNow()
            val next = kotlinx.coroutines.withTimeoutOrNull(left) { now.first { it != KeptWait.Quiet } } ?: return true
            if (next == KeptWait.Ended) return false
            left -= quiet.elapsedNow().inWholeMilliseconds
        }
        return true
    }

    /**
     * All conversations' kept tap: a saved row of a server the app is starting opens once that server is connected
     * ([live]), while the person is still on the page they tapped it on.
     */
    public fun decideOnServer(kept: ConversationRef?, onTappedPage: Boolean, live: Set<ProfileId>): KeptServerTap =
        when {
            kept == null -> KeptServerTap.Wait
            !onTappedPage -> KeptServerTap.Drop
            kept.profileId in live -> KeptServerTap.Open(kept)
            else -> KeptServerTap.Wait
        }
}

/** What the home does with New conversation tapped while its server was still opening (cold start). */
public enum class KeptNewTap {
    /** Nothing kept, or the server still opens: nothing to do. */
    Wait,

    /** The person moved to another page: the kept tap is dropped and never opens. */
    Drop,

    /** The server answered: open New conversation, once. */
    Open,

    /** The server did not answer (its opening failed): nothing opens and the home says why, with its way forward. */
    Refuse,
}

/** The kept New conversation tap's one decision, from what the home knows now; acted on only when it changes. */
public object KeptNewTapRules {
    /** [opening] is the phase of the server still opening, null once it answered. */
    public fun decide(kept: Boolean, onTappedPage: Boolean, opening: ChatsOpeningPhase?): KeptNewTap =
        when {
            !kept -> KeptNewTap.Wait
            !onTappedPage -> KeptNewTap.Drop
            opening == null -> KeptNewTap.Open
            opening == ChatsOpeningPhase.Failed -> KeptNewTap.Refuse
            else -> KeptNewTap.Wait
        }
}

/** Where a kept tap's wait stands, for [KeptTapRules.limitSpent]. */
public enum class KeptWait {
    /** Kept, and nothing in progress: the time counts. */
    Quiet,

    /** Kept while the app updates the source's agents: the time does not count. */
    Updating,

    /** The tap opened, was dropped or was replaced: the wait ends with nothing to say. */
    Ended,
}

/** What All conversations does with a row tapped while its server was starting. */
public sealed interface KeptServerTap {
    public data object Wait : KeptServerTap

    public data object Drop : KeptServerTap

    public data class Open(val ref: ConversationRef) : KeptServerTap
}
