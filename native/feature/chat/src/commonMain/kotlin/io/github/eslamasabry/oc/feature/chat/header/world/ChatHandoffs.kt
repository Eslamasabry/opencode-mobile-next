package io.github.eslamasabry.oc.feature.chat.header.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffEngine

/** The app supplies real graph identity, persistent links and their profile-deletion admission. */
public interface ChatHandoffs {
    public fun open(
        ref: ConversationRef,
        model: ModelRef,
        label: String,
        active: () -> Boolean,
    ): HandoffEngine?
}

@ContributesTo(AppScope::class)
public interface ChatHandoffBindings {
    @Multibinds(allowEmpty = true) public val chatHandoffSources: Set<ChatHandoffs>
}
