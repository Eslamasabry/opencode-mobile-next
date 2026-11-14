package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem

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

    /** How often the wait looks again. */
    public const val STEP_MS: Long = 2_000L

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

/** What All conversations does with a row tapped while its server was starting. */
public sealed interface KeptServerTap {
    public data object Wait : KeptServerTap

    public data object Drop : KeptServerTap

    public data class Open(val ref: ConversationRef) : KeptServerTap
}
