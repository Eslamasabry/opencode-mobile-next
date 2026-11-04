package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.ServerGateway

/** Only the foreground, visible conversation may publish its displayed idle transition. */
public interface ChatReadSharing {
    public fun visible(ref: ConversationRef): Boolean

    public suspend fun shared(ref: ConversationRef): Boolean

    public suspend fun publish(
        ref: ConversationRef,
        gateway: ServerGateway,
        idleMs: Long,
        stillCurrent: () -> Boolean,
    )
}

@ContributesTo(AppScope::class)
public interface ChatReadSharingBindings {
    @Multibinds(allowEmpty = true) public val chatReadSharing: Set<ChatReadSharing>
}
