package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * What the conversation list already shows of a conversation, read with no I/O, for the page's first frame
 * (open-transitions, Galaxy S22 recording: an existing conversation opened from All conversations with an empty title
 * that filled in later, although the row tapped had just shown it). The page starts from it; the server's own answer
 * replaces it as soon as it is read.
 */
public fun interface ChatGlance {
    /** The title the list shows for [ref], or null when the list does not hold it (or it is blank). */
    public fun title(ref: ConversationRef): String?
}

@ContributesTo(AppScope::class)
public interface ChatGlanceBindings {
    @Multibinds(allowEmpty = true) public val chatGlances: Set<ChatGlance>
}
