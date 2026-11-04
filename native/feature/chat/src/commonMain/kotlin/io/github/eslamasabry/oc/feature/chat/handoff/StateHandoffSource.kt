package io.github.eslamasabry.oc.feature.chat.handoff

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffEngine
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffLink

public data class HandoffDestination(val provider: String, val model: String, val label: String)

/** The app binds actual connected runtime adapters; the screen never imports protocols. */
public interface StateHandoffSource {
    public suspend fun links(ref: ConversationRef): Outcome<List<HandoffLink>>

    public suspend fun destinations(ref: ConversationRef): Outcome<List<HandoffDestination>>

    public suspend fun attempt(
        ref: ConversationRef,
        destination: HandoffDestination,
        current: () -> Boolean,
    ): Outcome<HandoffEngine>

    public suspend fun counterpart(
        ref: ConversationRef,
        link: HandoffLink,
    ): Outcome<ConversationRef>
}

@ContributesTo(AppScope::class)
public interface StateHandoffBindings {
    @Multibinds(allowEmpty = true) public val stateHandoffSources: Set<StateHandoffSource>
}
