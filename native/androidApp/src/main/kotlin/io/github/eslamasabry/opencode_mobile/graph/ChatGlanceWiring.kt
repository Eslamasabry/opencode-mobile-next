package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.chat.world.ChatGlance

/**
 * A conversation page starts from what the conversations list shows of it (open-transitions): the list's snapshot is
 * in memory, so the page's bar names the conversation in its first frame instead of filling in once the server is read.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class FeedChatGlance(private val runtime: PreviewRuntime) : ChatGlance {
    override fun title(ref: ConversationRef): String? =
        runtime.snapshot.value.items.firstOrNull { it.ref == ref }?.title?.takeIf { it.isNotBlank() }
}
