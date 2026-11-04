package io.github.eslamasabry.oc.feature.chat.header.world

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffPrepared
import io.github.eslamasabry.oc.engine.agents.handoff.HandoffSent

/** A destination read from the connected daemon's current, startable provider inventory. */
public data class AgentHandoffChoice(val providerId: String, val modelId: String, val label: String)

/** The host pins the source graph; unknown filesystem identity never produces a destination. */
public fun interface ChatAgentHandoffs {
    public fun open(ref: ConversationRef): ChatAgentHandoff?
}

public interface ChatAgentHandoff {
    public suspend fun choices(): Outcome<List<AgentHandoffChoice>>

    public suspend fun prepare(choice: AgentHandoffChoice): Outcome<HandoffPrepared>

    public suspend fun send(prompt: String): Outcome<HandoffSent>

    /** Metadata only, after confirmed delivery. Never recreates or resends. */
    public suspend fun retryLink(): Outcome<HandoffSent>

    public fun close()
}
