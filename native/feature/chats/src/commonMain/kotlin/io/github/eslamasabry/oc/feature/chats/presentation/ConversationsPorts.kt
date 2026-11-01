package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.FeedDay
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.engine.agents.feed.SystemFeedDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Conversations list's read side: the merged `ChatFeed` of `:engine:agents` for the saved connections. The
 * connection owner binds the real one (one `OpenCodeChatFeedSource` per live graph) with `replaces =
 * [QuietConversationsFeed::class]`; until then the list says "No conversations yet".
 */
public interface ConversationsFeed {
    public val snapshot: StateFlow<ChatFeedSnapshot>

    public suspend fun refresh(): Outcome<Unit>
}

/** The return brief for the active profile and location, and its exact dismissal (07 §5 B15). */
public interface ReturnBriefFeed {
    public val brief: StateFlow<ReturnBrief>

    /** Acknowledges exactly the rows of [shown] (`ReturnBriefStore.acknowledge`). */
    public suspend fun dismiss(shown: ReturnBrief): Outcome<Unit>
}

/**
 * The gear: opens Settings. `:ui:routes` has no Settings key yet (route request in the S2-CHATS report), so the
 * default door is closed and the gear is absent, never dead (02 §3.7).
 */
public interface SettingsDoor {
    public val available: Boolean

    public fun open()
}

@ContributesBinding(AppScope::class)
@Inject
public class QuietConversationsFeed : ConversationsFeed {
    override val snapshot: StateFlow<ChatFeedSnapshot> = MutableStateFlow(ChatFeedSnapshot()).asStateFlow()

    override suspend fun refresh(): Outcome<Unit> = Unit.ok()
}

@ContributesBinding(AppScope::class)
@Inject
public class NoReturnBrief : ReturnBriefFeed {
    override val brief: StateFlow<ReturnBrief> = MutableStateFlow(ReturnBrief.None).asStateFlow()

    override suspend fun dismiss(shown: ReturnBrief): Outcome<Unit> = Unit.ok()
}

@ContributesBinding(AppScope::class)
@Inject
public class ClosedSettingsDoor : SettingsDoor {
    override val available: Boolean = false

    override fun open(): Unit = Unit
}

/** Engine types the list needs, bound here because app graphs depend on this module, not on `:engine:agents`. */
@BindingContainer
@ContributesTo(AppScope::class)
public object ChatsBindings {
    @Provides public fun feedDay(): FeedDay = SystemFeedDay()
}
